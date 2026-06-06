package com.example.myapplication.tracking

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class TrackingStore(context: Context) {
    private val databaseHelper = TrackingDatabaseHelper(context.applicationContext)

    fun getExposedItemIds(): Set<String> {
        databaseHelper.readableDatabase.query(
            TABLE_TRACKING_EVENTS,
            arrayOf(COLUMN_ITEM_ID),
            "$COLUMN_ACTION = ?",
            arrayOf(ACTION_EXPOSURE),
            null,
            null,
            null
        ).use { cursor ->
            return buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ITEM_ID)))
                }
            }
        }
    }

    fun getStats(): TrackingStats {
        databaseHelper.readableDatabase.query(
            TABLE_TRACKING_EVENTS,
            arrayOf(COLUMN_ACTION),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            var exposureCount = 0
            var clickCount = 0
            var likeCount = 0
            var collectCount = 0
            var shareCount = 0

            while (cursor.moveToNext()) {
                when (cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACTION))) {
                    ACTION_EXPOSURE -> exposureCount += 1
                    ACTION_LIKE -> {
                        clickCount += 1
                        likeCount += 1
                    }
                    ACTION_COLLECT -> {
                        clickCount += 1
                        collectCount += 1
                    }
                    ACTION_SHARE -> {
                        clickCount += 1
                        shareCount += 1
                    }
                    else -> clickCount += 1
                }
            }

            return TrackingStats(
                exposureCount = exposureCount,
                clickCount = clickCount,
                likeCount = likeCount,
                collectCount = collectCount,
                shareCount = shareCount
            )
        }
    }

    fun saveExposure(event: ExposureEvent) {
        databaseHelper.writableDatabase.insertWithOnConflict(
            TABLE_TRACKING_EVENTS,
            null,
            ContentValues().apply {
                put(COLUMN_ITEM_ID, event.itemId)
                put(COLUMN_ACTION, ACTION_EXPOSURE)
                put(COLUMN_VISIBLE_RATIO, event.visibleRatio)
                put(COLUMN_STAY_MILLIS, event.stayMillis)
                put(COLUMN_TIMESTAMP_MILLIS, event.timestampMillis)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun saveClick(event: ClickEvent) {
        databaseHelper.writableDatabase.insert(
            TABLE_TRACKING_EVENTS,
            null,
            ContentValues().apply {
                put(COLUMN_ITEM_ID, event.itemId)
                put(COLUMN_ACTION, event.action)
                put(COLUMN_TIMESTAMP_MILLIS, event.timestampMillis)
            }
        )
    }

    private class TrackingDatabaseHelper(context: Context) : SQLiteOpenHelper(
        context,
        DATABASE_NAME,
        null,
        DATABASE_VERSION
    ) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_TRACKING_EVENTS (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_ITEM_ID TEXT NOT NULL,
                    $COLUMN_ACTION TEXT NOT NULL,
                    $COLUMN_VISIBLE_RATIO REAL,
                    $COLUMN_STAY_MILLIS INTEGER,
                    $COLUMN_TIMESTAMP_MILLIS INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE UNIQUE INDEX ${TABLE_TRACKING_EVENTS}_${ACTION_EXPOSURE}_unique
                ON $TABLE_TRACKING_EVENTS($COLUMN_ITEM_ID, $COLUMN_ACTION)
                WHERE $COLUMN_ACTION = '$ACTION_EXPOSURE'
                """.trimIndent()
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "tracking_events.db"
        const val DATABASE_VERSION = 1

        const val TABLE_TRACKING_EVENTS = "tracking_events"
        const val COLUMN_ID = "id"
        const val COLUMN_ITEM_ID = "item_id"
        const val COLUMN_ACTION = "action"
        const val COLUMN_VISIBLE_RATIO = "visible_ratio"
        const val COLUMN_STAY_MILLIS = "stay_millis"
        const val COLUMN_TIMESTAMP_MILLIS = "timestamp_millis"

        const val ACTION_EXPOSURE = "exposure"
        const val ACTION_LIKE = "like"
        const val ACTION_COLLECT = "collect"
        const val ACTION_SHARE = "share"
    }
}
