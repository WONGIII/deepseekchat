package com.wongiii.chat.data.remote

import com.wongiii.chat.data.local.AppPrefs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

val ApiJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
    isLenient = true
}

val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

class PersistentCookieJar(private val prefs: AppPrefs) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val saved = prefs.cookie(url.host) ?: return emptyList()
        return saved.split(';').mapNotNull { part ->
            val trimmed = part.trim()
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            runCatching {
                Cookie.Builder()
                    .name(trimmed.substring(0, eq).trim())
                    .value(trimmed.substring(eq + 1).trim())
                    .domain(url.host)
                    .path("/")
                    .build()
            }.getOrNull()
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        prefs.saveCookie(url.host, cookies.joinToString("; ") { "${it.name}=${it.value}" })
    }

    fun clear(host: String) = prefs.saveCookie(host, null)
}

class HttpClientFactory(private val prefs: AppPrefs) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(prefs))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun clearCookies(host: String) {
        (client.cookieJar as? PersistentCookieJar)?.clear(host)
    }
}

class HttpError(val status: Int, override val message: String) : Exception(message)

fun jsonBody(value: JsonObject): RequestBody = value.toString().toRequestBody(JSON_MEDIA)

fun readErrorMessage(body: String?, status: Int): String {
    if (body.isNullOrBlank()) return "HTTP $status"
    return runCatching {
        val json = ApiJson.parseToJsonElement(body)
        val obj = json as? JsonObject
        val error = obj?.get("error")
        if (error is JsonObject) {
            error["message"]?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        } else {
            error?.toString()?.trim('"')?.takeIf { it.isNotBlank() }
        }
    }.getOrNull() ?: body.take(300).ifBlank { "HTTP $status" }
}

fun endpoint(base: String, path: String): String = "${base.trimEnd('/')}/${path.trimStart('/')}"

object Endpoints {
    const val ORIGIN = "https://api.deepseek.com"
    const val DEFAULT_LLM = "$ORIGIN/anthropic/v1"
    const val DEFAULT_SEARCH = "$ORIGIN/v1/tools"
    const val DEFAULT_SPEECH = "$ORIGIN/v1/tools"

    fun llm(settings: com.wongiii.chat.data.model.NetworkSettings): String =
        com.wongiii.chat.data.local.normalizeBaseUrl(settings.llmBaseUrl) ?: DEFAULT_LLM

    fun search(settings: com.wongiii.chat.data.model.NetworkSettings): String =
        com.wongiii.chat.data.local.normalizeBaseUrl(settings.searchBaseUrl) ?: DEFAULT_SEARCH

    fun speech(settings: com.wongiii.chat.data.model.NetworkSettings): String =
        com.wongiii.chat.data.local.normalizeBaseUrl(settings.speechBaseUrl) ?: DEFAULT_SPEECH

    fun searchKey(settings: com.wongiii.chat.data.model.NetworkSettings): String =
        settings.searchApiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: settings.llmApiKey?.trim().orEmpty()

    fun speechKey(settings: com.wongiii.chat.data.model.NetworkSettings): String =
        settings.speechApiKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: settings.llmApiKey?.trim().orEmpty()
}
