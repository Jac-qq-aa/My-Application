package com.example.myapplication.data.local

import android.content.Context
import com.example.myapplication.data.FeedComment
import org.json.JSONArray
import org.json.JSONObject

class FeedCommentStore(context: Context) {
    private val preferences = context
        .getApplicationContext()
        .getSharedPreferences("feed_comment_store", Context.MODE_PRIVATE)

    fun getComments(itemId: String): List<FeedComment> {
        val raw = preferences.getString(commentsKey(itemId), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val json = array.getJSONObject(index)
                    add(
                        FeedComment(
                            id = json.getString("id"),
                            itemId = json.getString("itemId"),
                            author = json.getString("author"),
                            content = json.getString("content"),
                            timestampLabel = json.getString("timestampLabel")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun addComment(itemId: String, content: String): FeedComment {
        val currentComments = getComments(itemId)
        val comment = FeedComment(
            id = "${itemId}_comment_${currentComments.size + 1}_${System.currentTimeMillis()}",
            itemId = itemId,
            author = "我",
            content = content,
            timestampLabel = "刚刚"
        )
        saveComments(itemId, listOf(comment) + currentComments)
        return comment
    }

    private fun saveComments(itemId: String, comments: List<FeedComment>) {
        val array = JSONArray()
        comments.forEach { comment ->
            array.put(
                JSONObject()
                    .put("id", comment.id)
                    .put("itemId", comment.itemId)
                    .put("author", comment.author)
                    .put("content", comment.content)
                    .put("timestampLabel", comment.timestampLabel)
            )
        }
        preferences.edit()
            .putString(commentsKey(itemId), array.toString())
            .apply()
    }

    private fun commentsKey(itemId: String): String {
        return "comments:$itemId"
    }
}
