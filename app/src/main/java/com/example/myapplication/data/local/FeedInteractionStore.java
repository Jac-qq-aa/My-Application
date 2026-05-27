package com.example.myapplication.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * 广告互动状态的本地持久化存储。
 *
 * 这个类特意使用 Java 编写，用来满足“用 Java 实现持久化保存”的要求。
 * 当前保存的是点赞和收藏的广告 id 集合：
 * - App 退出后再次打开，已点赞/已收藏状态仍然存在；
 * - Kotlin 的 FeedViewModel 只调用这个类，不直接操作 SharedPreferences；
 * - 后续如果升级为 Room 数据库，可以保持 ViewModel 的调用语义基本不变。
 */
public final class FeedInteractionStore {
    private static final String PREF_NAME = "feed_interaction_store";
    private static final String KEY_LIKED_IDS = "liked_ids";
    private static final String KEY_UNLIKED_IDS = "unliked_ids";
    private static final String KEY_COLLECTED_IDS = "collected_ids";
    private static final String KEY_UNCOLLECTED_IDS = "uncollected_ids";

    private final SharedPreferences preferences;

    public FeedInteractionStore(Context context) {
        Context appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLiked(String itemId) {
        return getIdSet(KEY_LIKED_IDS).contains(itemId);
    }

    public boolean isExplicitlyUnliked(String itemId) {
        return getIdSet(KEY_UNLIKED_IDS).contains(itemId);
    }

    public boolean hasLikeOverride(String itemId) {
        return isLiked(itemId) || isExplicitlyUnliked(itemId);
    }

    public boolean isCollected(String itemId) {
        return getIdSet(KEY_COLLECTED_IDS).contains(itemId);
    }

    public boolean isExplicitlyUncollected(String itemId) {
        return getIdSet(KEY_UNCOLLECTED_IDS).contains(itemId);
    }

    public boolean hasCollectOverride(String itemId) {
        return isCollected(itemId) || isExplicitlyUncollected(itemId);
    }

    public void setLiked(String itemId, boolean liked) {
        if (liked) {
            updateId(KEY_LIKED_IDS, itemId, true);
            updateId(KEY_UNLIKED_IDS, itemId, false);
        } else {
            updateId(KEY_LIKED_IDS, itemId, false);
            updateId(KEY_UNLIKED_IDS, itemId, true);
        }
    }

    public void setCollected(String itemId, boolean collected) {
        if (collected) {
            updateId(KEY_COLLECTED_IDS, itemId, true);
            updateId(KEY_UNCOLLECTED_IDS, itemId, false);
        } else {
            updateId(KEY_COLLECTED_IDS, itemId, false);
            updateId(KEY_UNCOLLECTED_IDS, itemId, true);
        }
    }

    private Set<String> getIdSet(String key) {
        return new HashSet<>(preferences.getStringSet(key, new HashSet<>()));
    }

    private void updateId(String key, String itemId, boolean enabled) {
        Set<String> ids = getIdSet(key);
        if (enabled) {
            ids.add(itemId);
        } else {
            ids.remove(itemId);
        }
        preferences.edit().putStringSet(key, ids).apply();
    }
}
