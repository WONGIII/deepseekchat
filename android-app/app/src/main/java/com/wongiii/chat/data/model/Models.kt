package com.wongiii.chat.data.model

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.UUID

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class AccentColor(val color: Long, val softLight: Long, val softDark: Long) {
    BLUE(0xFF4D6BFE, 0xFFEDF3FE, 0xFF283142),
    TEAL(0xFF0E9F9A, 0xFFE4F8F6, 0xFF183634),
    PURPLE(0xFF7C5CFC, 0xFFF0EDFF, 0xFF2E2845),
    GREEN(0xFF2E9E6B, 0xFFE7F7EF, 0xFF1C3529),
    ORANGE(0xFFE98535, 0xFFFDF0E5, 0xFF3D2C1E);

    val compose: Color get() = Color(color)
    val soft: Color get() = Color(if (ThemeMode.DARK == ThemeMode.SYSTEM) softDark else softLight)
}

@Serializable
enum class BubbleStyle { TINTED, OUTLINED, NEUTRAL }

@Serializable
enum class BackgroundStyle { PLAIN, GRADIENT, CUSTOM }

@Serializable
enum class FontSize(val scale: Float) {
    SMALL(0.9f), NORMAL(1f), LARGE(1.12f), XLARGE(1.25f)
}

@Serializable
enum class TtsVoice(val api: String) {
    STELLA("stella"), TIDE("tide")
}

@Serializable
enum class TtsEmotion(val api: String, val label: String) {
    AUTO("auto", "自动"),
    SLOW("slow", "慢速"),
    GENTLE("gentle", "温柔"),
    HAPPY("happy", "开心"),
    SAD("sad", "悲伤"),
    SERIOUS("serious", "严肃"),
    EXCITED("excited", "兴奋"),
    DYNAMIC("dynamic", "动态")
}

@Serializable
data class NetworkSettings(
    val llmBaseUrl: String? = null,
    val searchBaseUrl: String? = null,
    val speechBaseUrl: String? = null,
    val llmApiKey: String? = null,
    val searchApiKey: String? = null,
    val speechApiKey: String? = null,
) {
    fun isEmpty(): Boolean = listOf(
        llmBaseUrl, searchBaseUrl, speechBaseUrl, llmApiKey, searchApiKey, speechApiKey
    ).all { it.isNullOrBlank() }
}

@Serializable
data class TextCitation(
    val type: String = "web_search_result_location",
    val url: String = "",
    val title: String? = null,
    @SerialName("cited_text") val citedText: String? = null,
    @SerialName("encrypted_index") val encryptedIndex: String? = null,
)

@Serializable
data class ReplyBlock(
    val type: String,
    val text: String? = null,
    val citations: List<TextCitation> = emptyList(),
    val thinking: String? = null,
    val signature: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val elapsedSeconds: Int? = null,
    val isTemplate: Boolean = false,
    val id: String? = null,
    val name: String? = null,
    val input: JsonObject? = null,
    val kind: String? = null,
    @SerialName("tool_use_id") val toolUseId: String? = null,
    val content: JsonElement? = null,
    @SerialName("caller") val caller: JsonObject? = null,
)

@Serializable
data class FileAttachment(
    val id: String = newId(),
    val name: String = "",
    val size: Long = 0,
    val isImage: Boolean = false,
    val mimeType: String = "",
    val localPath: String? = null,
    val serverId: String? = null,
    val textContent: String? = null,
    val extraction: String = "none",
    val extractionError: String? = null,
    val truncated: Boolean = false,
    val width: Int = 0,
    val height: Int = 0,
)

@Serializable
data class MessageError(
    val kind: String,
    val message: String,
    val status: Int? = null,
    val retryable: Boolean = false,
)

@Serializable
data class TokenUsage(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("cache_read_input_tokens") val cacheReadTokens: Int = 0,
    @SerialName("cache_creation_input_tokens") val cacheCreationTokens: Int = 0,
)

/**
 * Flat message tree. role = user/assistant. User messages use content/files; assistant
 * messages use blocks/status. selectedChildId selects the branch shown in the conversation.
 */
@Serializable
data class ChatMessage(
    val id: String = newId(),
    val role: String = "user",
    val content: String = "",
    val files: List<FileAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val parentId: String? = null,
    val selectedChildId: String? = null,
    val model: String? = null,
    val thinking: Boolean? = null,
    val search: Boolean? = null,
    val latestReminder: String? = null,
    val prefix: Boolean = false,
    val status: String = "pending",
    val blocks: List<ReplyBlock> = emptyList(),
    val stopReason: String? = null,
    val usage: TokenUsage? = null,
    val error: MessageError? = null,
    val refusal: Boolean = false,
) {
    val isUser: Boolean get() = role == "user"
}

@Serializable
data class ChatSession(
    val id: String = newId(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    val rootMessageId: String? = null,
)

@Serializable
data class SessionSummary(
    val id: String,
    val title: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val snippet: String? = null,
)

fun ChatSession.summary() = SessionSummary(id, title, createdAt, updatedAt)

fun ChatSession.path(): List<ChatMessage> {
    if (messages.isEmpty()) return emptyList()
    val byParent = messages.groupBy { it.parentId }
    val root = messages.firstOrNull { it.id == rootMessageId }
        ?: messages.firstOrNull { it.parentId == null }
        ?: messages.first()
    val output = mutableListOf<ChatMessage>()
    var current: ChatMessage? = root
    val guard = mutableSetOf<String>()
    while (current != null && guard.add(current.id)) {
        output += current
        val children = byParent[current.id].orEmpty()
        current = children.firstOrNull { it.id == current.selectedChildId }
            ?: children.lastOrNull()
    }
    return output
}

fun ChatSession.siblings(message: ChatMessage): List<ChatMessage> =
    messages.filter { it.parentId == message.parentId }
        .ifEmpty { listOf(message) }

fun ChatSession.withUpdatedAt(now: Long = System.currentTimeMillis()) = copy(updatedAt = now)

@Serializable
data class NetworkFieldRaw(
    @SerialName("llmBaseUrl") val llmBaseUrl: String? = null,
    @SerialName("searchBaseUrl") val searchBaseUrl: String? = null,
    @SerialName("speechBaseUrl") val speechBaseUrl: String? = null,
    @SerialName("llmApiKey") val llmApiKey: String? = null,
    @SerialName("searchApiKey") val searchApiKey: String? = null,
    @SerialName("speechApiKey") val speechApiKey: String? = null,
)
