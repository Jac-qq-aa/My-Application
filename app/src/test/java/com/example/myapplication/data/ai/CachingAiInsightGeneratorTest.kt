package com.example.myapplication.data.ai

import com.example.myapplication.data.FeedCategory
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.FeedItemType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CachingAiInsightGeneratorTest {
    @Test
    fun generate_reusesCachedInsightForSameAd() = runBlocking {
        var callCount = 0
        val generator = CachingAiInsightGenerator(
            delegate = AiInsightGenerator {
                callCount += 1
                AiAdInsight(
                    summary = "generated summary",
                    tags = listOf("generated"),
                    source = AiInsightSource.LOCAL_RULE
                )
            },
            cache = MemoryAiInsightCache()
        )

        val first = generator.generate(testItem("ad_1"))
        val second = generator.generate(testItem("ad_1"))

        assertEquals(first, second)
        assertEquals(1, callCount)
    }

    @Test
    fun generate_usesAdIdAsCacheBoundary() = runBlocking {
        var callCount = 0
        val generator = CachingAiInsightGenerator(
            delegate = AiInsightGenerator { item ->
                callCount += 1
                AiAdInsight(
                    summary = "summary for ${item.id}",
                    tags = listOf(item.id),
                    source = AiInsightSource.LOCAL_RULE
                )
            },
            cache = MemoryAiInsightCache()
        )

        val first = generator.generate(testItem("ad_1"))
        val second = generator.generate(testItem("ad_2"))

        assertEquals("summary for ad_1", first.summary)
        assertEquals("summary for ad_2", second.summary)
        assertEquals(2, callCount)
    }

    private fun testItem(id: String): FeedItem {
        return FeedItem(
            id = id,
            title = "测试广告",
            description = "测试描述",
            type = FeedItemType.IMAGE_BIG,
            category = FeedCategory.FEATURED,
            coverUrl = "https://example.com/cover.jpg",
            likesCount = 1,
            commentsCount = 1,
            isLiked = false,
            isCollected = false,
            aiSummary = "旧摘要",
            aiTags = listOf("旧标签")
        )
    }
}

