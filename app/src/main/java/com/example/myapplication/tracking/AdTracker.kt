package com.example.myapplication.tracking

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 本地埋点骨架。
 *
 * 生产环境通常会把事件写入批量队列，满足网络合并、失败重试、去重和采样。
 * 这里先用 Log 输出，保留清晰接口，后续替换为真实埋点 SDK 时 UI 不需要改。
 */
class AdTracker {
    private val exposedIds = mutableSetOf<String>()
    private val _stats = MutableStateFlow(TrackingStats())
    val stats: StateFlow<TrackingStats> = _stats.asStateFlow()

    fun trackExposure(event: ExposureEvent) {
        // 单条广告在一次列表生命周期内只报一次有效曝光，避免反复上下滑造成重复计数。
        if (exposedIds.add(event.itemId)) {
            Log.d("AdTracker", "曝光: $event")
            _stats.update { it.copy(exposureCount = it.exposureCount + 1) }
        }
    }

    fun trackClick(event: ClickEvent) {
        Log.d("AdTracker", "点击: $event")
        _stats.update { stats ->
            when (event.action) {
                "like" -> stats.copy(clickCount = stats.clickCount + 1, likeCount = stats.likeCount + 1)
                "collect" -> stats.copy(clickCount = stats.clickCount + 1, collectCount = stats.collectCount + 1)
                "share" -> stats.copy(clickCount = stats.clickCount + 1, shareCount = stats.shareCount + 1)
                else -> stats.copy(clickCount = stats.clickCount + 1)
            }
        }
    }
}
