package com.example.myapplication.data.ai

import com.example.myapplication.tracking.TrackingStats

class LocalRuleStatsInsightGenerator : StatsInsightGenerator {
    override suspend fun generate(stats: TrackingStats): AiStatsInsight {
        val ctr = stats.clickThroughRate
        val interactionRate = stats.interactionRate

        return AiStatsInsight(
            summary = buildSummary(stats, ctr),
            highlights = buildHighlights(stats, ctr, interactionRate),
            risks = buildRisks(stats, interactionRate),
            suggestions = buildSuggestions(stats, ctr, interactionRate),
            source = AiStatsInsightSource.LOCAL_RULE
        )
    }

    private fun buildSummary(stats: TrackingStats, ctr: Float): String {
        return when {
            stats.exposureCount == 0 -> "当前还没有有效曝光，暂时无法判断广告表现。"
            stats.exposureCount < 10 -> "当前已有初步互动数据，但样本量较少，结论需要继续观察。"
            ctr >= 0.5f -> "当前点击率较高，广告内容对用户有较强吸引力。"
            ctr >= 0.2f -> "当前点击率处于中等水平，广告具备一定吸引力。"
            else -> "当前点击率偏低，广告标题、封面或推荐排序需要优化。"
        }
    }

    private fun buildHighlights(
        stats: TrackingStats,
        ctr: Float,
        interactionRate: Float
    ): List<String> {
        return listOfNotNull(
            if (ctr >= 0.2f && stats.exposureCount > 0) "点击率已有可观察表现。" else null,
            if (interactionRate >= 0.15f && stats.exposureCount > 0) "核心互动率表现较好。" else null,
            if (stats.likeCount > 0) "点赞行为说明部分内容产生正向反馈。" else null,
            if (stats.collectCount > 0) "收藏行为说明广告有后续查看价值。" else null,
            if (stats.shareCount > 0) "分享行为说明部分广告具备传播价值。" else null
        ).ifEmpty {
            listOf("当前数据仍在积累，建议先增加曝光样本。")
        }.take(3)
    }

    private fun buildRisks(stats: TrackingStats, interactionRate: Float): List<String> {
        return listOfNotNull(
            if (stats.exposureCount < 10) "曝光样本较少，统计结论稳定性不足。" else null,
            if (stats.clickCount == 0 && stats.exposureCount > 0) "已有曝光但暂无点击，首屏吸引力不足。" else null,
            if (stats.shareCount == 0) "分享次数为 0，广告传播性暂未体现。" else null,
            if (interactionRate < 0.1f && stats.exposureCount >= 10) "互动率偏低，用户深度参与不足。" else null
        ).ifEmpty {
            listOf("当前未发现明显风险，继续观察趋势变化。")
        }.take(3)
    }

    private fun buildSuggestions(
        stats: TrackingStats,
        ctr: Float,
        interactionRate: Float
    ): List<String> {
        return listOfNotNull(
            if (stats.exposureCount < 10) "先积累更多曝光，再判断广告表现。" else null,
            if (ctr < 0.2f && stats.exposureCount > 0) "优化低点击广告的标题、封面和 CTA。" else null,
            if (interactionRate < 0.15f && stats.exposureCount > 0) "强化收藏、分享等深度互动入口。" else null,
            "优先提升高点击广告的曝光权重。",
            "持续观察 CTR 和互动率的变化。"
        ).take(3)
    }
}
