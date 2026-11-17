package com.wongiii.chat.data.remote

import com.wongiii.chat.data.model.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.Request

data class SearchResult(
    val url: String,
    val title: String,
    val snippet: String,
    val summary: String,
    val siteName: String?,
    val siteIcon: String?,
    val date: String?,
)

class SearchApi(private val factory: HttpClientFactory) {
    suspend fun search(baseUrl: String, apiKey: String, query: String, count: Int = 10): List<SearchResult> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim().take(128)
            val body = buildJsonObject {
                put("query", trimmed)
                put("count", count)
                put("ori_question", trimmed)
            }
            val request = Request.Builder()
                .url(endpoint(baseUrl, "search"))
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(jsonBody(body))
                .build()
            val text = factory.client.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw HttpError(response.code, readErrorMessage(payload, response.code))
                payload
            }
            val root = runCatching { AppJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
                ?: throw HttpError(0, "搜索接口返回格式错误")
            val rows = root["results"] as? JsonArray ?: throw HttpError(0, "搜索接口缺少 results")
            val seen = mutableSetOf<String>()
            rows.mapNotNull { row ->
                val obj = row as? JsonObject ?: return@mapNotNull null
                val url = (obj["url"] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
                if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
                if (!seen.add(url)) return@mapNotNull null
                fun value(key: String): String? =
                    (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                SearchResult(
                    url = url,
                    title = value("title") ?: url,
                    snippet = value("snippet") ?: "",
                    summary = value("summary") ?: "",
                    siteName = value("site_name"),
                    siteIcon = value("site_icon"),
                    date = value("date"),
                )
            }
        }
}
