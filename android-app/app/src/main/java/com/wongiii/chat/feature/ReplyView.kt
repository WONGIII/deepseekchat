package com.wongiii.chat.feature

import com.wongiii.chat.data.model.ChatMessage
import com.wongiii.chat.data.model.ReplyBlock
import com.wongiii.chat.data.model.TextCitation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

data class SearchSource(
    val url: String,
    val title: String,
    val snippet: String = "",
    val siteName: String? = null,
    val siteIcon: String? = null,
    val pageAge: String? = null,
)

data class SearchStep(
    val key: String,
    val queries: List<String> = emptyList(),
    val status: String = "done",
    val results: List<SearchSource> = emptyList(),
)

fun ChatMessage.searchSources(): List<SearchSource> {
    val seen = linkedSetOf<String>()
    val results = mutableListOf<SearchSource>()
    blocks.forEach { block ->
        if (block.type != "web_search_tool_result") return@forEach
        val content = block.content as? JsonArray ?: return@forEach
        content.forEach { item ->
            val obj = item as? JsonObject ?: return@forEach
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            if (!seen.add(url)) return@forEach
            results += SearchSource(
                url = url,
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: url,
                snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                siteName = obj["site_name"]?.jsonPrimitive?.contentOrNull,
                siteIcon = obj["site_icon"]?.jsonPrimitive?.contentOrNull,
                pageAge = obj["page_age"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }
    return results
}

fun ChatMessage.searchSteps(): List<SearchStep> {
    val resultsById = blocks.filter { it.type == "web_search_tool_result" }
        .associateBy { it.toolUseId.orEmpty() }
    return blocks.mapNotNull { block ->
        if (block.type != "server_tool_use" || block.kind != "search") return@mapNotNull null
        val id = block.id.orEmpty()
        val result = resultsById[id]
        val queries = runCatching {
            when (val q = block.input?.get("queries")) {
                is JsonArray -> q.mapNotNull { it.jsonPrimitive.contentOrNull }
                is JsonPrimitive -> listOfNotNull(q.contentOrNull)
                else -> emptyList()
            }
        }.getOrDefault(emptyList())
        val array = result?.content as? JsonArray
        val sources = array?.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            SearchSource(
                url = url,
                title = obj["title"]?.jsonPrimitive?.contentOrNull ?: url,
                snippet = obj["snippet"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                siteName = obj["site_name"]?.jsonPrimitive?.contentOrNull,
                siteIcon = obj["site_icon"]?.jsonPrimitive?.contentOrNull,
                pageAge = obj["page_age"]?.jsonPrimitive?.contentOrNull,
            )
        }.orEmpty()
        val failed = array?.any {
            (it as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull == "web_search_tool_result_error"
        } ?: false
        val status = when {
            result == null -> "wip"
            failed -> "failed"
            else -> "done"
        }
        SearchStep(id, queries, status, sources)
    }
}

fun TextCitation.toSource(): SearchSource = SearchSource(
    url = url,
    title = title ?: url,
)

fun ChatMessage.responseText(): String =
    blocks.filter { it.type == "text" && !it.isTemplate && it.text.orEmpty().isNotBlank() }
        .joinToString("\n\n") { it.text.orEmpty() }
