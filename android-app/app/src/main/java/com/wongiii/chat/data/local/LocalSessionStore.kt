package com.wongiii.chat.data.local

import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.SessionSummary
import com.wongiii.chat.data.model.summary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalSessionStore(root: File) {
    private val dir = File(root, "sessions_local").apply { mkdirs() }
    private val mutex = Mutex()

    suspend fun list(): List<SessionSummary> = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles { file -> file.extension == "json" }
                .orEmpty()
                .mapNotNull { file -> runCatching { decode(file) }.getOrNull() }
                .map { it.summary() }
                .sortedByDescending { it.updatedAt }
        }
    }

    suspend fun load(id: String): ChatSession? = withContext(Dispatchers.IO) {
        val file = File(dir, "$id.json")
        if (!file.exists()) null else runCatching { decode(file) }.getOrNull()
    }

    suspend fun save(session: ChatSession) = withContext(Dispatchers.IO) {
        mutex.withLock {
            write(File(dir, "${session.id}.json"), session)
        }
    }

    suspend fun rename(id: String, title: String): SessionSummary? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val file = File(dir, "$id.json")
            if (!file.exists()) return@withLock null
            val session = runCatching { decode(file) }.getOrNull() ?: return@withLock null
            val updated = session.copy(title = title.take(120), updatedAt = System.currentTimeMillis())
            write(file, updated)
            updated.summary()
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutex.withLock { File(dir, "$id.json").delete() }
        Unit
    }

    suspend fun replaceAll(sessions: List<ChatSession>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles { file -> file.extension == "json" }?.forEach { it.delete() }
            sessions.forEach { write(File(dir, "${it.id}.json"), it) }
        }
    }

    suspend fun search(query: String, limit: Int = 30): List<SessionSummary> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        if (needle.isEmpty()) return@withContext emptyList()
        mutex.withLock {
            dir.listFiles { file -> file.extension == "json" }
                .orEmpty()
                .mapNotNull { file ->
                    val session = runCatching { decode(file) }.getOrNull() ?: return@mapNotNull null
                    val content = buildString {
                        append(session.title)
                        session.messages.forEach { message ->
                            append('\n').append(message.content)
                            message.blocks.forEach { block ->
                                append('\n').append(block.text.orEmpty())
                                append('\n').append(block.thinking.orEmpty())
                            }
                        }
                    }
                    if (!content.contains(needle, ignoreCase = true)) return@mapNotNull null
                    val index = content.indexOf(needle, ignoreCase = true)
                    val start = (index - 28).coerceAtLeast(0)
                    val end = (index + needle.length + 60).coerceAtMost(content.length)
                    val snippet = content.substring(start, end).replace('\n', ' ')
                    session.summary().copy(snippet = snippet)
                }
                .sortedByDescending { it.updatedAt }
                .take(limit)
        }
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) {
        dir.listFiles { file -> file.extension == "json" }?.size ?: 0
    }

    suspend fun exportAll(): List<ChatSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            dir.listFiles { file -> file.extension == "json" }
                .orEmpty()
                .mapNotNull { runCatching { decode(it) }.getOrNull() }
        }
    }

    private fun decode(file: File): ChatSession =
        AppJson.decodeFromString(ChatSession.serializer(), file.readText(Charsets.UTF_8))

    private fun write(file: File, session: ChatSession) {
        val temp = File(file.parentFile, "${file.name}.${System.nanoTime()}.tmp")
        temp.writeText(AppJson.encodeToString(ChatSession.serializer(), session), Charsets.UTF_8)
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText(Charsets.UTF_8), Charsets.UTF_8)
            temp.delete()
        }
    }
}

data class SearchHit(
    val id: String,
    val title: String,
    val snippet: String,
    val updatedAt: Long,
)

fun SearchHit.toSummary() = SessionSummary(id, title, updatedAt, updatedAt, snippet)

fun plainSnippet(text: String, length: Int = 100): String =
    text.replace(Regex("\\s+"), " ").trim().take(length)

fun sessionDateLabel(updatedAt: Long): String {
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val date = java.time.Instant.ofEpochMilli(updatedAt).atZone(zone).toLocalDate()
    val dayDiff = java.time.temporal.ChronoUnit.DAYS.between(date, today)
    return when {
        dayDiff <= 0L -> "今天"
        dayDiff == 1L -> "昨天"
        dayDiff <= 7L -> "7 天内"
        dayDiff <= 30L -> "30 天内"
        else -> java.time.format.DateTimeFormatter.ofPattern("M 月 d 日").format(date)
    }
}

fun ChatSession.searchText(): String = buildString {
    append(title)
    messages.forEach { message ->
        append(' ').append(message.content)
        message.blocks.forEach { block ->
            append(' ').append(block.text.orEmpty())
            append(' ').append(block.thinking.orEmpty())
        }
    }
}

fun ChatMessage.hasUsableReply(): Boolean =
    !isUser && (status == "done" || status == "stopped") &&
        !refusal &&
        blocks.any { it.type == "text" && it.text.orEmpty().isNotBlank() }
