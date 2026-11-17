package com.wongiii.chat.data.remote

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.atomic.AtomicBoolean

class LlmApi(private val factory: HttpClientFactory) {
    private val client get() = factory.client

    fun stream(baseUrl: String, apiKey: String, body: JsonObject): Flow<JsonObject> = callbackFlow {
        val request = Request.Builder()
            .url(endpoint(baseUrl, "messages"))
            .header("x-api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(jsonBody(buildJsonObject {
                body.forEach { (key, value) -> put(key, value) }
                put("stream", true)
            }))
            .build()
        val finished = AtomicBoolean(false)
        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                if (!response.isSuccessful) {
                    val text = runCatching { response.body?.string() }.getOrNull()
                    if (finished.compareAndSet(false, true)) {
                        close(HttpError(response.code, readErrorMessage(text, response.code)))
                    }
                }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val parsed = runCatching { ApiJson.parseToJsonElement(data) as? JsonObject }.getOrNull()
                if (parsed != null) trySend(parsed)
            }

            override fun onClosed(eventSource: EventSource) {
                if (finished.compareAndSet(false, true)) close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (finished.compareAndSet(false, true)) {
                    val text = runCatching { response?.body?.string() }.getOrNull()
                    val status = response?.code ?: 0
                    if (status != 0) {
                        close(HttpError(status, readErrorMessage(text, status)))
                    } else {
                        close(t ?: HttpError(0, "连接中断"))
                    }
                }
            }
        }
        val source = EventSources.createFactory(client).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    suspend fun fetchModels(baseUrl: String, apiKey: String): List<String> {
        val urls = linkedSetOf(endpoint(baseUrl, "models"))
        runCatching {
            val uri = java.net.URI(baseUrl)
            val origin = "${uri.scheme}://${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}"
            urls += endpoint(origin, "models")
        }
        var lastError: Throwable? = null
        for (url in urls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("x-api-key", apiKey)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw HttpError(response.code, readErrorMessage(text, response.code))
                    }
                    val ids = parseModelIds(text)
                    if (ids == null) {
                        lastError = HttpError(0, "模型接口返回为空或格式不支持")
                        return@use
                    }
                    return ids
                }
            } catch (error: Throwable) {
                lastError = error
                val pathMismatch = error is HttpError && (error.status == 404 || error.status == 0)
                if (!pathMismatch) throw error
            }
        }
        throw lastError ?: HttpError(0, "没有可用模型接口")
    }

    private fun parseModelIds(text: String): List<String>? {
        if (text.isBlank()) return null
        val root = runCatching { ApiJson.parseToJsonElement(text) }.getOrNull() ?: return null
        val rows: JsonArray? = when (root) {
            is JsonObject -> (root["data"] as? JsonArray)
                ?: (root["models"] as? JsonArray)
                ?: (root["result"] as? JsonArray)
            is JsonArray -> root
            else -> null
        }
        if (rows == null) return null
        val ids = rows.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                is JsonObject -> (item["id"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    ?: (item["name"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                    ?: (item["model"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                else -> null
            }
        }.distinct()
        return ids
    }

}
