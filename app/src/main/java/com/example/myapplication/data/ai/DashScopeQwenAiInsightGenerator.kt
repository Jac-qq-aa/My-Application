package com.example.myapplication.data.ai

import com.example.myapplication.BuildConfig
import com.example.myapplication.data.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class DashScopeQwenAiInsightGenerator(
    private val baseUrl: String = BuildConfig.QWEN_API_URL,
    private val apiKey: String = BuildConfig.QWEN_API_KEY,
    private val model: String = BuildConfig.QWEN_MODEL,
    private val timeoutMillis: Long = 10_000
) : AiInsightGenerator {
    override suspend fun generate(item: FeedItem): AiAdInsight {
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
                        writer.write(buildRequestBody(item).toString())
                    }

                    val responseCode = connection.responseCode
                    val responseText = if (responseCode in 200..299) {
                        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } else {
                        connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    }

                    if (responseCode !in 200..299) {
                        error("DashScope Qwen service error: $responseCode $responseText")
                    }

                    parseResponse(responseText)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private fun buildRequestBody(item: FeedItem): JSONObject {
        val systemPrompt = """
            你是广告内容理解助手。只输出严格 JSON，不要输出 Markdown。
            JSON 格式：{"summary":"20到60字中文摘要","tags":["品类","风格","受众","场景"]}
            tags 最多 6 个，每个 tag 最多 6 个中文字符。
        """.trimIndent()
        val userPrompt = """
            请为下面广告生成摘要和智能标签：
            标题：${item.title}
            描述：${item.description}
            类型：${item.type}
            频道：${item.category.title}
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

    private fun parseResponse(responseText: String): AiAdInsight {
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

        // 小模型偶尔会在 JSON 前后追加解释文本。这里只截取第一个 { 到最后一个 }，
        // 让“能修复的输出”继续可用；如果仍然不是合法 JSON，就抛异常交给 Hybrid 降级。
        val json = JSONObject(extractJsonObject(content))
        val summary = json.getString("summary").trim().take(60)
        val tagsJson = json.optJSONArray("tags") ?: JSONArray()
        val tags = buildList {
            for (index in 0 until tagsJson.length()) {
                val tag = tagsJson.optString(index).trim().take(6)
                if (tag.isNotEmpty()) add(tag)
            }
        }.distinct().take(6)

        require(summary.isNotEmpty()) { "Qwen summary is empty" }
        require(tags.isNotEmpty()) { "Qwen tags are empty" }

        return AiAdInsight(
            summary = "Qwen摘要：$summary",
            tags = tags,
            source = AiInsightSource.QWEN
        )
    }

    private fun extractJsonObject(content: String): String {
        val startIndex = content.indexOf('{')
        val endIndex = content.lastIndexOf('}')
        require(startIndex >= 0 && endIndex > startIndex) { "Qwen response does not contain JSON object" }
        return content.substring(startIndex, endIndex + 1)
    }
}
