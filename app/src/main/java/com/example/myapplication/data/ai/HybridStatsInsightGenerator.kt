package com.example.myapplication.data.ai

import com.example.myapplication.tracking.TrackingStats

class HybridStatsInsightGenerator(
    private val remoteGenerator: StatsInsightGenerator = DashScopeQwenStatsInsightGenerator(),
    private val fallbackGenerator: StatsInsightGenerator = LocalRuleStatsInsightGenerator()
) : StatsInsightGenerator {
    private var remoteDisabled = false

    override suspend fun generate(stats: TrackingStats): AiStatsInsight {
        if (remoteDisabled) {
            return fallbackGenerator.generate(stats)
        }

        return runCatching { remoteGenerator.generate(stats) }
            .getOrElse {
                remoteDisabled = true
                fallbackGenerator.generate(stats)
            }
    }
}
