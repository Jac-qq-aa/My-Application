package com.example.myapplication.data.ai

/**
 * 大模型或本地规则生成的广告理解结果。
 *
 * summary 用作卡片副标题/详情摘要；tags 用作智能标签和标签过滤入口。
 */
data class AiAdInsight(
    val summary: String,
    val tags: List<String>,
    val source: AiInsightSource
)

enum class AiInsightSource {
    QWEN,
    LOCAL_RULE
}
