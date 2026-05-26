package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.FeedCategory
import com.example.myapplication.data.FeedComment
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.MockFeedDataSource
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
class FeedViewModel : ViewModel() {
    private companion object {
        const val PageSize = 20
    }

    private val allItems = MutableStateFlow<List<FeedItem>>(emptyList())
    private val selectedCategory = MutableStateFlow(FeedCategory.FEATURED)
    private val selectedTag = MutableStateFlow<String?>(null)
    private val isRefreshing = MutableStateFlow(false)
    private val isLoadingMore = MutableStateFlow(false)
    private val hasMoreItems = MutableStateFlow(true)
    private val currentPage = MutableStateFlow(1)
    private val screenState = MutableStateFlow<FeedScreenState>(FeedScreenState.Loading)
    private val commentsByItemId = MutableStateFlow<Map<String, List<FeedComment>>>(emptyMap())
    private var hasLoadedOnce = false

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
     * viewModelScope.launch 会在主线程启动协程，但 delay / 网络请求不会卡住主线程。
     * 真实项目中可以把 MockFeedDataSource 替换成 repository.loadFeed()。
     */
    fun refresh() {
        viewModelScope.launch {
            val showFullLoading = !hasLoadedOnce || screenState.value is FeedScreenState.Error
            if (showFullLoading) {
                screenState.value = FeedScreenState.Loading
            }
            isRefreshing.value = true
            try {
                currentPage.value = 1
                hasMoreItems.value = true
                val loadedItems = MockFeedDataSource.loadFeedItems(page = 1, pageSize = PageSize)
                allItems.value = loadedItems
                hasLoadedOnce = true
                screenState.value = loadedItems
                    .filter { it.category == selectedCategory.value }
                    .filter { item -> selectedTag.value == null || item.aiTags.contains(selectedTag.value) }
                    .toScreenState()
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
                val nextItems = MockFeedDataSource.loadFeedItems(
                    page = nextPage,
                    pageSize = PageSize,
                    networkDelayMillis = 700
                )
                allItems.value = allItems.value + nextItems
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
                item.copy(isCollected = !item.isCollected)
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
