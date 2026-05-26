package com.example.myapplication.data

/**
 * 广告卡片的展示类型。
 *
 * 在生产项目里，服务端通常会下发一个 cardType / creativeType 字段。
 * 客户端不要用字符串散落在 UI 里判断，而是收敛成 enum：
 * 1. 可读性更好；
 * 2. when 分支能得到编译期检查；
 * 3. 后续新增直播、轮播等类型时，改动范围更可控。
 */
enum class FeedItemType {
    IMAGE_BIG,
    IMAGE_SMALL,
    VIDEO
}

/**
 * 顶部频道 Tab。
 *
 * category 不是直接展示给用户的字符串，而是业务语义。
 * UI 层负责把它渲染成中文文案，避免数据层依赖 Android string resource。
 */
enum class FeedCategory(val title: String) {
    FEATURED("精选"),
    ECOMMERCE("电商"),
    LOCAL("本地")
}

/**
 * 单条广告信息流数据。
 *
 * 这里使用 data class，并且所有字段都是 val，原因是 Compose 和 StateFlow 都更偏好“不可变状态”：
 * - 点赞/收藏时，不直接修改旧对象，而是 copy 出一个新对象；
 * - StateFlow 发出新的 List 后，Compose 能准确知道哪一条数据变了；
 * - 配合 LazyColumn 的 key = item.id，可以让列表只重组变化的卡片，而不是粗暴刷新整屏。
 */
data class FeedItem(
    val id: String,
    val title: String,
    val description: String,
    val type: FeedItemType,
    val category: FeedCategory,
    val coverUrl: String,
    val videoUrl: String? = null,
    val likesCount: Int,
    val commentsCount: Int,
    val isLiked: Boolean,
    val isCollected: Boolean,
    val aiSummary: String,
    val aiTags: List<String>
)
