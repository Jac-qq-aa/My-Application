package com.example.myapplication.data.ai

import com.example.myapplication.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DashScopeQwenSearchIntentParser(
    private val baseUrl: String = BuildConfig.QWEN_API_URL,
    private val apiKey: String = BuildConfig.QWEN_API_KEY,
    private val model: String = BuildConfig.QWEN_MODEL,
    private val timeoutMillis: Long = 10_000
) : SearchIntentParser {
    override suspend fun parse(query: String): SearchIntent {
        require(apiKey.isNotBlank()) { "Qwen API key is empty" }

        return withContext(Dispatchers.IO) {
            withTimeout(timeoutMillis) {
                val connection = (URL("${baseUrl.trimEnd('/')}/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMillis.toInt()
                    readTimeout = timeoutMillis.toInt()
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
                }

                try {
                    OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                        writer.write(buildRequestBody(query).toString())
                    }

                    val responseCode = connection.responseCode
                    val responseText = if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    }

                    if (responseCode !in 200..299) {
                        error("DashScope Qwen search intent error: $responseCode $responseText")
                    }

                    parseResponse(responseText)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun buildRequestBody(query: String): JSONObject {
        val systemPrompt = """
            你是广告搜索意图解析器。只输出严格 JSON，不要输出 Markdown。
            JSON 格式：{"keywords":["关键词"],"tags":["标签"],"category":"精选|电商|本地|null","mediaType":"video|image_big|image_small|null"}
            tags 只能从这些词中选择：学生党、性价比、运动、附近优惠、视频创意、高转化、新品、智能家居、品质生活、电商爆款、满减、包邮、复购高。
            category 和 mediaType 不确定时输出 null。
        """.trimIndent()

        return JSONObject()
            .put("model", model)
            .put("stream", false)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", query))
            )
    }

    private fun parseResponse(responseText: String): SearchIntent {
        val response = JSONObject(responseText)
        val content = response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(extractJsonObject(content))
        val keywords = json.optJSONArray("keywords").toStringList(maxSize = 8)
        val tags = json.optJSONArray("tags").toStringList(maxSize = 8)
        val category = json.optNullableString("category")
        val mediaType = json.optNullableString("mediaType")

        require(keywords.isNotEmpty() || tags.isNotEmpty() || category != null || mediaType != null) {
            "Qwen search intent is empty"
        }

        return SearchIntent(
            keywords = keywords,
            tags = tags,
            category = category,
            mediaType = mediaType,
            source = SearchIntentSource.QWEN
        )
    }

    private fun extractJsonObject(content: String): String {
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')
        require(startIndex >= 0 && endIndex > startIndex) { "Qwen response does not contain JSON object" }
        return content.substring(startIndex, endIndex + 1)
    }
}

private fun JSONArray?.toStringList(maxSize: Int): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                add(value)
            }
        }
    }.distinct().take(maxSize)
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    val value = optString(name).trim()
    return value.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
