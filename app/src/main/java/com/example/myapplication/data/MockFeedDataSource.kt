package com.example.myapplication.data

import kotlinx.coroutines.delay

/**
 * 本地 Mock 数据源。
 *
 * 真实项目中，这一层会替换为 Repository + RemoteDataSource + LocalDataSource。
 * 训练营阶段先保留清晰分层：ViewModel 只关心“拿数据”，不关心数据来自网络、数据库还是 Mock。
 */
object MockFeedDataSource {
    var failCount: Int = 0

    /**
     * 模拟网络请求。
     *
     * suspend 表示这是一个“可挂起函数”：它可以在 delay / 网络 IO 时暂停当前协程，
     * 但不会阻塞主线程，因此 UI 仍然可以流畅响应。
     */
    suspend fun loadFeedItems(
        page: Int,
        pageSize: Int,
        refreshSeed: Int,
        networkDelayMillis: Long = 1_000
    ): List<FeedItem> {
        delay(networkDelayMillis)
        if (failCount > 0) {
            failCount -= 1
            error("模拟网络异常，请稍后重试")
        }

        val covers = listOf(
            "https://images.unsplash.com/photo-1491553895911-0055eca6402d",
            "https://images.unsplash.com/photo-1523275335684-37898b6baf30",
            "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee",
            "https://images.unsplash.com/photo-1516321318423-f06f85e504b3",
            "https://images.unsplash.com/photo-1517245386807-bb43f82c33c4"
        )

        val tagPool = listOf(
            listOf("AI精选", "高转化", "新品", "学生党"),
            listOf("附近优惠", "周末可用", "到店核销", "限时"),
            listOf("智能家居", "品质生活", "运动"),
            listOf("视频创意", "强种草", "年轻人偏好", "学生党"),
            listOf("电商爆款", "满减", "包邮", "复购高", "性价比")
        )
        val videoUrls = listOf(
            "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            "https://storage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"
        )

        val startIndex = (page - 1) * pageSize
        return List(pageSize) { localIndex ->
            val index = startIndex + localIndex
            val displayIndex = refreshSeed * 1_000 + index + 1
            val type = when (index % 3) {
                0 -> FeedItemType.IMAGE_BIG
                1 -> FeedItemType.IMAGE_SMALL
                else -> FeedItemType.VIDEO
            }
            val category = when (index % 3) {
                0 -> FeedCategory.FEATURED
                1 -> FeedCategory.ECOMMERCE
                else -> FeedCategory.LOCAL
            }
            FeedItem(
                id = "refresh_${refreshSeed}_ad_${index + 1}",
                title = when (category) {
                    FeedCategory.FEATURED -> "第 ${refreshSeed + 1} 批精选 · 城市通勤新装备 $displayIndex"
                    FeedCategory.ECOMMERCE -> "第 ${refreshSeed + 1} 批电商 · 春夏爆款好物 $displayIndex"
                    FeedCategory.LOCAL -> "第 ${refreshSeed + 1} 批本地 · 附近高分体验 $displayIndex"
                },
                description = "刷新批次 ${refreshSeed + 1}，分页 $page。这条广告用于验证下拉刷新、上拉加载和状态同步是否生效。",
                type = type,
                category = category,
                coverUrl = "${covers[index % covers.size]}?auto=format&fit=crop&w=1200&q=80",
                videoUrl = if (type == FeedItemType.VIDEO) videoUrls[index % videoUrls.size] else null,
                likesCount = 128 + refreshSeed * 11 + index * 17,
                commentsCount = 12 + refreshSeed + index * 3,
                isLiked = index % 5 == 0,
                isCollected = index % 7 == 0,
                aiSummary = if (index % 4 == 0) {
                    "AI 摘要：第 ${refreshSeed + 1} 批素材突出产品核心卖点、适用场景和即时优惠，适合在用户浏览决策早期做种草曝光。"
                } else {
                    "AI 摘要：第 ${refreshSeed + 1} 批素材风格清晰，标题利益点明确。"
                },
                aiTags = tagPool[index % tagPool.size]
            )
        }
    }
}
