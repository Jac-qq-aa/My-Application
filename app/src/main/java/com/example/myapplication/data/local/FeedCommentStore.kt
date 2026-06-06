package com.example.myapplication.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.myapplication.data.FeedComment
import org.json.JSONArray

class FeedCommentStore(context: Context) {
    private val appContext = context.applicationContext
    private val databaseHelper = FeedCommentDatabaseHelper(appContext)
    private val legacyPreferences = appContext.getSharedPreferences(
        LEGACY_PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun getComments(itemId: String): List<FeedComment> {
        migrateLegacyCommentsIfNeeded()

        databaseHelper.readableDatabase.query(
            TABLE_COMMENTS,
            COMMENT_COLUMNS,
            "$COLUMN_ITEM_ID = ?",
            arrayOf(itemId),
            null,
            null,
            "$COLUMN_CREATED_AT DESC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    add(
                        FeedComment(
                            id = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                            itemId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ITEM_ID)),
                            author = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_AUTHOR)),
                            content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONTENT)),
                            timestampLabel = cursor.getString(
                                cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP_LABEL)
                            )
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun addComment(itemId: String, content: String): FeedComment {
        migrateLegacyCommentsIfNeeded()

        val now = System.currentTimeMillis()
        val comment = FeedComment(
            id = "${itemId}_comment_$now",
            itemId = itemId,
            author = "我",
            content = content,
            timestampLabel = "刚刚"
        )

        databaseHelper.writableDatabase.insertWithOnConflict(
            TABLE_COMMENTS,
            null,
            comment.toContentValues(now),
            SQLiteDatabase.CONFLICT_REPLACE
        )

        return comment
    }

    private fun migrateLegacyCommentsIfNeeded() {
        if (legacyPreferences.getBoolean(KEY_LEGACY_MIGRATED, false)) {
            return
        }

        val legacyEntries = legacyPreferences.all
            .filterKeys { key -> key.startsWith(LEGACY_COMMENTS_KEY_PREFIX) }
            .mapNotNull { entry ->
                val rawComments = entry.value as? String ?: return@mapNotNull null
                val itemId = entry.key.removePrefix(LEGACY_COMMENTS_KEY_PREFIX)
                itemId to rawComments
            }

        if (legacyEntries.isNotEmpty()) {
            val database = databaseHelper.writableDatabase
            database.beginTransaction()
            try {
                legacyEntries.forEach { (itemId, rawComments) ->
                    migrateLegacyComments(itemId, rawComments, database)
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

    private fun migrateLegacyComments(
        itemId: String,
        rawComments: String,
        database: SQLiteDatabase
    ) {
        runCatching {
            val comments = JSONArray(rawComments)
            val baseCreatedAt = System.currentTimeMillis()

            for (index in 0 until comments.length()) {
                val json = comments.getJSONObject(index)
                val comment = FeedComment(
                    id = json.getString("id"),
                    itemId = json.optString("itemId", itemId),
                    author = json.getString("author"),
                    content = json.getString("content"),
                    timestampLabel = json.getString("timestampLabel")
                )

                database.insertWithOnConflict(
                    TABLE_COMMENTS,
                    null,
                    comment.toContentValues(baseCreatedAt - index),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
        }
    }

    private fun FeedComment.toContentValues(createdAt: Long): ContentValues {
        return ContentValues().apply {
            put(COLUMN_ID, id)
            put(COLUMN_ITEM_ID, itemId)
            put(COLUMN_AUTHOR, author)
            put(COLUMN_CONTENT, content)
            put(COLUMN_TIMESTAMP_LABEL, timestampLabel)
            put(COLUMN_CREATED_AT, createdAt)
        }
    }

    private class FeedCommentDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_COMMENTS (
                    $COLUMN_ID TEXT PRIMARY KEY,
                    $COLUMN_ITEM_ID TEXT NOT NULL,
                    $COLUMN_AUTHOR TEXT NOT NULL,
                    $COLUMN_CONTENT TEXT NOT NULL,
                    $COLUMN_TIMESTAMP_LABEL TEXT NOT NULL,
                    $COLUMN_CREATED_AT INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX ${TABLE_COMMENTS}_${COLUMN_ITEM_ID}_index " +
                    "ON $TABLE_COMMENTS($COLUMN_ITEM_ID)"
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "feed_comments.db"
        const val DATABASE_VERSION = 1

        const val TABLE_COMMENTS = "feed_comments"
        const val COLUMN_ID = "id"
        const val COLUMN_ITEM_ID = "item_id"
        const val COLUMN_AUTHOR = "author"
        const val COLUMN_CONTENT = "content"
        const val COLUMN_TIMESTAMP_LABEL = "timestamp_label"
        const val COLUMN_CREATED_AT = "created_at"

        val COMMENT_COLUMNS = arrayOf(
            COLUMN_ID,
            COLUMN_ITEM_ID,
            COLUMN_AUTHOR,
            COLUMN_CONTENT,
            COLUMN_TIMESTAMP_LABEL,
            COLUMN_CREATED_AT
        )

        const val LEGACY_PREFERENCES_NAME = "feed_comment_store"
        const val LEGACY_COMMENTS_KEY_PREFIX = "comments:"
        const val KEY_LEGACY_MIGRATED = "sqlite_migrated"
    }
}
