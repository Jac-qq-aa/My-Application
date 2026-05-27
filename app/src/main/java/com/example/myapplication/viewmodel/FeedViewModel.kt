package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.FeedCategory
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.MockFeedDataSource
import com.example.myapplication.data.ai.HybridAiInsightGenerator
import com.example.myapplication.data.local.FeedInteractionStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * 信息流页面的业务逻辑大管家。
 *
 * ViewModel 的职责：
 * 1. 持有页面状态，避免 Composable 自己管理复杂业务状态；
 * 2. 通过 viewModelScope 启动协程，自动跟随 ViewModel 生命周期取消任务；
 * 3. 通过 StateFlow 向 UI 暴露“可观察、可订阅、始终有当前值”的响应式数据。
 */
class FeedViewModel(application: Application) : AndroidViewModel(application) {
    private companion object {
        const val PageSize = 20
    }

    private val interactionStore = FeedInteractionStore(application)
    private val aiInsightGenerator = HybridAiInsightGenerator()
    private val allItems = MutableStateFlow<List<FeedItem>>(emptyList())
    private val selectedCategory = MutableStateFlow(FeedCategory.FEATURED)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val isLoadingMore = MutableStateFlow(false)
    private val hasMoreItems = MutableStateFlow(true)
    private val currentPage = MutableStateFlow(1)
    private var refreshSeed = 0
    private var aiInsightJob: Job? = null

    /**
     * 当前 Tab 下真正给列表展示的数据。
     *
     * combine 会监听 allItems 和 selectedCategory 任意一个变化：
     * - 点赞/收藏改变 allItems，会自动重新计算当前列表；
     * - 切 Tab 改变 selectedCategory，也会自动得到过滤后的列表。
     *
     * StateFlow 的价值在于：UI collect 之后，只要这里 emit 新值，Compose 就会自动重组相关区域。
     */
    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val allFeedItems: StateFlow<List<FeedItem>> = allItems.asStateFlow()
    val feedItems: StateFlow<List<FeedItem>> = _feedItems.asStateFlow()

    val currentCategory: StateFlow<FeedCategory> = selectedCategory.asStateFlow()
    val currentTag: StateFlow<String?> = selectedTag.asStateFlow()
    val refreshing: StateFlow<Boolean> = isRefreshing.asStateFlow()
    val loadingMore: StateFlow<Boolean> = isLoadingMore.asStateFlow()
    val hasMore: StateFlow<Boolean> = hasMoreItems.asStateFlow()

    init {
        observeFilteredItems()
        refresh()
    }

    private fun observeFilteredItems() {
        viewModelScope.launch {
            combine(allItems, selectedCategory, selectedTag) { items, category, tag ->
                items
                    .filter { it.category == category }
                    .filter { item -> tag == null || item.aiTags.contains(tag) }
            }.collect { filteredItems ->
                _feedItems.value = filteredItems
            }
        }
    }

    /**
     * 刷新数据。
     *
     * viewModelScope.launch 会在主线程启动协程，但 delay / 网络请求不会卡住主线程。
     * 真实项目中可以把 MockFeedDataSource 替换成 repository.loadFeed()。
     */
    fun refresh() {
        viewModelScope.launch {
            isRefreshing.value = true
            refreshSeed += 1
            currentPage.value = 1
            hasMoreItems.value = true
            val loadedItems = restorePersistedInteractions(
                MockFeedDataSource.loadFeedItems(
                    page = 1,
                    pageSize = PageSize,
                    refreshSeed = refreshSeed
                )
            )
            allItems.value = loadedItems
            generateAiInsights(loadedItems, cancelRunning = true)
            isRefreshing.value = false
        }
    }

