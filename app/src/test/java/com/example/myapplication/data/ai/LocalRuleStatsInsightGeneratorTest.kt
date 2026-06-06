package com.example.myapplication.data.ai

import com.example.myapplication.tracking.TrackingStats
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRuleStatsInsightGeneratorTest {
    private val generator = LocalRuleStatsInsightGenerator()

    @Test
    fun generate_withoutExposure_returnsInsufficientDataInsight() = runBlocking {
        val insight = generator.generate(TrackingStats())

        assertEquals(AiStatsInsightSource.LOCAL_RULE, insight.source)
        assertTrue(insight.summary.contains("还没有有效曝光"))
        assertTrue(insight.highlights.isNotEmpty())
        assertTrue(insight.risks.any { it.contains("曝光样本较少") })
        assertTrue(insight.suggestions.any { it.contains("积累更多曝光") })
    }

    @Test
    fun generate_withHighCtr_returnsPositiveClickInsight() = runBlocking {
        val insight = generator.generate(
            TrackingStats(
                exposureCount = 20,
                clickCount = 12,
                likeCount = 3,
                collectCount = 2,
                shareCount = 1
            )
        )

        assertTrue(insight.summary.contains("点击率较高"))
        assertTrue(insight.highlights.any { it.contains("点击率") })
        assertTrue(insight.highlights.size <= 3)
        assertTrue(insight.risks.size <= 3)
        assertTrue(insight.suggestions.size <= 3)
    }
}
