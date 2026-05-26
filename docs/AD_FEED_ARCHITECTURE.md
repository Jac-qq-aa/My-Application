# 单列广告信息流 App 开发文档

## 1. 项目目标

本项目是一个基于 Kotlin、Jetpack Compose、Coroutines、Flow / StateFlow 的单列广告信息流骨架。

当前版本重点完成：

- 广告信息流数据模型
- 本地 Mock 数据源
- ViewModel 状态管理
- 顶部 Tab 频道切换
- LazyColumn 单列列表
- 多类型广告卡片工厂
- 点赞 / 收藏状态同步
- 下拉刷新
- AI 摘要与标签展示
- 有效曝光埋点骨架
- Media3 ExoPlayer 复用池接口预留

## 2. 目录结构

核心代码位于：

```text
app/src/main/java/com/example/myapplication/
```

目录说明：

```text
data/
  FeedItem.kt
  MockFeedDataSource.kt

viewmodel/
  FeedViewModel.kt

ui/
  feed/
    FeedScreen.kt
  detail/
    DetailScreen.kt
  components/
    AdCardFactory.kt
    VideoPlayerPool.kt

tracking/
  AdTracker.kt
  TrackingModels.kt
```

## 3. 架构分层

### data 层

`FeedItem.kt` 定义广告卡片数据结构。

设计原则：

- 使用 `data class`
- 字段使用 `val`
- 状态更新通过 `copy()` 完成
- 不在 UI 中散落字符串类型判断，而是使用 `FeedItemType` 枚举

这样做的好处是：状态不可变、变化可追踪、Compose 更容易做精准重组。

### viewmodel 层

`FeedViewModel.kt` 是当前页面的状态中心。

它维护：

```kotlin
StateFlow<List<FeedItem>>
```

核心职责：

- 异步加载 Mock 数据
- 维护当前 Tab
- 根据 Tab 过滤数据
- 处理点赞
- 处理收藏
- 暴露刷新状态

### ui 层

`FeedScreen.kt` 负责页面结构：

- 顶部标题栏
- 顶部频道 Tab
- 下拉刷新
- LazyColumn 信息流
- 曝光检测

`AdCardFactory.kt` 负责根据广告类型渲染不同卡片：

- `IMAGE_BIG`
- `IMAGE_SMALL`
- `VIDEO`

### tracking 层

`AdTracker.kt` 当前使用 `Log.d` 模拟埋点。

后续接入真实埋点 SDK 时，只需要替换这一层，不需要修改 UI。

## 4. 状态管理设计

### 单一事实源

当前列表数据统一由 `FeedViewModel` 持有。

```kotlin
private val allItems = MutableStateFlow<List<FeedItem>>(emptyList())
val feedItems: StateFlow<List<FeedItem>>
```

UI 只观察 `feedItems`，不自己复制业务状态。

### 为什么用 StateFlow

`StateFlow` 的特点：

- 永远有当前值
- 可以被 Compose 订阅
- 状态变化后自动通知 UI
- 适合表示页面状态

在 Compose 中：

```kotlin
val items by viewModel.feedItems.collectAsState()
```

当 ViewModel 更新 `feedItems` 后，Compose 会自动刷新依赖这份状态的 UI。

## 5. 点赞 / 收藏同步方案

### 问题

用户在详情页点赞或收藏后，列表页对应卡片如何同步更新，而且不重绘整个列表？

### 当前方案

点赞逻辑：

```kotlin
allItems.value = allItems.value.map { item ->
    if (item.id == id) {
        item.copy(isLiked = !item.isLiked)
    } else {
        item
    }
}
```

关键点：

- 不修改旧 item
- 只 copy 被操作的 item
- 未变化的 item 保持原样
- `LazyColumn` 使用 `key = item.id`

列表代码：

```kotlin
items(
    items = items,
    key = { item -> item.id },
    contentType = { item -> item.type }
)
```

这样 Compose 可以复用未变化的 item slot，只重组状态变化的那张卡片。

### 详情页扩展

未来接入详情页时，不要在详情页单独保存一份点赞状态。

详情页应该：

- 根据 `itemId` 从同一个 ViewModel 或 Repository 中读取数据
- 点赞时调用同一个 `toggleLike(id)`
- 收藏时调用同一个 `toggleCollect(id)`

这样列表页和详情页天然同步。

## 6. 动态卡片和 AI 标签性能方案

### 问题

AI 摘要长度不固定，标签数量也不固定。如果卡片高度频繁变化，LazyColumn 滚动时可能出现抖动和掉帧。

### 当前方案

媒体区域固定比例：

```kotlin
Modifier.aspectRatio(16f / 9f)
```

摘要限制行数：

```kotlin
maxLines = 2
overflow = TextOverflow.Ellipsis
```

标签使用 `FlowRow`，并限制最大行数：

```kotlin
FlowRow(
    maxLines = 2
)
```

收益：

- 卡片高度更稳定
- 文本不会无限撑开
- LazyColumn 测量压力更小
- 滚动体验更稳定

## 7. 有效曝光埋点方案

### 口径

一条广告满足以下条件，记为一次有效曝光：

- 卡片可见面积超过 50%
- 连续停留超过 1 秒
- 同一次列表生命周期内只上报一次

### 实现位置

文件：

```text
ui/feed/FeedScreen.kt
```

核心逻辑：

- 通过 `LazyListState.layoutInfo.visibleItemsInfo` 获取当前可见 item
- 计算 item 可见高度 / item 总高度
- 大于等于 50% 时启动协程
- 1 秒后再次确认
- 仍然满足条件才调用 `AdTracker.trackExposure`

### 为什么用协程

曝光口径需要“等待 1 秒后再确认”。

协程适合这种异步等待：

```kotlin
delay(1_000)
```

它不会阻塞主线程，也不会卡住 UI。

## 8. 视频播放预留方案

当前 VIDEO 卡片先展示封面和播放图标，不直接播放。

原因：

- 单列视频流不能给每个视频都创建播放器
- ExoPlayer 数量过多会导致内存和解码资源压力
- 应该维护播放器复用池

已预留接口：

```text
ui/components/VideoPlayerPool.kt
```

后续可以扩展：

- 当前可见视频自动播放
- 离屏视频暂停
- 播放器租借 / 归还
- 静音自动播放
- 首帧预加载

## 9. 当前页面入口

`MainActivity.kt` 已接入：

```kotlin
val feedViewModel: FeedViewModel = viewModel()
FeedScreen(viewModel = feedViewModel)
```

运行 app 后，首页就是广告信息流。

## 10. 后续建议

下一阶段可以继续补：

- Coil 图片加载
- Navigation Compose 详情页跳转
- Repository 层
- Room 本地缓存
- Paging 3 分页加载
- Media3 真正视频播放
- 曝光埋点批量上报
- ViewModel 单元测试
- Compose UI 自动化测试

