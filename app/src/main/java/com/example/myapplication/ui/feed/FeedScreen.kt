package com.example.myapplication.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.FeedCategory
import com.example.myapplication.tracking.AdTracker
import com.example.myapplication.tracking.ClickEvent
import com.example.myapplication.tracking.ExposureEvent
import com.example.myapplication.tracking.TrackingStats
import com.example.myapplication.ui.components.AdCardFactory
import com.example.myapplication.viewmodel.FeedViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    tracker: AdTracker,
    onNavigateToDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items by viewModel.feedItems.collectAsState()
    val currentCategory by viewModel.currentCategory.collectAsState()
    val currentTag by viewModel.currentTag.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val stats by tracker.stats.collectAsState()
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    val refreshState = rememberPullRefreshState(refreshing = refreshing, onRefresh = viewModel::refresh)

    TrackEffectiveExposure(
        listStateInfoProvider = { listState.layoutInfo.visibleItemsInfo },
        viewportStartProvider = { listState.layoutInfo.viewportStartOffset },
        viewportEndProvider = { listState.layoutInfo.viewportEndOffset },
        tracker = tracker
    )

    LaunchedEffect(listState, items.size, hasMore) {
        snapshotFlow {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalCount = listState.layoutInfo.totalItemsCount
            lastVisibleIndex to totalCount
        }
            .distinctUntilChanged()
            .collect { (lastVisibleIndex, totalCount) ->
                if (hasMore && totalCount > 0 && lastVisibleIndex >= totalCount - 4) {
                    viewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("广告信息流") })
                ScrollableTabRow(selectedTabIndex = FeedCategory.entries.indexOf(currentCategory)) {
                    FeedCategory.entries.forEach { category ->
                        Tab(
                            selected = currentCategory == category,
                            onClick = {
                                viewModel.selectCategory(category)
                            },
                            text = { Text(category.title) }
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pullRefresh(refreshState)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "refresh_banner", contentType = "refresh") {
                    RefreshStatusBanner(refreshing = refreshing)
                }

                item(key = "stats_panel", contentType = "stats") {
                    TrackingStatsPanel(stats = stats)
                }

                item(key = "tag_filter", contentType = "filter") {
                    AnimatedVisibility(
                        visible = currentTag != null,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        AssistChip(
                            onClick = viewModel::clearTag,
                            label = { Text("筛选标签：${currentTag.orEmpty()}，点击清除") }
                        )
                    }
                }

                /**
                 * key = item.id 是局部刷新的关键。
                 *
                 * 点赞某一条后，ViewModel 会发出新 List，但未变化 item 的 id 和对象语义都保持稳定。
                 * LazyColumn 能根据 key 复用原来的 item slot，Compose 只重组状态发生变化的卡片。
                 */
                items(
                    items = items,
                    key = { item -> item.id },
                    contentType = { item -> item.type }
                ) { item ->
                    AdCardFactory(
                        item = item,
                        onLikeClick = { id ->
                            viewModel.toggleLike(id)
                            tracker.trackClick(ClickEvent(id, "like"))
                        },
                        onCollectClick = { id ->
                            viewModel.toggleCollect(id)
                            tracker.trackClick(ClickEvent(id, "collect"))
                        },
                        onShareClick = { id ->
                            tracker.trackClick(ClickEvent(id, "share"))
                        },
                        onTagClick = { tag ->
                            viewModel.selectTag(tag)
                            tracker.trackClick(ClickEvent(item.id, "tag:$tag"))
                        },
                        onCardClick = { id ->
                            tracker.trackClick(ClickEvent(id, "card"))
                            onNavigateToDetail(id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item(key = "load_more", contentType = "load_more") {
                    LoadMoreFooter(
                        loadingMore = loadingMore,
                        hasMore = hasMore,
                        itemCount = items.size
                    )
                }
            }

            PullRefreshIndicator(
                refreshing = refreshing,
                state = refreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun RefreshStatusBanner(refreshing: Boolean) {
    AnimatedVisibility(
        visible = refreshing,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "refreshSpin")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 850)),
            label = "refreshRotation"
        )

        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "刷新中",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = rotation }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "正在刷新广告内容...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun TrackingStatsPanel(stats: TrackingStats) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatText(label = "曝光", value = stats.exposureCount)
            StatText(label = "点击", value = stats.clickCount)
            StatText(label = "点赞", value = stats.likeCount)
            StatText(label = "收藏", value = stats.collectCount)
            StatText(label = "分享", value = stats.shareCount)
        }
    }
}

@Composable
private fun StatText(label: String, value: Int) {
    val animatedValue by animateIntAsState(
        targetValue = value,
        label = "stat_$label"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = animatedValue.toString(), style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadMoreFooter(
    loadingMore: Boolean,
    hasMore: Boolean,
    itemCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            loadingMore -> {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "正在加载下一批广告...", style = MaterialTheme.typography.bodyMedium)
            }
            itemCount == 0 -> Text(text = "当前筛选下暂无广告", style = MaterialTheme.typography.bodySmall)
            !hasMore -> Text(text = "没有更多广告了，试试下拉刷新", style = MaterialTheme.typography.bodySmall)
            else -> Text(text = "继续上滑加载更多", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * 有效曝光计算。
 *
 * 核心口径：
 * 1. 通过 LazyListState.layoutInfo 拿到当前屏幕内可见 item；
 * 2. 计算 item 可见高度 / item 总高度，单列流中这基本等价于可见面积比例；
 * 3. 比例 >= 50% 后启动 1 秒确认任务；
 * 4. 1 秒后如果仍然 >= 50%，才上报曝光。
 */
@Composable
private fun TrackEffectiveExposure(
    listStateInfoProvider: () -> List<LazyListItemInfo>,
    viewportStartProvider: () -> Int,
    viewportEndProvider: () -> Int,
    tracker: AdTracker
) {
    val scope = rememberCoroutineScope()
    val pendingJobs = remember { mutableStateMapOf<String, Job>() }
    val reportedIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        snapshotFlow { listStateInfoProvider() }
            .collectLatest { visibleItems ->
                val ratios = visibleItems.associate { itemInfo ->
                    val id = itemInfo.key as? String ?: return@associate "" to 0f
                    id to itemInfo.visibleRatio(viewportStartProvider(), viewportEndProvider())
                }.filterKeys { it.isNotEmpty() }

                ratios.forEach { (id, ratio) ->
                    if (ratio >= 0.5f && reportedIds[id] != true && pendingJobs[id] == null) {
                        pendingJobs[id] = scope.launch {
                            delay(1_000)
                            val latestInfo = listStateInfoProvider().firstOrNull { it.key == id }
                            val latestRatio = latestInfo?.visibleRatio(viewportStartProvider(), viewportEndProvider()) ?: 0f
                            if (latestRatio >= 0.5f) {
                                reportedIds[id] = true
                                tracker.trackExposure(
                                    ExposureEvent(
                                        itemId = id,
                                        visibleRatio = latestRatio,
                                        stayMillis = 1_000
                                    )
                                )
                            }
                            pendingJobs.remove(id)
                        }
                    }
                    if (ratio < 0.5f) {
                        pendingJobs.remove(id)?.cancel()
                    }
                }
            }
    }
}

private fun LazyListItemInfo.visibleRatio(viewportStart: Int, viewportEnd: Int): Float {
    val itemStart = offset
    val itemEnd = offset + size
    val visibleStart = max(itemStart, viewportStart)
    val visibleEnd = min(itemEnd, viewportEnd)
    val visibleSize = (visibleEnd - visibleStart).coerceAtLeast(0)
    return visibleSize.toFloat() / size.toFloat()
}
