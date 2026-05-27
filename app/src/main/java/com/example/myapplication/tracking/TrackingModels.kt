package com.example.myapplication.tracking

/**
 * 曝光事件。
 *
 * visibleRatio 是卡片在可见区域中的比例。单列流常见口径是：
 * “可见面积超过 50%，并且连续停留超过 1 秒，算一次有效曝光”。
 */
data class ExposureEvent(
    val itemId: String,
    val visibleRatio: Float,
    val stayMillis: Long,
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ClickEvent(
    val itemId: String,
    val action: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * 本地统计面板展示用的聚合数据。
 *
 * 真实生产环境不会只在内存里统计，而是会上报到服务端。
 * 训练营阶段先把关键指标可视化出来，便于演示曝光、点击、互动口径。
 */
data class TrackingStats(
    val exposureCount: Int = 0,
    val clickCount: Int = 0,
    val likeCount: Int = 0,
    val collectCount: Int = 0,
    val shareCount: Int = 0
) {
    val interactionCount: Int
        get() = likeCount + collectCount + shareCount

    val clickThroughRate: Float
        get() = if (exposureCount == 0) 0f else clickCount.toFloat() / exposureCount.toFloat()

    val interactionRate: Float
        get() = if (exposureCount == 0) 0f else interactionCount.toFloat() / exposureCount.toFloat()

    val totalEventCount: Int
        get() = exposureCount + clickCount
}

data class StatMetric(
    val label: String,
    val value: Int,
    val description: String
)

fun TrackingStats.toStatMetrics(): List<StatMetric> {
    return listOf(
        StatMetric("曝光", exposureCount, "有效曝光次数"),
        StatMetric("点击", clickCount, "卡片和互动点击"),
        StatMetric("点赞", likeCount, "点赞操作"),
        StatMetric("收藏", collectCount, "收藏操作"),
        StatMetric("分享", shareCount, "系统分享调用"),
        StatMetric("互动", interactionCount, "点赞 + 收藏 + 分享")
    )
}
