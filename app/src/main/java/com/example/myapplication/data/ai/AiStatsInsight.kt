package com.example.myapplication.data.ai

import com.example.myapplication.tracking.TrackingStats

data class AiStatsInsight(
    val summary: String,
    val highlights: List<String>,
    val risks: List<String>,
    val suggestions: List<String>,
    val source: AiStatsInsightSource
)

enum class AiStatsInsightSource {
    LOCAL_RULE,
    QWEN
}

interface StatsInsightGenerator {
    suspend fun generate(stats: TrackingStats): AiStatsInsight
}
