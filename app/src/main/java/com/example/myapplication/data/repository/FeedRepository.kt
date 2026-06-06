package com.example.myapplication.data.repository

import android.content.Context
import com.example.myapplication.data.FeedComment
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.MockFeedDataSource
import com.example.myapplication.data.ai.AiAdInsight
import com.example.myapplication.data.ai.AiInsightGenerator
import com.example.myapplication.data.ai.CachingAiInsightGenerator
import com.example.myapplication.data.ai.HybridAiInsightGenerator
import com.example.myapplication.data.ai.SQLiteAiInsightCache
import com.example.myapplication.data.local.FeedCommentStore
import com.example.myapplication.data.local.FeedInteractionStore

interface FeedRepository {
    suspend fun loadFeedItems(
        page: Int,
        pageSize: Int,
        refreshSeed: Int,
        networkDelayMillis: Long = 1_000
    ): List<FeedItem>

    suspend fun generateAiInsight(item: FeedItem): AiAdInsight

    fun toggleLike(item: FeedItem): FeedItem

    fun toggleCollect(item: FeedItem): FeedItem

    fun getComments(itemId: String): List<FeedComment>

    fun addComment(itemId: String, content: String): FeedComment
}

class DefaultFeedRepository(
    context: Context,
    private val interactionStore: FeedInteractionStore = FeedInteractionStore(context),
    private val commentStore: FeedCommentStore = FeedCommentStore(context),
    private val aiInsightGenerator: AiInsightGenerator = CachingAiInsightGenerator(
        delegate = HybridAiInsightGenerator(),
        cache = SQLiteAiInsightCache(context)
    )
) : FeedRepository {
    override suspend fun loadFeedItems(
        page: Int,
        pageSize: Int,
        refreshSeed: Int,
        networkDelayMillis: Long
    ): List<FeedItem> {
        return MockFeedDataSource.loadFeedItems(
            page = page,
            pageSize = pageSize,
            refreshSeed = refreshSeed,
            networkDelayMillis = networkDelayMillis
        ).restorePersistedInteractions()
    }

    override suspend fun generateAiInsight(item: FeedItem): AiAdInsight {
        return aiInsightGenerator.generate(item)
    }

    override fun toggleLike(item: FeedItem): FeedItem {
        val nextLiked = !item.isLiked
        interactionStore.setLiked(item.id, nextLiked)
        return item.copy(
            isLiked = nextLiked,
            likesCount = if (nextLiked) item.likesCount + 1 else (item.likesCount - 1).coerceAtLeast(0)
        )
    }

    override fun toggleCollect(item: FeedItem): FeedItem {
        val nextCollected = !item.isCollected
        interactionStore.setCollected(item.id, nextCollected)
        return item.copy(isCollected = nextCollected)
    }

    override fun getComments(itemId: String): List<FeedComment> {
        return commentStore.getComments(itemId)
    }

    override fun addComment(itemId: String, content: String): FeedComment {
        return commentStore.addComment(itemId, content)
    }

    private fun List<FeedItem>.restorePersistedInteractions(): List<FeedItem> {
        return map { item ->
            val restoredLiked = if (interactionStore.hasLikeOverride(item.id)) {
                interactionStore.isLiked(item.id)
            } else {
                item.isLiked
            }
            val restoredCollected = if (interactionStore.hasCollectOverride(item.id)) {
                interactionStore.isCollected(item.id)
            } else {
                item.isCollected
            }
            item.copy(
                isLiked = restoredLiked,
                isCollected = restoredCollected,
                likesCount = when {
                    restoredLiked && !item.isLiked -> item.likesCount + 1
                    !restoredLiked && item.isLiked -> (item.likesCount - 1).coerceAtLeast(0)
                    else -> item.likesCount
                }
            )
        }
    }
}
