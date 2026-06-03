package com.example.myapplication.viewmodel

import android.app.Application
import com.example.myapplication.data.FeedCategory
import com.example.myapplication.data.FeedComment
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.FeedItemType
import com.example.myapplication.data.ai.AiAdInsight
import com.example.myapplication.data.ai.AiInsightSource
import com.example.myapplication.data.repository.FeedRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun refresh_loadsFeaturedItemsAndRestoresPersistedComments() = runTest {
        val repository = FakeFeedRepository(
            pages = mapOf(1 to listOf(testItem("ad_1", category = FeedCategory.FEATURED))),
            comments = mutableMapOf(
                "ad_1" to listOf(
                    FeedComment(
                        id = "comment_1",
                        itemId = "ad_1",
                        author = "我",
                        content = "之前发布的评论",
                        timestampLabel = "刚刚"
                    )
                )
            )
        )

        val viewModel = FeedViewModel(Application(), repository)

        assertEquals(FeedScreenState.Content, viewModel.currentScreenState.value)
        assertEquals(listOf("ad_1"), viewModel.feedItems.value.map { it.id })
        assertEquals(1, viewModel.comments.value["ad_1"]?.size)
        assertEquals(2, viewModel.allFeedItems.value.first().commentsCount)
    }

    @Test
    fun loadMore_failureExposesFooterErrorWithoutClearingCurrentItems() = runTest {
        val repository = FakeFeedRepository(
            pages = mapOf(1 to listOf(testItem("ad_1", category = FeedCategory.FEATURED))),
            failingPages = setOf(2)
        )
        val viewModel = FeedViewModel(Application(), repository)

        viewModel.loadMore()

        assertTrue(viewModel.currentLoadMoreState.value is LoadMoreState.Error)
        assertEquals(listOf("ad_1"), viewModel.feedItems.value.map { it.id })
    }

    @Test
    fun addComment_persistsThroughRepositoryAndUpdatesCommentCount() = runTest {
        val repository = FakeFeedRepository(
            pages = mapOf(1 to listOf(testItem("ad_1", category = FeedCategory.FEATURED)))
        )
        val viewModel = FeedViewModel(Application(), repository)

        viewModel.addComment("ad_1", "新增评论")

        assertEquals("新增评论", repository.comments.getValue("ad_1").first().content)
        assertEquals(1, viewModel.comments.value["ad_1"]?.size)
        assertEquals(2, viewModel.allFeedItems.value.first().commentsCount)
    }

    @Test
    fun selectTag_filtersCurrentFeedItems() = runTest {
        val repository = FakeFeedRepository(
            pages = mapOf(
                1 to listOf(
                    testItem("ad_1", category = FeedCategory.FEATURED, tags = listOf("学生党")),
                    testItem("ad_2", category = FeedCategory.FEATURED, tags = listOf("附近优惠"))
                )
            )
        )
        val viewModel = FeedViewModel(Application(), repository)

        viewModel.selectTag("学生党")

        assertEquals(listOf("ad_1"), viewModel.feedItems.value.map { it.id })
    }

    private class FakeFeedRepository(
        private val pages: Map<Int, List<FeedItem>>,
        private val failingPages: Set<Int> = emptySet(),
        val comments: MutableMap<String, List<FeedComment>> = mutableMapOf()
    ) : FeedRepository {
        override suspend fun loadFeedItems(
            page: Int,
            pageSize: Int,
            refreshSeed: Int,
            networkDelayMillis: Long
        ): List<FeedItem> {
            if (failingPages.contains(page)) {
                error("分页加载失败")
            }
            return pages[page].orEmpty()
        }

        override suspend fun generateAiInsight(item: FeedItem): AiAdInsight {
            return AiAdInsight(
                summary = item.aiSummary,
                tags = item.aiTags,
                source = AiInsightSource.LOCAL_RULE
            )
        }

        override fun toggleLike(item: FeedItem): FeedItem {
            return item.copy(
                isLiked = !item.isLiked,
                likesCount = if (item.isLiked) item.likesCount - 1 else item.likesCount + 1
            )
        }

        override fun toggleCollect(item: FeedItem): FeedItem {
            return item.copy(isCollected = !item.isCollected)
        }

        override fun getComments(itemId: String): List<FeedComment> {
            return comments[itemId].orEmpty()
        }

        override fun addComment(itemId: String, content: String): FeedComment {
            val current = comments[itemId].orEmpty()
            val comment = FeedComment(
                id = "${itemId}_comment_${current.size + 1}",
                itemId = itemId,
                author = "我",
                content = content,
                timestampLabel = "刚刚"
            )
            comments[itemId] = listOf(comment) + current
            return comment
        }
    }

    private fun testItem(
        id: String,
        category: FeedCategory,
        tags: List<String> = listOf("学生党")
    ): FeedItem {
        return FeedItem(
            id = id,
            title = "测试广告 $id",
            description = "测试描述",
            type = FeedItemType.IMAGE_BIG,
            category = category,
            coverUrl = "https://example.com/$id.jpg",
            likesCount = 1,
            commentsCount = 1,
            isLiked = false,
            isCollected = false,
            aiSummary = "测试摘要",
            aiTags = tags
        )
    }
}
