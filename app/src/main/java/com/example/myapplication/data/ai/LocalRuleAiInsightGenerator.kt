package com.example.myapplication.data.ai

import com.example.myapplication.data.FeedCategory
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.FeedItemType

/**
 * 本地规则降级生成器。
 *
 * 当本地 Qwen 服务没启动、手机无法访问电脑服务、模型超时或输出不合规时，
 * 使用这套规则保证 App 仍然能展示摘要和标签。
 */
class LocalRuleAiInsightGenerator : AiInsightGenerator {
    override suspend fun generate(item: FeedItem): AiAdInsight {
        val categoryTag = when (item.category) {
            FeedCategory.FEATURED -> "精选"
            FeedCategory.ECOMMERCE -> "电商"
            FeedCategory.LOCAL -> "本地生活"
        }
        val mediaTag = when (item.type) {
            FeedItemType.IMAGE_BIG -> "大图种草"
            FeedItemType.IMAGE_SMALL -> "轻量图文"
            FeedItemType.VIDEO -> "视频创意"
        }
        val summary = "本地AI摘要：${item.title}突出${categoryTag}场景，适合通过${mediaTag}形式快速传达卖点。"
        val audienceTag = when (item.category) {
            FeedCategory.FEATURED -> "学生党"
            FeedCategory.ECOMMERCE -> "性价比"
            FeedCategory.LOCAL -> "附近优惠"
        }
        val sceneTag = if (item.title.contains("装备") || item.description.contains("装备")) {
            "运动"
        } else {
            "智能推荐"
        }
        val tags = listOf(categoryTag, mediaTag, audienceTag, sceneTag, "高转化", "智能推荐")

        return AiAdInsight(
            summary = summary.take(60),
            tags = tags.distinct().take(6),
            source = AiInsightSource.LOCAL_RULE
        )
    }
}
