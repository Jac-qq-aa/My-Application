package com.example.myapplication.data.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val CurrentAiInsightModelVersion = "qwen2.5-0.5b-v1"

interface AiInsightCache {
    fun get(itemId: String, modelVersion: String = CurrentAiInsightModelVersion): AiAdInsight?
    fun put(itemId: String, insight: AiAdInsight, modelVersion: String = CurrentAiInsightModelVersion)
}

class SharedPreferencesAiInsightCache(context: Context) : AiInsightCache {
    private val preferences = context.getSharedPreferences("ai_insight_cache", Context.MODE_PRIVATE)

    override fun get(itemId: String, modelVersion: String): AiAdInsight? {
        val raw = preferences.getString(cacheKey(itemId, modelVersion), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val tagsJson = json.optJSONArray("tags") ?: JSONArray()
            val tags = buildList {
                for (index in 0 until tagsJson.length()) {
                    val tag = tagsJson.optString(index).trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            }
            AiAdInsight(
                summary = json.getString("summary"),
                tags = tags,
                source = AiInsightSource.valueOf(json.getString("source"))
            )
        }.getOrNull()
    }

    override fun put(itemId: String, insight: AiAdInsight, modelVersion: String) {
        val json = JSONObject()
            .put("summary", insight.summary)
            .put("tags", JSONArray(insight.tags))
            .put("source", insight.source.name)

        preferences.edit()
            .putString(cacheKey(itemId, modelVersion), json.toString())
            .apply()
    }

    private fun cacheKey(itemId: String, modelVersion: String): String {
        return "$modelVersion:$itemId"
    }
}

class MemoryAiInsightCache : AiInsightCache {
    private val cache = mutableMapOf<String, AiAdInsight>()

    override fun get(itemId: String, modelVersion: String): AiAdInsight? {
        return cache[cacheKey(itemId, modelVersion)]
    }

    override fun put(itemId: String, insight: AiAdInsight, modelVersion: String) {
        cache[cacheKey(itemId, modelVersion)] = insight
    }

    private fun cacheKey(itemId: String, modelVersion: String): String {
        return "$modelVersion:$itemId"
    }
}

class CachingAiInsightGenerator(
    private val delegate: AiInsightGenerator,
    private val cache: AiInsightCache
) : AiInsightGenerator {
    override suspend fun generate(item: com.example.myapplication.data.FeedItem): AiAdInsight {
        val cached = cache.get(item.id)
        if (cached != null) {
            return cached
        }

        val generated = delegate.generate(item)
        cache.put(item.id, generated)
        return generated
    }
}
