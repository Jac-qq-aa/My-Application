package com.example.myapplication.data.ai

import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

private const val CurrentAiInsightModelVersion = "qianwen3.5plus-v1"

interface AiInsightCache {
    fun get(itemId: String, modelVersion: String = CurrentAiInsightModelVersion): AiAdInsight?
    fun put(itemId: String, insight: AiAdInsight, modelVersion: String = CurrentAiInsightModelVersion)
}

class SQLiteAiInsightCache(context: Context) : AiInsightCache {
    private val appContext = context.applicationContext
    private val databaseHelper = AiInsightDatabaseHelper(appContext)
    private val legacyPreferences = appContext.getSharedPreferences(
        LEGACY_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    override fun get(itemId: String, modelVersion: String): AiAdInsight? {
        migrateLegacyCacheIfNeeded()

        databaseHelper.readableDatabase.query(
            TABLE_AI_INSIGHTS,
            AI_INSIGHT_COLUMNS,
            "$COLUMN_ITEM_ID = ? AND $COLUMN_MODEL_VERSION = ?",
            arrayOf(itemId, modelVersion),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            return runCatching {
                AiAdInsight(
                    summary = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SUMMARY)),
                    tags = parseTags(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TAGS_JSON))),
                    source = AiInsightSource.valueOf(
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SOURCE))
                    )
                )
            }.getOrNull()
        }
    }

    @Synchronized
    override fun put(itemId: String, insight: AiAdInsight, modelVersion: String) {
        migrateLegacyCacheIfNeeded()

        databaseHelper.writableDatabase.insertWithOnConflict(
            TABLE_AI_INSIGHTS,
            null,
            insight.toContentValues(itemId, modelVersion),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun migrateLegacyCacheIfNeeded() {
        if (legacyPreferences.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            return
        }

        val legacyEntries = legacyPreferences.all
            .filterValues { value -> value is String }
            .mapNotNull { entry ->
                val separatorIndex = entry.key.indexOf(":")
                if (separatorIndex <= 0 || separatorIndex == entry.key.lastIndex) {
                    return@mapNotNull null
                }
                val modelVersion = entry.key.substring(0, separatorIndex)
                val itemId = entry.key.substring(separatorIndex + 1)
                Triple(itemId, modelVersion, entry.value as String)
            }

        if (legacyEntries.isNotEmpty()) {
            val database = databaseHelper.writableDatabase
            database.beginTransaction()
            try {
                legacyEntries.forEach { (itemId, modelVersion, rawInsight) ->
                    val insight = parseLegacyInsight(rawInsight) ?: return@forEach
                    database.insertWithOnConflict(
                        TABLE_AI_INSIGHTS,
                        null,
                        insight.toContentValues(itemId, modelVersion),
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                }
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }

        legacyPreferences.edit()
            .putBoolean(KEY_LEGACY_MIGRATED, true)
            .apply()
    }

    private fun parseLegacyInsight(rawInsight: String): AiAdInsight? {
        return runCatching {
            val json = JSONObject(rawInsight)
            AiAdInsight(
                summary = json.getString("summary"),
                tags = parseTags(json.optJSONArray("tags")?.toString() ?: "[]"),
                source = AiInsightSource.valueOf(json.getString("source"))
            )
        }.getOrNull()
    }

    private fun parseTags(rawTags: String): List<String> {
        val tagsJson = JSONArray(rawTags)
        return buildList {
            for (index in 0 until tagsJson.length()) {
                val tag = tagsJson.optString(index).trim()
                if (tag.isNotEmpty()) add(tag)
            }
        }
    }

    private fun AiAdInsight.toContentValues(
        itemId: String,
        modelVersion: String
    ): ContentValues {
        return ContentValues().apply {
            put(COLUMN_ITEM_ID, itemId)
            put(COLUMN_MODEL_VERSION, modelVersion)
            put(COLUMN_SUMMARY, summary)
            put(COLUMN_TAGS_JSON, JSONArray(tags).toString())
            put(COLUMN_SOURCE, source.name)
            put(COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
    }

    private class AiInsightDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_AI_INSIGHTS (
                    $COLUMN_ITEM_ID TEXT NOT NULL,
                    $COLUMN_MODEL_VERSION TEXT NOT NULL,
                    $COLUMN_SUMMARY TEXT NOT NULL,
                    $COLUMN_TAGS_JSON TEXT NOT NULL,
                    $COLUMN_SOURCE TEXT NOT NULL,
                    $COLUMN_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY ($COLUMN_ITEM_ID, $COLUMN_MODEL_VERSION)
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "ai_insights.db"
        const val DATABASE_VERSION = 1

        const val TABLE_AI_INSIGHTS = "ai_insights"
        const val COLUMN_ITEM_ID = "item_id"
        const val COLUMN_MODEL_VERSION = "model_version"
        const val COLUMN_SUMMARY = "summary"
        const val COLUMN_TAGS_JSON = "tags_json"
        const val COLUMN_SOURCE = "source"
        const val COLUMN_UPDATED_AT = "updated_at"

        val AI_INSIGHT_COLUMNS = arrayOf(
            COLUMN_ITEM_ID,
            COLUMN_MODEL_VERSION,
            COLUMN_SUMMARY,
            COLUMN_TAGS_JSON,
            COLUMN_SOURCE,
            COLUMN_UPDATED_AT
        )

        const val LEGACY_PREFERENCES_NAME = "ai_insight_cache"
        const val KEY_LEGACY_MIGRATED = "sqlite_migrated"
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
