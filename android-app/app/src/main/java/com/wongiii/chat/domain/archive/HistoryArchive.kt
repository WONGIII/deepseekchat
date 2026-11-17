package com.wongiii.chat.domain.archive

import com.wongiii.chat.data.local.FileStore
import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.FileAttachment
import com.wongiii.chat.data.model.MessageError
import com.wongiii.chat.data.model.ReplyBlock
import com.wongiii.chat.data.model.SessionSummary
import com.wongiii.chat.data.model.TokenUsage
import com.wongiii.chat.data.model.path
import com.wongiii.chat.domain.files.inferMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
private data class ArchiveSession(
    val id: String,
    val title: String? = null,
    val pinned: Boolean = false,
    val current_message_id: Int = 0,
    val inserted_at: String = "",
    val updated_at: String = "",
    val messages: List<ArchiveMessage> = emptyList(),
)

@Serializable
private data class ArchiveMessage(
    val id: Int,
    val parent_id: Int? = null,
    val role: String,
    val status: String,
    val thinking_enabled: Boolean = false,
    val search_enabled: Boolean = false,
    val accumulated_token_usage: Int = 0,
    val inserted_at: String = "",
    val fragments: List<JsonObject> = emptyList(),
)

@Serializable
private data class ArchiveIndex(
    val id: String,
    val title: String? = null,
    val pinned: Boolean = false,
    val message_count: Int = 0,
    val inserted_at: String = "",
    val updated_at: String = "",
)

