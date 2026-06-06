package com.example.myapplication.data.ai

import com.example.myapplication.BuildConfig
import com.example.myapplication.tracking.TrackingStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DashScopeQwenStatsInsightGenerator(
    private val baseUrl: String = BuildConfig.QWEN_API_URL,
    private val apiKey: String = BuildConfig.QWEN_API_KEY,
    private val model: String = BuildConfig.QWEN_MODEL,
    private val timeoutMillis: Long = 10_000
) : StatsInsightGenerator {
    override suspend fun generate(stats: TrackingStats): AiStatsInsight {
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
                        writer.write(buildRequestBody(stats).toString())
                    }

                    val responseCode = connection.responseCode
                    val responseText = if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    }

                    if (responseCode !in 200..299) {
                        error("DashScope Qwen stats service error: $responseCode $responseText")
                    }

                    parseResponse(responseText)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun buildRequestBody(stats: TrackingStats): JSONObject {
        val systemPrompt = """
            你是广告数据分析助手。只输出严格 JSON，不要输出 Markdown。
            JSON 格式：{"summary":"不超过40字","highlights":["最多3条"],"risks":["最多3条"],"suggestions":["最多3条"]}
            每条数组内容不超过24个中文字符。
        """.trimIndent()
        val userPrompt = """
            请基于以下本地埋点数据，输出广告表现解读：
            曝光次数：${stats.exposureCount}
            点击次数：${stats.clickCount}
            点赞次数：${stats.likeCount}
            收藏次数：${stats.collectCount}
            分享次数：${stats.shareCount}
            CTR：${formatRate(stats.clickThroughRate)}
            互动率：${formatRate(stats.interactionRate)}
        """.trimIndent()

        return JSONObject()
            .put("model", model)
            .put("stream", false)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )
    }

    private fun parseResponse(responseText: String): AiStatsInsight {
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
        val summary = json.getString("summary").trim().take(40)
        val highlights = json.readStringArray("highlights")
        val risks = json.readStringArray("risks")
        val suggestions = json.readStringArray("suggestions")

        require(summary.isNotEmpty()) { "Qwen stats summary is empty" }
        require(highlights.isNotEmpty() || risks.isNotEmpty() || suggestions.isNotEmpty()) {
            "Qwen stats insight is empty"
        }

        return AiStatsInsight(
            summary = "Qwen解读：$summary",
            highlights = highlights,
            risks = risks,
            suggestions = suggestions,
            source = AiStatsInsightSource.QWEN
        )
    }

    private fun JSONObject.readStringArray(name: String): List<String> {
        val array = optJSONArray(name) ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim().take(24)
                if (value.isNotEmpty()) add(value)
            }
        }.distinct().take(3)
    }

    private fun extractJsonObject(content: String): String {
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')
        require(startIndex >= 0 && endIndex > startIndex) { "Qwen response does not contain JSON object" }
        return content.substring(startIndex, endIndex + 1)
    }

    private fun formatRate(value: Float): String {
        return String.format(Locale.US, "%.2f%%", value * 100)
    }
}
