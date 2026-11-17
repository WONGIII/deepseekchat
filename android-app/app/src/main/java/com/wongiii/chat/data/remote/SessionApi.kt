package com.wongiii.chat.data.remote

import com.wongiii.chat.data.model.AppJson
import com.wongiii.chat.data.model.ChatSession
import com.wongiii.chat.data.model.NetworkSettings
import com.wongiii.chat.data.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class NetworkEnvelope(val settings: NetworkSettings? = null)

@Serializable
private data class SearchResultDto(
    val id: String,
    val title: String = "",
    val snippet: String = "",
    val updatedAt: Long = 0,
)

class SessionApi(
    private val factory: HttpClientFactory,
    private val serverUrl: String,
) {
    private val client get() = factory.client
    private val base get() = serverUrl.trimEnd('/')

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpError(response.code, readErrorMessage(text, response.code))
            text
        }
    }

    suspend fun list(): List<SessionSummary> {
        val text = call(Request.Builder().url(endpoint(base, "api/sessions")).get().build())
        if (text.isBlank()) return emptyList()
        return AppJson.decodeFromString(ListSerializer(SessionSummary.serializer()), text)
    }

    suspend fun load(id: String): ChatSession? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint(base, "api/sessions/${encode(id)}")).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@use null
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpError(response.code, readErrorMessage(text, response.code))
            AppJson.decodeFromString(ChatSession.serializer(), text)
        }
    }

    suspend fun save(session: ChatSession): SessionSummary {
        val body = AppJson.encodeToString(ChatSession.serializer(), session).toRequestBody(JSON_MEDIA)
        val text = call(
            Request.Builder()
                .url(endpoint(base, "api/sessions/${encode(session.id)}"))
                .put(body)
                .build()
        )
        return AppJson.decodeFromString(SessionSummary.serializer(), text)
    }

    suspend fun rename(id: String, title: String): SessionSummary {
        val body = buildJsonObject { put("title", title) }.let { jsonBody(it) }
        val text = call(
            Request.Builder()
                .url(endpoint(base, "api/sessions/${encode(id)}"))
                .patch(body)
                .build()
        )
        return AppJson.decodeFromString(SessionSummary.serializer(), text)
    }

    suspend fun delete(id: String) {
        call(
            Request.Builder()
                .url(endpoint(base, "api/sessions/${encode(id)}"))
                .delete()
                .build()
        )
    }

    suspend fun replaceAll(sessions: List<ChatSession>): List<SessionSummary> {
        val array = JsonArray(sessions.map { AppJson.encodeToJsonElement(ChatSession.serializer(), it) })
        val body = buildJsonObject { put("sessions", array) }.let { jsonBody(it) }
        val text = call(
            Request.Builder()
                .url(endpoint(base, "api/sessions"))
                .put(body)
                .build()
        )
        return AppJson.decodeFromString(ListSerializer(SessionSummary.serializer()), text)
    }

    suspend fun search(query: String, limit: Int = 30): List<SessionSummary> {
        val url = endpoint(base, "api/sessions/search")
            .let { "$it?q=${encode(query)}&limit=$limit" }
        val text = call(Request.Builder().url(url).get().build())
        if (text.isBlank()) return emptyList()
        return runCatching {
            AppJson.decodeFromString(ListSerializer(SearchResultDto.serializer()), text)
                .map { SessionSummary(it.id, it.title, it.updatedAt, it.updatedAt, it.snippet) }
        }.getOrElse {
            runCatching {
                val obj = AppJson.parseToJsonElement(text)
                val arr = (obj as? kotlinx.serialization.json.JsonObject)?.get("results") as? JsonArray
                arr?.mapNotNull { element ->
                    runCatching {
                        val dto = AppJson.decodeFromJsonElement(SearchResultDto.serializer(), element)
                        SessionSummary(dto.id, dto.title, dto.updatedAt, dto.updatedAt, dto.snippet)
                    }.getOrNull()
                }.orEmpty()
            }.getOrDefault(emptyList())
        }
    }

    suspend fun networkSettings(): NetworkSettings? {
        val text = call(Request.Builder().url(endpoint(base, "api/settings/network")).get().build())
        val env = runCatching {
            AppJson.decodeFromString(NetworkEnvelope.serializer(), text)
        }.getOrNull()
        return env?.settings
    }

    suspend fun saveNetworkSettings(settings: NetworkSettings) {
        val body = buildJsonObject {
            put("settings", AppJson.encodeToJsonElement(NetworkSettings.serializer(), settings))
        }.let { jsonBody(it) }
        call(
            Request.Builder()
                .url(endpoint(base, "api/settings/network"))
                .put(body)
                .build()
        )
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun encodeQuery(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
