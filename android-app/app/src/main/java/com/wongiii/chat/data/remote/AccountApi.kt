package com.wongiii.chat.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Request
import org.json.JSONObject

data class AccountUser(val username: String)

class AccountApi(
    private val factory: HttpClientFactory,
    private val serverUrl: String,
) {
    private val client get() = factory.client
    private val base get() = serverUrl.trimEnd('/')

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(endpoint(base, "api/health")).get().build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun me(): AccountUser? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint(base, "api/auth/me")).get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 401) return@use null
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpError(response.code, readErrorMessage(text, response.code))
            AccountUser(JSONObject(text).optString("username"))
        }
    }

    suspend fun login(username: String, password: String): AccountUser =
        submit("api/auth/login", username, password)

    suspend fun register(username: String, password: String): AccountUser =
        submit("api/auth/register", username, password)

    private suspend fun submit(path: String, username: String, password: String): AccountUser =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("username", username)
                put("password", password)
            }
            val request = Request.Builder()
                .url(endpoint(base, path))
                .post(jsonBody(body))
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw HttpError(response.code, readErrorMessage(text, response.code))
                }
                AccountUser(JSONObject(text).optString("username"))
            }
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(endpoint(base, "api/auth/logout"))
                .post(buildJsonObject { }.let { jsonBody(it) })
                .build()
            client.newCall(request).execute().close()
        }
        factory.clearCookies(java.net.URI(base).host)
        Unit
    }

    suspend fun version(): AppVersion? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(endpoint(base, "api/app/version")).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val obj = JSONObject(response.body?.string().orEmpty())
                AppVersion(
                    version = obj.optString("version"),
                    url = obj.optString("url"),
                    notes = obj.optString("notes"),
                )
            }
        }.getOrNull()
    }
}

data class AppVersion(val version: String, val url: String, val notes: String)
