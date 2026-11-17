package com.wongiii.chat.domain.chat

import android.util.Base64
import com.wongiii.chat.data.local.AppSettings
import com.wongiii.chat.data.local.FileStore
import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.Brand
import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.FileAttachment
import com.wongiii.chat.data.model.MessageError
import com.wongiii.chat.data.model.ReplyBlock
import com.wongiii.chat.data.model.TokenUsage
import com.wongiii.chat.data.model.TextCitation
import com.wongiii.chat.data.model.newId
import com.wongiii.chat.data.model.path
import com.wongiii.chat.data.remote.Endpoints
import com.wongiii.chat.data.remote.HttpError
import com.wongiii.chat.data.remote.LlmApi
import com.wongiii.chat.data.remote.SearchApi
import com.wongiii.chat.data.remote.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ChatEngine(
    private val llmApi: LlmApi,
    private val searchApi: SearchApi,
    private val fileStore: FileStore,
) {
    suspend fun stream(
        session: ChatSession,
        assistant: ChatMessage,
        question: ChatMessage,
        settings: AppSettings,
        titleEnabled: Boolean,
        onUpdate: (ChatSession) -> Unit,
        onServerTitle: (String) -> Unit,
    ): ChatSession {
        val apiKey = settings.network.llmApiKey?.trim().orEmpty()
        if (apiKey.isEmpty()) throw HttpError(401, "请先在设置中填写 API Key")
        val baseUrl = Endpoints.llm(settings.network)
        val searchOutcome = if (assistant.search == true && question.content.isNotBlank()) {
            runSearch(settings, question.content, session, assistant.id, onUpdate)
        } else {
            SearchOutcome(null, session)
        }
        val body = buildRequestBody(
            session = searchOutcome.session,
            question = question,
            assistant = assistant,
            settings = settings,
            titleEnabled = titleEnabled,
            searchReference = searchOutcome.reference,
        )
        var working = searchOutcome.session
        val state = StreamState()
        var lastError: Throwable? = null
        try {
            llmApi.stream(baseUrl, apiKey, body).collect { event ->
                val type = (event["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (event["error"] is JsonObject) {
                    val message = (event["error"] as? JsonObject)
                        ?.get("message")?.let { (it as? JsonPrimitive)?.contentOrNull }
                        .orEmpty()
                    throw HttpError(0, message.ifBlank { "生成失败" })
                }
                when (type) {
                    "message_start" -> {
                        val message = event["message"] as? JsonObject
                        val model = (message?.get("model") as? JsonPrimitive)?.contentOrNull
                        val usage = message?.get("usage")?.let { decodeUsage(it) }
                        state.usage = usage
                        working = patch(working, assistant.id, onUpdate) {
                            it.copy(model = model ?: it.model, usage = usage)
                        }
                    }
                    "content_block_start" -> {
                        val eventIndex = (event["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -1
                        val content = event["content_block"] as? JsonObject ?: return@collect
                        val decoded = decodeBlock(content, state)
                        if (decoded != null) {
                            val blockIndex = working.messages.firstOrNull { it.id == assistant.id }?.blocks?.size ?: 0
                            working = patch(working, assistant.id, onUpdate) {
                                it.copy(blocks = it.blocks + decoded)
                            }
                            state.blockIndex[eventIndex] = blockIndex
                        }
                    }
                    "content_block_delta" -> {
                        val index = (event["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -1
                        val delta = event["delta"] as? JsonObject ?: return@collect
                        val deltaType = (delta["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                        val blockIndex = state.blockIndex[index] ?: return@collect
                        when (deltaType) {
                            "text_delta" -> {
                                val text = (delta["text"] as? JsonPrimitive)?.contentOrNull ?: return@collect
                                working = patchBlock(working, assistant.id, blockIndex, onUpdate) { block ->
                                    block.copy(text = (block.text.orEmpty() + text))
                                }
                            }
                            "thinking_delta" -> {
                                val text = (delta["thinking"] as? JsonPrimitive)?.contentOrNull ?: return@collect
                                working = patchBlock(working, assistant.id, blockIndex, onUpdate) { block ->
                                    block.copy(thinking = (block.thinking.orEmpty() + text))
                                }
                            }
                            "signature_delta" -> {
                                val sig = (delta["signature"] as? JsonPrimitive)?.contentOrNull ?: return@collect
                                working = patchBlock(working, assistant.id, blockIndex, onUpdate) { block ->
                                    block.copy(signature = (block.signature.orEmpty() + sig))
                                }
                            }
                            "citations_delta" -> {
                                val citation = delta["citation"] as? JsonObject ?: return@collect
                                val decoded = decodeCitation(citation) ?: return@collect
                                working = patchBlock(working, assistant.id, blockIndex, onUpdate) { block ->
                                    block.copy(citations = block.citations + decoded)
                                }
                            }
                        }
                    }
                    "content_block_stop" -> {
                        val index = (event["index"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: -1
                        val blockIndex = state.blockIndex[index] ?: return@collect
                        val now = System.currentTimeMillis()
                        working = patchBlock(working, assistant.id, blockIndex, onUpdate) { block ->
                            if (block.type == "thinking") {
                                val elapsed = block.startedAt?.let { ((now - it) / 1000).toInt() }
                                block.copy(endedAt = now, elapsedSeconds = elapsed)
                            } else block
                        }
                    }
                    "message_delta" -> {
                        val delta = event["delta"] as? JsonObject
                        val stopReason = (delta?.get("stop_reason") as? JsonPrimitive)?.contentOrNull
                        val title = (delta?.get("title") as? JsonPrimitive)?.contentOrNull?.trim()
                        val usage = event["usage"]?.let { decodeUsage(it) } ?: delta?.get("usage")?.let { decodeUsage(it) }
                        state.stopReason = stopReason
                        state.usage = usage
                        working = patch(working, assistant.id, onUpdate) {
                            it.copy(stopReason = stopReason ?: it.stopReason, usage = usage ?: it.usage)
                        }
                        if (!title.isNullOrBlank()) onServerTitle(title)
                    }
                    "message_stop" -> {
                        state.stopped = true
                    }
                    "ping" -> Unit
                }
            }
        } catch (cancelled: CancellationException) {
            val settled = settle(working, assistant.id, onUpdate, status = "stopped", stopReason = state.stopReason)
            return settled
        } catch (error: Throwable) {
            lastError = error
        }

        val status = when {
            lastError != null -> "error"
            state.stopReason == "refusal" -> "done"
            state.stopReason != null || state.stopped -> "done"
            else -> "error"
        }
        if (lastError != null && state.stopReason == null) {
            val kind = when {
                lastError is HttpError && lastError.status == 401 -> "unauthorized"
                lastError is HttpError && lastError.status == 402 -> "balance"
                lastError is HttpError && lastError.status == 429 -> "rate"
                lastError is HttpError && lastError.status >= 500 -> "server"
                else -> "network"
            }
            val message = lastError.message ?: "网络连接失败"
            return patch(working, assistant.id, onUpdate) {
                it.copy(
                    status = status,
                    error = MessageError(kind, message, (lastError as? HttpError)?.status),
                    blocks = it.blocks.ifEmpty {
                        listOf(ReplyBlock(type = "text", text = "", isTemplate = true))
                    },
                )
            }
        }
        return settle(
            working,
            assistant.id,
            onUpdate,
            status = status,
            stopReason = state.stopReason,
            refusal = state.stopReason == "refusal",
        )
    }

    private suspend fun runSearch(
        settings: AppSettings,
        query: String,
        session: ChatSession,
        assistantId: String,
        onUpdate: (ChatSession) -> Unit,
    ): SearchOutcome {
        val callId = "srvtoolu_s_1_" + newId().replace("-", "")
        val toolUse = ReplyBlock(
            type = "server_tool_use",
            id = callId,
            name = "deepseek_web_search",
            kind = "search",
            input = buildJsonObject {
                put("action", "search")
                put("queries", buildJsonArray { add(JsonPrimitive(query.take(128))) })
            },
        )
        var working = patch(session, assistantId, onUpdate) {
            it.copy(blocks = it.blocks + toolUse)
        }
        val results = runCatching {
            searchApi.search(
                Endpoints.search(settings.network),
                Endpoints.searchKey(settings.network),
                query,
            )
        }.getOrElse { emptyList() }
        val resultBlock = if (results.isEmpty()) {
            ReplyBlock(
                type = "web_search_tool_result",
                toolUseId = callId,
                kind = "search",
                content = buildJsonArray {
                    add(buildJsonObject {
                        put("type", "web_search_tool_result_error")
                        put("error_code", "unavailable")
                    })
                },
            )
        } else {
            ReplyBlock(
                type = "web_search_tool_result",
                toolUseId = callId,
                kind = "search",
                content = buildJsonArray {
                    results.take(10).forEach { result ->
                        add(buildJsonObject {
                            put("type", "web_search_result")
                            put("url", result.url)
                            put("title", result.title)
                            put("snippet", result.snippet)
                            put("site_name", result.siteName)
                            put("site_icon", result.siteIcon)
                            put("page_age", result.date)
                            put("encrypted_content", "")
                        })
                    }
                },
            )
        }
        working = patch(working, assistantId, onUpdate) { it.copy(blocks = it.blocks + resultBlock) }
        return SearchOutcome(
            reference = if (results.isEmpty()) null else createSearchReference(results),
            session = working,
        )
    }

    private data class SearchOutcome(
        val reference: String?,
        val session: ChatSession,
    )

    private fun buildRequestBody(
        session: ChatSession,
        question: ChatMessage,
        assistant: ChatMessage,
        settings: AppSettings,
        titleEnabled: Boolean,
        searchReference: String?,
    ): JsonObject {
        val path = session.path().filter { it.id != assistant.id }
        val contextMessages = buildContext(path, question, assistant, settings)
        val system = buildString {
            append(Brand.SYSTEM_PROMPT)
            if (searchReference != null) append("\n\n").append(searchReference)
        }
        return buildJsonObject {
            put("model", assistant.model ?: settings.model)
            put("system", system)
            put("messages", JsonArray(contextMessages))
            put("thinking", buildJsonObject {
                put("type", if (assistant.thinking == true) "enabled" else "disabled")
            })
            put("safety_check", buildJsonObject { put("level", "high") })
            put("tools", buildJsonArray {
                add(buildJsonObject {
                    put("type", "deepseek_read_url")
                    put("name", "deepseek_read_url")
                })
            })
            if (titleEnabled) {
                put("title", buildJsonObject { put("enabled", true) })
            }
        }
    }

    private fun buildContext(
        path: List<ChatMessage>,
        question: ChatMessage,
        assistant: ChatMessage,
        settings: AppSettings,
    ): List<JsonObject> {
        val messages = mutableListOf<JsonObject>()
        var previousReminder: String? = null
        fun appendReminder(reminder: String?) {
            if (!reminder.isNullOrBlank() && reminder != previousReminder) {
                messages += buildJsonObject {
                    put("role", "latest_reminder")
                    put("content", reminder)
                }
                previousReminder = reminder
            }
        }
        val beforeQuestion = path.filter { it.id != question.id }
        var i = 0
        while (i + 1 < beforeQuestion.size) {
            val user = beforeQuestion[i]
            val reply = beforeQuestion[i + 1]
            if (user.isUser && !reply.isUser && isUsableReply(reply)) {
                val userContent = userContent(user, settings)
                if (userContent != null) {
                    appendReminder(reply.latestReminder)
                    messages += buildJsonObject {
                        put("role", "user")
                        put("content", userContent)
                    }
                    messages += buildJsonObject {
                        put("role", "assistant")
                        put("content", JsonArray(assistantContent(reply, includeThinking = false)))
                    }
                }
            }
            i += 2
        }
        appendReminder(assistant.latestReminder ?: latestReminder(settings))
        val questionContent = userContent(question, settings)
        if (questionContent != null) {
            messages += buildJsonObject {
                put("role", "user")
                put("content", questionContent)
            }
        }
        if (assistant.prefix) {
            val prefix = assistantContent(assistant, includeThinking = assistant.thinking == true)
            if (prefix.isNotEmpty()) {
                messages += buildJsonObject {
                    put("role", "assistant")
                    put("content", JsonArray(prefix))
                }
            }
        }
        return messages
    }

    private fun userContent(message: ChatMessage, settings: AppSettings): JsonElement? {
        val images = message.files.filter { it.isImage }.mapNotNull { imageBlock(it) }
        val texts = buildString {
            if (message.content.isNotBlank()) append(message.content.trim())
            message.files.filter { !it.isImage }.forEach { file ->
                val extracted = file.textContent?.trim().orEmpty()
                if (extracted.isNotBlank()) {
                    if (isNotEmpty()) append("\n\n")
                    append("【附件：").append(file.name).append("】\n")
                    if (file.truncated) append("（内容过长，已截断）\n")
                    append(extracted)
                }
            }
        }.trim()
        if (images.isEmpty() && texts.isEmpty()) return null
        if (images.isEmpty()) return JsonPrimitive(texts)
        val blocks = buildJsonArray {
            images.forEach { add(it) }
            if (texts.isNotEmpty()) {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", texts)
                })
            }
        }
        return blocks
    }

    private fun imageBlock(file: FileAttachment): JsonObject? {
        val path = file.localPath ?: return null
        val bytes = fileStore.read(path) ?: return null
        if (bytes.isEmpty() || bytes.size > 20 * 1024 * 1024) return null
        val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return buildJsonObject {
            put("type", "image")
            put("source", buildJsonObject {
                put("type", "base64")
                put("media_type", file.mimeType.ifBlank { "image/png" })
                put("data", data)
            })
        }
    }

    private fun assistantContent(message: ChatMessage, includeThinking: Boolean): List<JsonElement> {
        val out = mutableListOf<JsonElement>()
        if (includeThinking) {
            message.blocks.filter { it.type == "thinking" && it.thinking.orEmpty().isNotBlank() }.forEach { block ->
                out += buildJsonObject {
                    put("type", "thinking")
                    put("thinking", block.thinking.orEmpty())
                    put("signature", block.signature.orEmpty())
                }
            }
        }
        val text = message.blocks.filter { it.type == "text" && it.text.orEmpty().isNotBlank() }
            .joinToString("\n\n") { it.text.orEmpty() }
        if (text.isNotBlank()) {
            out += buildJsonObject {
                put("type", "text")
                put("text", text)
            }
        }
        return out
    }

    private fun decodeBlock(content: JsonObject, state: StreamState): ReplyBlock? {
        val type = (content["type"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (type == "tool_use") {
            state.prefix = false
            return null
        }
        return when (type) {
            "text" -> ReplyBlock(
                type = "text",
                text = (content["text"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                citations = (content["citations"] as? JsonArray)?.mapNotNull {
                    (it as? JsonObject)?.let(::decodeCitation)
                }.orEmpty(),
            )
            "thinking" -> ReplyBlock(
                type = "thinking",
                thinking = (content["thinking"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                signature = (content["signature"] as? JsonPrimitive)?.contentOrNull,
                startedAt = System.currentTimeMillis(),
            )
            "server_tool_use", "web_search_tool_result", "web_fetch_tool_result" ->
                runCatching {
                    AppJson.decodeFromJsonElement(ReplyBlock.serializer(), content)
                }.getOrNull()
            else -> null
        }
    }

    private fun decodeCitation(content: JsonObject): TextCitation? {
        val url = (content["url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        return TextCitation(
            url = url,
            title = (content["title"] as? JsonPrimitive)?.contentOrNull,
            citedText = (content["cited_text"] as? JsonPrimitive)?.contentOrNull,
            encryptedIndex = (content["encrypted_index"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    private fun decodeUsage(element: JsonElement): TokenUsage? = runCatching {
        AppJson.decodeFromJsonElement(TokenUsage.serializer(), element)
    }.getOrNull()

    private fun patch(
        session: ChatSession,
        assistantId: String,
        onUpdate: (ChatSession) -> Unit,
        transform: (ChatMessage) -> ChatMessage,
    ): ChatSession {
        val messages = session.messages.map { message ->
            if (message.id == assistantId) transform(message) else message
        }
        val next = session.copy(messages = messages, updatedAt = System.currentTimeMillis())
        onUpdate(next)
        return next
    }

    private fun patchBlock(
        session: ChatSession,
        assistantId: String,
        blockIndex: Int,
        onUpdate: (ChatSession) -> Unit,
        transform: (ReplyBlock) -> ReplyBlock,
    ): ChatSession = patch(session, assistantId, onUpdate) { message ->
        if (blockIndex < 0 || blockIndex >= message.blocks.size) message
        else message.copy(blocks = message.blocks.toMutableList().also {
            it[blockIndex] = transform(it[blockIndex])
        })
    }

    private fun settle(
        session: ChatSession,
        assistantId: String,
        onUpdate: (ChatSession) -> Unit,
        status: String,
        stopReason: String?,
        refusal: Boolean = false,
    ): ChatSession = patch(session, assistantId, onUpdate) { message ->
        val now = System.currentTimeMillis()
        val blocks = if (refusal) {
            message.blocks.filter { it.type != "thinking" && (it.type != "text" || it.isTemplate) }
        } else {
            message.blocks.map { block ->
                if (block.type == "thinking" && block.endedAt == null) {
                    block.copy(
                        endedAt = now,
                        elapsedSeconds = block.startedAt?.let { ((now - it) / 1000).toInt() },
                    )
                } else block
            }
        }
        message.copy(
            status = status,
            stopReason = stopReason ?: message.stopReason,
            blocks = blocks,
            refusal = refusal,
            prefix = message.prefix && !refusal,
        )
    }

    private fun latestReminder(settings: AppSettings): String {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val weekday = when (LocalDate.now().dayOfWeek.value) {
            1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"
            5 -> "星期五"; 6 -> "星期六"; else -> "星期日"
        }
        val location = settings.location.orEmpty()
        val language = if (settings.locale == "en-US") "English" else "中文"
        return "$date,$weekday,$location,Web,$language"
    }

    private fun createSearchReference(results: List<SearchResult>): String = buildString {
        append("以下是联网搜索返回的参考资料，供回答用户问题时参考。\n")
        append("资料可能不准确、过时或与问题无关，请自行判断；引用网页时请使用资料中的链接，资料不足时请如实说明。\n\n")
        results.take(10).forEachIndexed { index, result ->
            append(index + 1).append(". ").append(result.title).append('\n')
            append("链接：").append(result.url).append('\n')
            append("摘要：").append(result.summary.ifBlank { result.snippet }).append("\n\n")
        }
    }

    private fun isUsableReply(message: ChatMessage): Boolean =
        !message.isUser && (message.status == "done" || message.status == "stopped") &&
            !message.refusal && message.blocks.any {
                it.type == "text" && it.text.orEmpty().isNotBlank() && !it.isTemplate
            }

    private data class StreamState(
        val blockIndex: MutableMap<Int, Int> = mutableMapOf(),
        var stopReason: String? = null,
        var usage: TokenUsage? = null,
        var prefix: Boolean = false,
        var stopped: Boolean = false,
    )
}
