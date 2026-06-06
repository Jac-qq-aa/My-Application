package com.example.myapplication.data.ai

import com.example.myapplication.data.FeedItem

/**
 * 混合 AI 生成器。
 *
 * 优先调用 Qwen API；任何异常都降级到本地规则，避免 demo 时因为模型服务不可用导致页面空白。
 */
class HybridAiInsightGenerator(
    private val remoteGenerator: AiInsightGenerator = DashScopeQwenAiInsightGenerator(),
    private val fallbackGenerator: AiInsightGenerator = LocalRuleAiInsightGenerator()
) : AiInsightGenerator {
    private var remoteDisabled = false

    override suspend fun generate(item: FeedItem): AiAdInsight {
        if (remoteDisabled) {
            return fallbackGenerator.generate(item)
        }

        return runCatching { remoteGenerator.generate(item) }
            .getOrElse {
                // 一旦 Qwen API 不可用，后续广告直接走本地规则。
                // 这样 API key、网络或模型名配置异常时，不会每条广告都等待网络超时。
                remoteDisabled = true
                fallbackGenerator.generate(item)
            }
    }
}
