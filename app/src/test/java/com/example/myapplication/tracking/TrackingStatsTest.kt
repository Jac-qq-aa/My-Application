package com.example.myapplication.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingStatsTest {
    @Test
    fun rates_areZeroWhenThereAreNoExposures() {
        val stats = TrackingStats(clickCount = 3, likeCount = 1)

        assertEquals(0f, stats.clickThroughRate)
        assertEquals(0f, stats.interactionRate)
    }

    @Test
    fun metrics_includeClickAndInteractionRatesInputs() {
        val stats = TrackingStats(
            exposureCount = 10,
            clickCount = 5,
            likeCount = 2,
            collectCount = 1,
            shareCount = 1
        )

        assertEquals(0.5f, stats.clickThroughRate)
        assertEquals(0.4f, stats.interactionRate)
        assertEquals(4, stats.interactionCount)
        assertEquals(15, stats.totalEventCount)
    }
}