    /**
     * 上拉加载更多。
     *
     * 这里用最容易理解的手写分页：page + pageSize。
     * 生产项目中广告流通常还会使用 cursor、server token 或 Paging 3。
     */
    fun loadMore() {
        if (isRefreshing.value || isLoadingMore.value || !hasMoreItems.value) return

        viewModelScope.launch {
            isLoadingMore.value = true
            val nextPage = currentPage.value + 1
            val nextItems = restorePersistedInteractions(
                MockFeedDataSource.loadFeedItems(
                    page = nextPage,
                    pageSize = PageSize,
                    refreshSeed = refreshSeed,
                    networkDelayMillis = 700
                )
            )
            allItems.value = allItems.value + nextItems
            generateAiInsights(nextItems, cancelRunning = false)
            currentPage.value = nextPage
            hasMoreItems.value = nextItems.isNotEmpty() && nextPage < 5
            isLoadingMore.value = false
        }
    }

    fun selectCategory(category: FeedCategory) {
        selectedCategory.value = category
        selectedTag.value = null
    }

    fun selectTag(tag: String) {
        selectedTag.value = tag
    }

    fun clearTag() {
        selectedTag.value = null
    }

    /**
     * 将 Java 持久化层里的点赞/收藏状态恢复到 Mock 数据上。
     *
     * Mock 数据每次加载都会创建新的 FeedItem 对象，如果不做这一步，
     * App 重启或刷新后 UI 就会丢失之前保存的互动状态。
     */
    private fun restorePersistedInteractions(items: List<FeedItem>): List<FeedItem> {
        return items.map { item ->
            val restoredLiked = if (interactionStore.hasLikeOverride(item.id)) {
                interactionStore.isLiked(item.id)
            } else {
                item.isLiked
            }
            val restoredCollected = if (interactionStore.hasCollectOverride(item.id)) {
                interactionStore.isCollected(item.id)
            } else {
                item.isCollected
            }
            item.copy(
                isLiked = restoredLiked,
                isCollected = restoredCollected,
                likesCount = when {
                    restoredLiked && !item.isLiked -> item.likesCount + 1
                    !restoredLiked && item.isLiked -> (item.likesCount - 1).coerceAtLeast(0)
                    else -> item.likesCount
                }
            )
        }
    }

    /**
     * 异步生成 AI 摘要和标签。
     *
     * 这里不阻塞首屏：列表先展示 Mock 内容，AI 结果回来后按 id 局部更新对应 item。
     * HybridAiInsightGenerator 会优先调用本地 Qwen；如果本地服务不可用，会自动用规则降级。
     */
    private fun generateAiInsights(items: List<FeedItem>, cancelRunning: Boolean) {
        if (cancelRunning) {
            aiInsightJob?.cancel()
        }
        aiInsightJob = viewModelScope.launch {
            // 本地小模型推理速度有限。这里串行生成，避免一次性 20 个请求把 Ollama 打满，
            // 同时每生成完一条就按 id 更新状态，UI 可以逐条看到 Qwen 摘要替换 Mock 摘要。
            items.forEach { item ->
                val insight = aiInsightGenerator.generate(item)
                allItems.value = allItems.value.map { current ->
                    if (current.id == item.id) {
                        current.copy(
                            aiSummary = insight.summary,
                            aiTags = insight.tags
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * 点赞状态切换。
     *
     * 关键点：不要原地修改 List 或 FeedItem。
     * 我们使用 map + copy 创建新列表和新 item，StateFlow 才能发出新引用，
     * Compose 也才能精确感知“id 对应的那一项变了”。
     */
    fun toggleLike(id: String) {
        allItems.value = allItems.value.map { item ->
            if (item.id == id) {
                val nextLiked = !item.isLiked
                interactionStore.setLiked(id, nextLiked)
                item.copy(
                    isLiked = nextLiked,
                    likesCount = if (nextLiked) item.likesCount + 1 else (item.likesCount - 1).coerceAtLeast(0)
                )
            } else {
                item
            }
        }
    }

    /**
     * 收藏状态切换。
     *
     * 列表页、详情页未来只要观察同一个 StateFlow 或同一个 Repository 状态源，
     * 这里的修改就会同时通知所有页面，实现跨页面同步。
     */
    fun toggleCollect(id: String) {
        allItems.value = allItems.value.map { item ->
            if (item.id == id) {
                val nextCollected = !item.isCollected
                interactionStore.setCollected(id, nextCollected)
                item.copy(isCollected = nextCollected)
            } else {
                item
            }
        }
    }
}
