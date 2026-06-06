package com.example.myapplication.data.local;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * 广告互动状态的本地持久化存储。
 *
 * 这个类特意使用 Java 编写，用来满足“用 Java 实现持久化保存”的要求。
 * 当前通过 SQLite 保存点赞和收藏的显式操作状态：
 * - App 退出后再次打开，已点赞/已收藏状态仍然存在；
 * - Kotlin 的 FeedViewModel 只调用这个类，不直接操作 SQLite；
 * - 首次使用时会兼容迁移旧 SharedPreferences 数据。
 */
public final class FeedInteractionStore {
    private static final String DATABASE_NAME = "feed_interactions.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_INTERACTIONS = "feed_interactions";
    private static final String COLUMN_ITEM_ID = "item_id";
    private static final String COLUMN_LIKE_STATE = "like_state";
    private static final String COLUMN_COLLECT_STATE = "collect_state";
    private static final String COLUMN_UPDATED_AT = "updated_at";

    private static final String LEGACY_PREF_NAME = "feed_interaction_store";
    private static final String KEY_LIKED_IDS = "liked_ids";
    private static final String KEY_UNLIKED_IDS = "unliked_ids";
    private static final String KEY_COLLECTED_IDS = "collected_ids";
    private static final String KEY_UNCOLLECTED_IDS = "uncollected_ids";
    private static final String KEY_SQLITE_MIGRATED = "sqlite_migrated";

    private final DatabaseHelper databaseHelper;
    private final SharedPreferences legacyPreferences;

    public FeedInteractionStore(Context context) {
        Context appContext = context.getApplicationContext();
        databaseHelper = new DatabaseHelper(appContext);
        legacyPreferences = appContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized boolean isLiked(String itemId) {
        migrateLegacyInteractionsIfNeeded();
        Integer state = getState(itemId, COLUMN_LIKE_STATE);
        return state != null && state == 1;
    }

    public synchronized boolean isExplicitlyUnliked(String itemId) {
        migrateLegacyInteractionsIfNeeded();
        Integer state = getState(itemId, COLUMN_LIKE_STATE);
        return state != null && state == 0;
    }

    public synchronized boolean hasLikeOverride(String itemId) {
        migrateLegacyInteractionsIfNeeded();
        return getState(itemId, COLUMN_LIKE_STATE) != null;
    }

    public synchronized boolean isCollected(String itemId) {
        migrateLegacyInteractionsIfNeeded();
        Integer state = getState(itemId, COLUMN_COLLECT_STATE);
        return state != null && state == 1;
    }

    public synchronized boolean isExplicitlyUncollected(String itemId) {
        migrateLegacyInteractionsIfNeeded();
        Integer state = getState(itemId, COLUMN_COLLECT_STATE);
        return state != null && state == 0;
    }

    public synchronized boolean hasCollectOverride(String itemId) {
        migrateLegacyInteractionsIfNeeded();
        return getState(itemId, COLUMN_COLLECT_STATE) != null;
    }

    public synchronized void setLiked(String itemId, boolean liked) {
        migrateLegacyInteractionsIfNeeded();
        updateState(itemId, COLUMN_LIKE_STATE, liked ? 1 : 0);
    }

    public synchronized void setCollected(String itemId, boolean collected) {
        migrateLegacyInteractionsIfNeeded();
        updateState(itemId, COLUMN_COLLECT_STATE, collected ? 1 : 0);
    }

    private Integer getState(String itemId, String columnName) {
        SQLiteDatabase database = databaseHelper.getReadableDatabase();
        try (Cursor cursor = database.query(
                TABLE_INTERACTIONS,
                new String[]{columnName},
                COLUMN_ITEM_ID + " = ?",
                new String[]{itemId},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst() || cursor.isNull(0)) {
                return null;
            }
            return cursor.getInt(0);
        }
    }

    private void updateState(String itemId, String columnName, int state) {
        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_ITEM_ID, itemId);
        values.put(columnName, state);
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        database.insertWithOnConflict(TABLE_INTERACTIONS, null, values, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues updateValues = new ContentValues();
        updateValues.put(columnName, state);
        updateValues.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        database.update(TABLE_INTERACTIONS, updateValues, COLUMN_ITEM_ID + " = ?", new String[]{itemId});
    }

    private void migrateLegacyInteractionsIfNeeded() {
        if (legacyPreferences.getBoolean(KEY_SQLITE_MIGRATED, false)) {
            return;
        }

        SQLiteDatabase database = databaseHelper.getWritableDatabase();
        database.beginTransaction();
        try {
            for (String itemId : getLegacyIdSet(KEY_LIKED_IDS)) {
                upsertState(database, itemId, COLUMN_LIKE_STATE, 1);
            }
            for (String itemId : getLegacyIdSet(KEY_UNLIKED_IDS)) {
                upsertState(database, itemId, COLUMN_LIKE_STATE, 0);
            }
            for (String itemId : getLegacyIdSet(KEY_COLLECTED_IDS)) {
                upsertState(database, itemId, COLUMN_COLLECT_STATE, 1);
            }
            for (String itemId : getLegacyIdSet(KEY_UNCOLLECTED_IDS)) {
                upsertState(database, itemId, COLUMN_COLLECT_STATE, 0);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }

        legacyPreferences.edit().putBoolean(KEY_SQLITE_MIGRATED, true).apply();
    }

    private Set<String> getLegacyIdSet(String key) {
        return new HashSet<>(legacyPreferences.getStringSet(key, new HashSet<>()));
    }

    private void upsertState(SQLiteDatabase database, String itemId, String columnName, int state) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_ITEM_ID, itemId);
        values.put(columnName, state);
        values.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        database.insertWithOnConflict(TABLE_INTERACTIONS, null, values, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues updateValues = new ContentValues();
        updateValues.put(columnName, state);
        updateValues.put(COLUMN_UPDATED_AT, System.currentTimeMillis());
        database.update(TABLE_INTERACTIONS, updateValues, COLUMN_ITEM_ID + " = ?", new String[]{itemId});
    }

    private static final class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            database.execSQL(
                    "CREATE TABLE " + TABLE_INTERACTIONS + " (" +
                            COLUMN_ITEM_ID + " TEXT PRIMARY KEY, " +
                            COLUMN_LIKE_STATE + " INTEGER, " +
                            COLUMN_COLLECT_STATE + " INTEGER, " +
                            COLUMN_UPDATED_AT + " INTEGER NOT NULL" +
                            ")"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        }
    }
}
