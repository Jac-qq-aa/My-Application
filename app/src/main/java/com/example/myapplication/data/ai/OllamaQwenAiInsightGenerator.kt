package com.example.myapplication.data.ai

import com.example.myapplication.data.FeedItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 本地 Qwen/Ollama 客户端。
 *
 * 默认地址 `http://10.0.2.2:11434` 适用于 Android Emulator 访问电脑本机 Ollama。
 * 真机调试时需要把 baseUrl 改成电脑局域网 IP，例如 `http://192.168.1.8:11434`。
 *
 * 推荐本地部署命令：
 * 1. 安装 Ollama；
 * 2. 执行 `ollama pull qwen2.5:0.5b`；
 * 3. 执行 `ollama serve`。
 */
class OllamaQwenAiInsightGenerator(
    private val baseUrl: String = "http://10.0.2.2:11434",
    private val model: String = "qwen2.5:0.5b",
    private val timeoutMillis: Long = 6_000
) : AiInsightGenerator {
    override suspend fun generate(item: FeedItem): AiAdInsight {
        return withContext(Dispatchers.IO) {
            withTimeout(timeoutMillis) {
                val connection = (URL("$baseUrl/api/chat").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMillis.toInt()
                    readTimeout = timeoutMillis.toInt()
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
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
                        error("Qwen service error: $responseCode $responseText")
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