object HistoryArchive {
    suspend fun export(
        sessions: List<ChatSession>,
        fileStore: FileStore,
        output: OutputStream,
    ): Int = withContext(Dispatchers.IO) {
        val images = mutableListOf<Triple<String, ByteArray, String>>()
        val sessionFiles = sessions.map { session ->
            val ordered = numberMessages(session)
            val numbers = ordered.withIndex().associate { (index, message) -> message.id to index + 1 }
            val current = session.path().lastOrNull()?.let { numbers[it.id] } ?: 0
            val archiveMessages = ordered.mapIndexed { index, message ->
                val parentNumber = message.parentId?.let { numbers[it] }
                archiveMessage(session.id, message, index + 1, parentNumber, images, fileStore)
            }
            val archive = buildJsonObject {
                put("id", session.id)
                put("title", if (session.title.isBlank()) JsonPrimitive(null) else JsonPrimitive(session.title))
                put("pinned", false)
                put("current_message_id", current)
                put("inserted_at", iso(session.createdAt))
                put("updated_at", iso(session.updatedAt))
                put("messages", JsonArray(archiveMessages))
            }
            val index = buildJsonObject {
                put("id", session.id)
                put("title", if (session.title.isBlank()) JsonPrimitive(null) else JsonPrimitive(session.title))
                put("pinned", false)
                put("message_count", ordered.size)
                put("inserted_at", iso(session.createdAt))
                put("updated_at", iso(session.updatedAt))
            }
            archive to index
        }
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(buildJsonObject { put("export_format_version", 3) }.toString().toByteArray())
            zip.closeEntry()
            sessionFiles.forEach { (session, _) ->
                zip.putNextEntry(ZipEntry("chat_sessions/${safe(session["id"]?.jsonPrimitive?.contentOrNull ?: "session")}.json"))
                zip.write(session.toString().toByteArray())
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry("chat_session_index.json"))
            zip.write(JsonArray(sessionFiles.map { it.second }).toString().toByteArray())
            zip.closeEntry()
            images.forEach { (path, bytes, _) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        sessions.size
    }

    suspend fun import(
        input: InputStream,
        fileStore: FileStore,
    ): List<ChatSession> = withContext(Dispatchers.IO) {
        val files = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > 250L * 1024 * 1024) error("导入文件过大")
                    files[entry.name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val manifest = files["manifest.json"]?.let {
            runCatching { AppJson.parseToJsonElement(String(it)).jsonObject }.getOrNull()
        } ?: error("不是有效的历史备份：缺少 manifest.json")
        val version = manifest["export_format_version"]?.jsonPrimitive?.intOrNull
        if (version != 3) error("仅支持导出格式版本 3")
        val sessionEntries = files.filterKeys { it.startsWith("chat_sessions/") && it.endsWith(".json") }
        val sessions = sessionEntries.map { (_, bytes) ->
            val archive = AppJson.decodeFromString(ArchiveSession.serializer(), String(bytes))
            toLocal(archive, files, fileStore)
        }
        sessions.sortedByDescending { it.updatedAt }
    }

    private suspend fun archiveMessage(
        sessionId: String,
        message: ChatMessage,
        number: Int,
        parentNumber: Int?,
        images: MutableList<Triple<String, ByteArray, String>>,
        fileStore: FileStore,
    ): JsonObject {
        val fragments = buildJsonArray {
            if (message.isUser && message.files.isNotEmpty()) {
                val entries = buildJsonArray {
                    message.files.forEach { file ->
                        val exportPath = if (file.isImage && file.localPath != null) {
                            val bytes = fileStore.read(file.localPath)
                            if (bytes != null) {
                                val path = "chat_sessions/$sessionId" + "_files/" +
                                    safe(file.id) + "_" + safe(file.name)
                                images += Triple(path, bytes, file.mimeType)
                                path
                            } else null
                        } else null
                        add(buildJsonObject {
                            put("id", file.id)
                            put("file_name", file.name)
                            put("file_size", file.size)
                            put("token_usage", JsonPrimitive(null))
                            put("inserted_at", iso(message.createdAt))
                            put("export_path", exportPath?.let(::JsonPrimitive) ?: JsonPrimitive(null))
                            put("is_image", file.isImage)
                            put("width", file.width)
                            put("height", file.height)
                            if (!file.textContent.isNullOrBlank()) {
                                put("text_content", file.textContent)
                                put("text_truncated", file.truncated)
                            }
                        })
                    }
                }
                add(buildJsonObject {
                    put("type", "FILE")
                    put("files", entries)
                })
            }
            if (message.isUser) {
                add(buildJsonObject {
                    put("type", "REQUEST")
                    put("content", message.content)
                })
            } else {
                val searchResults = message.blocks.filter { it.type == "web_search_tool_result" }
                    .associateBy { it.toolUseId.orEmpty() }
                message.blocks.forEach { block ->
                    when (block.type) {
                        "thinking" -> if (!block.thinking.isNullOrBlank()) add(buildJsonObject {
                            put("type", "THINK")
                            put("content", block.thinking)
                            put("elapsed_secs", block.elapsedSeconds ?: block.startedAt?.let { start ->
                                block.endedAt?.let { end -> ((end - start) / 1000).toInt() }
                            } ?: 0)
                        })
                        "text" -> if (!block.text.isNullOrBlank()) add(buildJsonObject {
                            put("type", if (block.isTemplate) "TEMPLATE_RESPONSE" else "RESPONSE")
                            put("content", block.text)
                        })
                        "server_tool_use" -> if (block.kind == "search") {
                            val result = searchResults[block.id.orEmpty()]
                            val queries = block.input?.get("queries")?.let { element ->
                                runCatching { element.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
                            }.orEmpty()
                            add(buildJsonObject {
                                put("type", "TOOL_SEARCH")
                                put("queries", JsonArray(queries.map(::JsonPrimitive)))
                                put("status", if (result == null) "WIP" else "FINISHED")
                                val content = result?.content
                                val rows = if (content is JsonArray) content.mapNotNull { item ->
                                    val obj = item as? JsonObject ?: return@mapNotNull null
                                    val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                    buildJsonObject {
                                        put("url", url)
                                        put("title", obj["title"]?.jsonPrimitive?.contentOrNull ?: url)
                                        put("snippet", obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty())
                                        put("published_at", obj["page_age"]?.jsonPrimitive?.contentOrNull?.let(::JsonPrimitive) ?: JsonPrimitive(null))
                                        put("site_name", obj["site_name"]?.jsonPrimitive?.contentOrNull?.let(::JsonPrimitive) ?: JsonPrimitive(null))
                                        put("site_icon", obj["site_icon"]?.jsonPrimitive?.contentOrNull?.let(::JsonPrimitive) ?: JsonPrimitive(null))
                                    }
                                } else emptyList()
                                put("results", JsonArray(rows))
                            })
                        }
                    }
                }
                if (message.error != null && message.blocks.none { it.type == "text" && it.isTemplate }) {
                    add(buildJsonObject {
                        put("type", "TEMPLATE_RESPONSE")
                        put("content", message.error.message)
                    })
                }
            }
        }
        return buildJsonObject {
            put("id", number)
            put("parent_id", parentNumber?.let(::JsonPrimitive) ?: JsonPrimitive(null))
            put("role", if (message.isUser) "USER" else "ASSISTANT")
            put("status", archiveStatus(message))
            put("thinking_enabled", message.thinking ?: false)
            put("search_enabled", message.search ?: false)
            put("accumulated_token_usage", message.usage?.let {
                it.inputTokens + it.outputTokens + it.cacheReadTokens + it.cacheCreationTokens
            } ?: 0)
            put("inserted_at", iso(message.createdAt))
            put("fragments", fragments)
        }
    }

    private fun numberMessages(session: ChatSession): List<ChatMessage> {
        val byId = session.messages.associateBy { it.id }
        val visited = mutableSetOf<String>()
        val ordered = mutableListOf<ChatMessage>()
        fun visit(message: ChatMessage) {
            if (!visited.add(message.id)) return
            message.parentId?.let { byId[it] }?.let { visit(it) }
            ordered += message
        }
        session.messages.forEach { visit(it) }
        return ordered
    }

    private suspend fun toLocal(
        archive: ArchiveSession,
        files: Map<String, ByteArray>,
        fileStore: FileStore,
    ): ChatSession {
        val byNumber = mutableMapOf<Int, ChatMessage>()
        val created = archive.messages.sortedBy { it.id }.map { message ->
            val parent = message.parent_id?.let { byNumber[it] }
            val localId = "${archive.id}:${message.id}"
            val userFiles = message.fragments.filter { it["type"]?.asString() == "FILE" }
                .flatMap { fragment ->
                    fragment["files"]?.let { element ->
                        runCatching { element.jsonArray }.getOrNull()?.mapNotNull { item ->
                            val obj = item as? JsonObject ?: return@mapNotNull null
                            val id = obj["id"]?.asString() ?: return@mapNotNull null
                            val name = obj["file_name"]?.asString().orEmpty()
                            val isImage = obj["is_image"]?.asBoolean() ?: false
                            val exportPath = obj["export_path"]?.asString()
                            val bytes = exportPath?.let { files[it] }
                            val localPath = if (bytes != null) {
                                fileStore.saveAt(id, bytes).absolutePath
                            } else null
                            FileAttachment(
                                id = id,
                                name = name,
                                size = obj["file_size"]?.asLong() ?: bytes?.size?.toLong() ?: 0L,
                                isImage = isImage,
                                mimeType = inferMime(name),
                                localPath = localPath,
                                textContent = obj["text_content"]?.asString(),
                                truncated = obj["text_truncated"]?.asBoolean() ?: false,
                                extraction = if (isImage) "done" else if (obj["text_content"]?.asString() != null) "done" else "none",
                                width = obj["width"]?.asInt() ?: 0,
                                height = obj["height"]?.asInt() ?: 0,
                            )
                        }.orEmpty()
                    }.orEmpty()
                }
            val isUser = message.role == "USER"
            val content = if (isUser) {
                message.fragments.filter { it["type"]?.asString() == "REQUEST" }
                    .joinToString("") { it["content"]?.asString().orEmpty() }
            } else ""
            val blocks = if (isUser) emptyList() else fragmentsToBlocks(message.fragments, archive.id, message.id)
            val status = localStatus(message.status)
            ChatMessage(
                id = localId,
                role = if (isUser) "user" else "assistant",
                content = content,
                files = userFiles,
                createdAt = parseTime(message.inserted_at),
                parentId = parent?.id,
                status = status,
                thinking = message.thinking_enabled,
                search = message.search_enabled,
                blocks = if (status == "done" && message.status == "CONTENT_FILTER") {
                    blocks.filter { it.type != "thinking" && (it.type != "text" || it.isTemplate) }
                } else blocks,
                refusal = message.status == "CONTENT_FILTER",
                stopReason = if (message.status == "CONTENT_FILTER") "refusal" else null,
                error = if (status == "error") MessageError("history", "历史会话中的回复失败") else null,
                usage = TokenUsage(outputTokens = message.accumulated_token_usage),
            ).also { byNumber[message.id] = it }
        }
        val currentNumber = archive.current_message_id
        val current = byNumber[currentNumber] ?: created.firstOrNull()
        val chain = mutableSetOf<String>()
        val selectedByParent = mutableMapOf<String, String>()
        var cursor = current
        while (cursor != null && chain.add(cursor.id)) {
            val parent = cursor.parentId?.let { id -> created.firstOrNull { it.id == id } }
            if (parent != null) selectedByParent[parent.id] = cursor.id
            cursor = parent
        }
        created.groupBy { it.parentId }.forEach { (parentId, children) ->
            if (parentId != null && parentId !in selectedByParent) {
                selectedByParent[parentId] = children.maxByOrNull { it.createdAt }?.id.orEmpty()
            }
        }
        val messages = created.map { message ->
            message.copy(selectedChildId = selectedByParent[message.id])
        }
        val root = messages.firstOrNull { it.parentId == null } ?: messages.firstOrNull()
        return ChatSession(
            id = archive.id,
            title = archive.title.orEmpty(),
            createdAt = parseTime(archive.inserted_at),
            updatedAt = parseTime(archive.updated_at),
            messages = messages,
            rootMessageId = root?.id,
        )
    }

    private fun fragmentsToBlocks(fragments: List<JsonObject>, sessionId: String, messageId: Int): List<ReplyBlock> {
        val blocks = mutableListOf<ReplyBlock>()
        fragments.forEachIndexed { index, fragment ->
            when (fragment["type"]?.asString()) {
                "RESPONSE" -> fragment["content"]?.asString()?.let { blocks += ReplyBlock("text", text = it) }
                "TEMPLATE_RESPONSE" -> fragment["content"]?.asString()?.let {
                    blocks += ReplyBlock("text", text = it, isTemplate = true)
                }
                "THINK" -> fragment["content"]?.asString()?.let {
                    blocks += ReplyBlock(
                        "thinking",
                        thinking = it,
                        elapsedSeconds = fragment["elapsed_secs"]?.asDouble()?.toInt(),
                    )
                }
                "TOOL_SEARCH" -> {
                    val toolId = "history-$sessionId-$messageId-$index"
                    val queries = fragment["queries"]?.let { element ->
                        runCatching { element.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
                    }.orEmpty()
                    blocks += ReplyBlock(
                        type = "server_tool_use",
                        id = toolId,
                        name = "deepseek_web_search",
                        kind = "search",
                        input = buildJsonObject {
                            put("action", "search")
                            put("query", queries.firstOrNull().orEmpty())
                            put("queries", JsonArray(queries.map(::JsonPrimitive)))
                        },
                    )
                    val status = fragment["status"]?.asString() ?: "FINISHED"
                    if (status != "WIP") {
                        val results = fragment["results"]?.let { element ->
                            runCatching { element.jsonArray }.getOrNull()
                        }.orEmpty()
                        val content = if (status == "FAILED") {
                            buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "web_search_tool_result_error")
                                    put("error_code", "unavailable")
                                })
                            }
                        } else {
                            buildJsonArray {
                                results.forEach { item ->
                                    val obj = item as? JsonObject ?: return@forEach
                                    val url = obj["url"]?.asString() ?: return@forEach
                                    add(buildJsonObject {
                                        put("type", "web_search_result")
                                        put("url", url)
                                        put("title", obj["title"]?.asString() ?: url)
                                        put("snippet", obj["snippet"]?.asString().orEmpty())
                                        put("site_name", obj["site_name"]?.asString())
                                        put("site_icon", obj["site_icon"]?.asString())
                                        put("page_age", obj["published_at"]?.asString())
                                    })
                                }
                            }
                        }
                        blocks += ReplyBlock(
                            type = "web_search_tool_result",
                            toolUseId = toolId,
                            kind = "search",
                            content = content,
                        )
                    }
                }
            }
        }
        return blocks
    }

    private fun archiveStatus(message: ChatMessage): String = when {
        message.refusal -> "CONTENT_FILTER"
        message.isUser -> "FINISHED"
        message.status == "done" && message.stopReason == "max_tokens" -> "INCOMPLETE"
        message.status == "done" -> "FINISHED"
        message.status == "stopped" -> "INCOMPLETE"
        message.status == "error" -> "FAILED"
        else -> "WIP"
    }

    private fun localStatus(status: String): String = when (status) {
        "FINISHED", "CONTENT_FILTER" -> "done"
        "FAILED", "CONTEXT_LENGTH_EXCEEDED", "TIMEOUT" -> "error"
        else -> "stopped"
    }

    private fun iso(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    private fun parseTime(value: String): Long =
        runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(System.currentTimeMillis())

    private fun safe(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64).ifBlank { "file" }

    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.contentOrNull
    private fun JsonElement.asInt(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement.asLong(): Long? = (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
    private fun JsonElement.asDouble(): Double? = (this as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
    private fun JsonElement.asBoolean(): Boolean? = (this as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
}
