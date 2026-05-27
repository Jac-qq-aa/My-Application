package com.example.myapplication.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.FeedCategory
import com.example.myapplication.data.FeedComment
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
    private val screenState = MutableStateFlow<FeedScreenState>(FeedScreenState.Loading)
    private val commentsByItemId = MutableStateFlow<Map<String, List<FeedComment>>>(emptyMap())
    private var refreshSeed = 0
    private var hasLoadedOnce = false
    private var aiInsightJob: Job? = null

    private val _feedItems = MutableStateFlow<List<FeedItem>>(emptyList())
    val allFeedItems: StateFlow<List<FeedItem>> = allItems.asStateFlow()
    val feedItems: StateFlow<List<FeedItem>> = _feedItems.asStateFlow()

    val currentCategory: StateFlow<FeedCategory> = selectedCategory.asStateFlow()
    val currentTag: StateFlow<String?> = selectedTag.asStateFlow()
    val refreshing: StateFlow<Boolean> = isRefreshing.asStateFlow()
    val loadingMore: StateFlow<Boolean> = isLoadingMore.asStateFlow()
    val hasMore: StateFlow<Boolean> = hasMoreItems.asStateFlow()
    val currentScreenState: StateFlow<FeedScreenState> = screenState.asStateFlow()
    val comments: StateFlow<Map<String, List<FeedComment>>> = commentsByItemId.asStateFlow()

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
                if (hasLoadedOnce && screenState.value !is FeedScreenState.Error && screenState.value !is FeedScreenState.Loading) {
                    screenState.value = filteredItems.toScreenState()
                }
            }
        }
    }

    /**
     * 刷新数据。
     *
     * 首屏失败时进入 Error；已有内容时刷新失败不清空旧列表，避免演示时页面闪成空白。
     */
    fun refresh() {
        viewModelScope.launch {
            val showFullLoading = !hasLoadedOnce || screenState.value is FeedScreenState.Error
            if (showFullLoading) {
                screenState.value = FeedScreenState.Loading
            }
            isRefreshing.value = true
            try {
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
                hasLoadedOnce = true
                screenState.value = loadedItems
                    .filter { it.category == selectedCategory.value }
                    .filter { item -> selectedTag.value == null || item.aiTags.contains(selectedTag.value) }
                    .toScreenState()
                generateAiInsights(loadedItems, cancelRunning = true)
            } catch (exception: Exception) {
                if (allItems.value.isEmpty()) {
                    screenState.value = FeedScreenState.Error(
                        message = exception.message ?: "广告加载失败，请稍后重试"
                    )
                }
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun retry() {
        refresh()
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
            try {
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
            } finally {
                isLoadingMore.value = false
            }
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

    private fun List<FeedItem>.toScreenState(): FeedScreenState {
        return if (isEmpty()) {
            FeedScreenState.Empty(isTagFiltered = selectedTag.value != null)
        } else {
            FeedScreenState.Content
        }
    }

    /**
     * 将 Java 持久化层里的点赞/收藏状态恢复到 Mock 数据上。
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
     */
    private fun generateAiInsights(items: List<FeedItem>, cancelRunning: Boolean) {
        if (cancelRunning) {
            aiInsightJob?.cancel()
        }
        aiInsightJob = viewModelScope.launch {
            // 本地小模型推理速度有限。串行生成可以避免一次性请求把 Ollama 打满。
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
     * 使用 map + copy 创建新列表和新 item，StateFlow 才能发出新引用，
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

    fun addComment(itemId: String, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) return

        val currentComments = commentsByItemId.value[itemId].orEmpty()
        val newComment = FeedComment(
            id = "${itemId}_comment_${currentComments.size + 1}_${System.currentTimeMillis()}",
            itemId = itemId,
            author = "我",
            content = trimmedContent,
            timestampLabel = "刚刚"
        )
        commentsByItemId.value = commentsByItemId.value + (itemId to (listOf(newComment) + currentComments))
        allItems.value = allItems.value.map { item ->
            if (item.id == itemId) {
                item.copy(commentsCount = item.commentsCount + 1)
            } else {
                item
            }
        }
    }
}
