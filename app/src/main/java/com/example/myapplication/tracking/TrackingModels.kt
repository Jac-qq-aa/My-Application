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
)
