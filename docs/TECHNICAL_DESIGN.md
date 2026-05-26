# 单列广告信息流 App 技术方案

## 1. 课题理解

本课题要求实现一个内容与广告混排场景下的单列广告信息流 App。核心目标不是只把列表画出来，而是完整覆盖信息流产品中的几个关键能力：

- 信息流浏览体验：单列滚动、卡片多样式、刷新、加载更多、位置保持。
- 动态广告展示：大图、小图、视频等多种卡片形态，并保证滚动性能。
- 跨页面互动同步：列表页和详情页之间的点赞、收藏、分享状态一致。
- 内容理解能力：为广告生成 AI 摘要和智能标签，支持标签过滤和对话式搜索。
- 资源复用与稳定性：列表 cell 复用、播放器复用、缓存池设计。
- 埋点统计：模拟曝光、点击、互动事件，并能可视化展示统计结果。

当前项目已经完成核心骨架：Compose 单列流、三类卡片、Tab 切换、下拉刷新、点赞收藏、AI 摘要/标签展示、曝光埋点骨架、Media3 播放器池接口预留。

## 2. 参考项目分析

参考源码已放在：

```text
external/
  Hilt-MVVM-Compose-Movie/
  BaseApp-Jetpack-Compose-Android-Kotlin/
  compose-impression-tracker/
  compose-stability-analyzer/
  androidx-media/
```

### 2.1 Hilt-MVVM-Compose-Movie

参考点：

- Compose + ViewModel + Flow 的 MVVM 状态管理。
- Repository 层隔离数据来源。
- 列表、详情、搜索、收藏等页面之间的状态协作。

本项目借鉴：

- 使用 `FeedViewModel` 管理信息流状态。
- 使用 `StateFlow<List<FeedItem>>` 驱动 UI。
- 点赞、收藏通过同一个状态源更新，避免列表页和详情页各存一份状态。

### 2.2 BaseApp-Jetpack-Compose-Android-Kotlin

参考点：

- Compose 项目的基础工程分层。
- 可扩展到 Hilt、Repository、Room、Paging、Navigation 的架构方式。

本项目借鉴：

- 当前先保留轻量分层：`data` / `viewmodel` / `ui` / `tracking`。
- 后续功能增多后，再演进出 `repository` / `domain` / `network` / `database` / `di`。

### 2.3 compose-impression-tracker

参考点：

- Compose 组件曝光追踪。
- 支持曝光时长、可见比例、生命周期感知等参数。

本项目借鉴：

- 使用 `LazyListState.layoutInfo.visibleItemsInfo` 获取可见 item。
- 可见比例超过 50% 后，等待 1 秒再次确认。
- 同一条广告在一次列表生命周期内只上报一次有效曝光。

### 2.4 compose-stability-analyzer

参考点：

- 分析 Compose 参数稳定性。
- 辅助定位不必要重组和性能问题。

本项目借鉴：

- `FeedItem` 使用不可变 `data class`。
- `LazyColumn.items` 使用稳定 `key = item.id`。
- 使用 `contentType = item.type` 帮助列表复用相同类型 item。
- 限制动态文本和标签的高度，减少滚动时的测量抖动。

### 2.5 androidx/media

参考点：

- Media3 ExoPlayer 官方实现。
- 播放器生命周期、资源释放、播放状态管理。

本项目借鉴：

- 预留 `VideoPlayerPool`。
- 避免每个视频卡片创建一个播放器。
- 后续按可见视频租借 / 归还播放器。

## 3. 整体架构方案

### 3.1 当前分层

```text
app/src/main/java/com/example/myapplication/
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

### 3.2 后续目标分层

随着功能增加，建议演进为：

```text
data/
  model/
  repository/
  source/
    local/
    remote/
  cache/

domain/
  usecase/

ui/
  feed/
  detail/
  search/
  statistics/
  components/

viewmodel/

tracking/

player/

di/
```

演进原则：

- 当前阶段不为了架构而架构，优先保证核心功能跑通。
- 当网络、缓存、AI、分页、搜索都加入后，再拆出 Repository 和 UseCase。
- UI 不直接依赖网络或数据库，只依赖 ViewModel 暴露的状态。

## 4. 核心功能方案

### 4.1 单列信息流

最终方案：

- 使用 `LazyColumn` 实现单列流。
- 使用 `items(key = item.id, contentType = item.type)`。
- 卡片内部使用固定媒体比例和有限文本行数。

原因：

- `LazyColumn` 只 compose 当前可见和预取范围内的 item，适合长列表。
- `key` 保证点赞、收藏、刷新后 item 身份稳定。
- `contentType` 能帮助列表区分大图、小图、视频卡片，提高复用效率。

当前实现：

```text
ui/feed/FeedScreen.kt
```

### 4.2 多样式广告卡片

卡片类型：

- `IMAGE_BIG`
- `IMAGE_SMALL`
- `VIDEO`

最终方案：

- 使用 `AdCardFactory` 根据 `FeedItemType` 分发到不同卡片。
- 卡片共享标题、描述、AI 摘要、标签、互动栏。
- 视频卡片先展示封面和播放按钮，后续接入 Media3。

方案对比：

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 在 FeedScreen 中直接 `when(type)` | 写起来快 | 列表页会变臃肿，后续卡片增多难维护 | 不采用 |
| 使用 `AdCardFactory` 收敛卡片分发 | UI 分层清楚，扩展新卡片容易 | 多一个组件文件 | 当前采用 |
| 服务端动态 DSL 渲染 | 灵活度最高 | 复杂度过高，不适合训练营初期 | 后续可选 |

### 4.3 Tab 切换与刷新

最终方案：

- `FeedViewModel` 持有全量数据 `allItems`。
- 持有当前频道 `selectedCategory`。
- 使用 `combine(allItems, selectedCategory)` 得到当前展示列表。

原因：

- 切 Tab 不需要 UI 自己过滤。
- 点赞、收藏后当前 Tab 自动刷新。
- 后续可以把本地过滤替换成网络分页请求。

当前实现：

```text
viewmodel/FeedViewModel.kt
```

### 4.4 下拉刷新与上拉加载更多

当前已实现：

- 下拉刷新。
- 上拉加载更多。
- 模拟 1 秒网络加载。
- 基于 `page`、`hasMore`、`isLoadingMore` 的手写分页状态。

待实现：

- 更完整的错误态和重试入口。
- 后续可替换为服务端 cursor 或 Paging 3。

推荐方案：

- 初期手写分页状态：`page`、`hasMore`、`isLoadingMore`。
- 后期接入 Paging 3。

方案对比：

| 方案 | 优点 | 缺点 | 适用阶段 |
| --- | --- | --- | --- |
| 手写分页 | 简单，容易讲清楚 | 边界状态需要自己维护 | 当前训练营阶段 |
| Paging 3 | 生产级，加载状态完善 | 学习成本更高，接入更重 | 后续增强 |

### 4.5 详情页与返回位置保持

目标：

- 点击卡片进入详情页。
- 在详情页点赞、收藏后，返回列表页对应卡片状态同步。
- 返回后列表位置不变。

最终方案：

- 使用 Navigation Compose。
- `FeedScreen` 的 `LazyListState` 保持在页面级别。
- 列表和详情共享同一个 `FeedViewModel`，或共享 Repository 中的 `StateFlow`。
- 详情页通过 `itemId` 获取当前广告数据。

当前状态：

- 已接入 Navigation Compose。
- 点击卡片可以进入 `DetailScreen`。
- 详情页点赞 / 收藏调用同一个 `FeedViewModel`。
- 列表页使用可保存的 `LazyListState`，返回后保持滚动位置。

## 5. 数据与状态同步方案

### 5.1 数据获取方式

当前方案：

- 本地 `MockFeedDataSource`。
- `delay(1_000)` 模拟网络耗时。
- 一次生成 20 条广告。

后续方案：

- `FeedRepository` 统一数据入口。
- 支持 Mock / 网络切换。
- AI 摘要和标签可来自服务端，也可本地缓存。

### 5.2 状态生命周期

当前方案：

- 页面状态由 `FeedViewModel` 持有。
- `viewModelScope` 管理协程生命周期。
- `StateFlow` 暴露给 Compose。

核心链路：

```text
用户点击点赞
-> FeedScreen 调用 viewModel.toggleLike(id)
-> ViewModel copy 对应 FeedItem
-> StateFlow 发出新 List
-> Compose 只重组对应 key 的卡片
```

### 5.3 跨页面同步

方案对比：

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 详情页返回时把结果传回列表 | 实现直观 | 多页面、多入口时容易漏同步 | 不推荐 |
| 列表页和详情页共享 ViewModel | 简单，适合单 Activity Compose | 作用域要管理好 | 当前推荐 |
| Repository 维护全局 StateFlow | 更生产化，适合多页面和缓存 | 架构复杂度更高 | 后续演进 |

最终方案：

- 当前训练营阶段使用共享 ViewModel。
- 当接入网络、Room、Paging 后，升级为 Repository 单一事实源。

## 6. 动态卡片与 Compose 性能方案

### 6.1 难点

AI 摘要长度不固定、标签数量不固定，会带来：

- 卡片高度变化大。
- LazyColumn 反复测量。
- 滚动中出现跳动或掉帧。
- 点赞 / 收藏时误触发过多重组。

### 6.2 最终方案

- 媒体区域固定比例：`aspectRatio(16f / 9f)` 或 `aspectRatio(4f / 3f)`。
- 标题、描述、AI 摘要限制 `maxLines`。
- 标签使用 `FlowRow(maxLines = 2)`。
- 数据类保持不可变。
- LazyColumn 使用稳定 key。
- 卡片类型使用 contentType。

### 6.3 效果评估

评估方式：

- 使用 Android Studio Layout Inspector 观察重组。
- 使用 Compose Stability Analyzer 检查参数稳定性。
- 快速连续点赞，观察是否只有目标卡片变化。
- 快速滚动列表，观察是否出现明显高度跳动。

## 7. 资源复用与缓存池方案

### 7.1 Cell 复用

Compose 中没有传统 RecyclerView 的 ViewHolder，但 LazyColumn 会复用 item slot。

本项目策略：

- `key = item.id`
- `contentType = item.type`
- 卡片内部状态尽量上移到 ViewModel

### 7.2 播放器复用

难点：

- 视频流中如果每个卡片都创建 ExoPlayer，会造成内存和解码器资源浪费。
- 列表快速滚动时，播放器创建和释放会产生明显卡顿。

最终方案：

- 使用 `VideoPlayerPool` 管理播放器。
- 可见视频卡片租借播放器。
- 离屏暂停并归还播放器。
- 页面销毁时 `releaseAll()`。

当前状态：

- 已预留 `VideoPlayerPool` 和 `SimpleVideoPlayerPool`。
- 视频卡片暂时展示播放图标占位。

### 7.3 图片/AI 缓存

当前方案：

- 已接入 Coil Compose。
- 列表页和详情页都使用 `coverUrl` 加载远程图片。
- 显式开启内存缓存、磁盘缓存和网络缓存。
- 图片加载中展示渐变占位。
- 图片加载失败展示错误占位。
- Mock 数据暂时使用 Unsplash 远程图片 URL，适合训练营演示，不将图片资源打包进 APK。

后续建议：

- AI 摘要和标签以 `adId` 为 key 缓存。
- 缓存字段包括 `summary`、`tags`、`modelVersion`、`updatedAt`。
- 模型版本变化时可以主动失效旧缓存。

## 8. 曝光与点击统计方案

### 8.1 曝光口径

有效曝光定义：

- 广告卡片可见比例 >= 50%。
- 连续停留时间 >= 1 秒。
- 同一次列表生命周期内同一广告只上报一次。

当前实现：

```text
ui/feed/FeedScreen.kt -> TrackEffectiveExposure
tracking/AdTracker.kt
tracking/TrackingModels.kt
```

### 8.2 点击口径

当前支持模拟点击事件：

- 点击卡片。
- 点赞。
- 收藏。
- 点击标签。
- 分享。
- 详情页互动。

待补齐：

- 更细的统计详情页。
- 图表化展示曝光、点击、互动趋势。

### 8.3 方案对比

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 点击即上报 Log | 简单直观 | 无法做失败重试和批量上报 | 当前可用 |
| 本地事件队列批量上报 | 更接近生产 | 实现复杂 | 后续增强 |
| 接真实埋点 SDK | 最生产化 | 训练营环境依赖外部服务 | 可选 |

## 9. AI 摘要、标签与搜索方案

### 9.1 AI 输出要求

AI 不能自由输出散文本，必须结构化。

推荐 JSON 格式：

```json
{
  "summary": "一句 20-60 字的广告摘要",
  "tags": ["品类", "风格", "受众", "场景"],
  "keywords": ["搜索关键词1", "搜索关键词2"]
}
```

约束：

- `summary` 最多 60 字。
- `tags` 最多 6 个。
- 每个 tag 最多 6 个中文字符。
- 输出失败时使用本地规则兜底。

### 9.2 方案对比

| 方案 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- |
| 本地 Mock AI 字段 | 稳定、无网络依赖 | 不是真实 AI | 当前已实现 |
| 调 Qwen 免费接口 | 真实大模型能力 | 需要 API、网络、错误处理 | 推荐下一步 |
| 本地简单模型服务 | 可展示 C/S 架构 | 搭建成本较高 | 可选加分 |

### 9.3 对话式搜索

目标：

用户输入自然语言，例如：

```text
我想看适合学生党的性价比运动装备
```

系统返回匹配广告。

推荐初版方案：

- 用户输入 query。
- 从广告的 `title`、`description`、`aiSummary`、`aiTags` 中做关键词匹配。
- 匹配结果展示为信息流列表。

增强方案：

- 调大模型把自然语言 query 解析成结构化意图。
- 例如：`品类=运动`、`人群=学生党`、`偏好=性价比`。
- 再用结构化条件过滤广告。

## 10. 动画方案

当前状态：

- 已实现页面切换动画：信息流进入详情页时右侧滑入，返回时滑出。
- 已实现点赞彩蛋：点赞时出现上飘爱心，并保留爱心缩放反馈。
- 已实现收藏图标缩放动效。
- 已实现分享按钮轻旋转反馈。
- 已实现视频卡片播放按钮呼吸动效。
- 已实现标签筛选条展开 / 收起动画。
- 已实现统计面板数字变化动画。

推荐实现：

- 页面切换：Navigation Compose 默认转场或自定义 fade/slide。
- 点赞动效：点击爱心时使用 `animateFloatAsState` 做缩放。
- 收藏动效：图标颜色和大小轻微变化。
- 列表 item：加载时使用 placeholder 或淡入。

控制原则：

- 动画只增强反馈，不影响滚动性能。
- 信息流中避免大量复杂动画同时运行。

## 11. 当前实现程度

### 已完成

- Compose 单列信息流。
- 三种广告卡片：大图、小图、视频占位。
- 顶部 Tab：精选 / 电商 / 本地。
- 模拟网络加载。
- 下拉刷新。
- 上拉加载更多。
- 点击卡片进入详情页。
- 返回列表保持位置。
- 点赞 / 收藏。
- 分享交互。
- 详情页点赞 / 收藏与列表同步。
- 页面切换动画。
- 点赞浮动爱心彩蛋。
- 收藏 / 分享按钮动效。
- 视频播放按钮呼吸动效。
- 标签筛选条动画。
- 统计数字动画。
- Coil 图片加载和缓存。
- 图片 loading / error 占位。
- AI 摘要和标签展示。
- 标签点击过滤。
- 标签点击埋点。
- 曝光统计骨架。
- 点击统计骨架。
- 本地统计面板展示曝光、点击、点赞、收藏、分享数据。
- Media3 ExoPlayer 复用池接口。
- README 项目说明和 AI 声明。
- 开发文档和参考项目文档。

### 未完成但必须补齐

- 加载中 / 空态 / 错误态需要继续打磨。
- 演示视频。

### 可选加分

- Qwen 摘要和标签生成。
- 标签点击过滤。
- 对话式搜索。
- Media3 真视频播放。
- Compose 性能数据对比。
- 测试用例。

## 12. 周会推进计划

### 第 1 阶段：核心闭环

目标：保证 App 可演示，核心功能无明显 Bug。

- 完成详情页跳转。
- 完成详情页点赞 / 收藏同步。
- 完成返回列表位置保持。
- 完成上拉加载更多。
- 补充加载中 / 空态 / 错误态。

### 第 2 阶段：统计和 AI

目标：满足题目亮点要求。

- 增加统计面板。
- 展示曝光数、点击数、点赞数、收藏数。
- 实现标签点击过滤。
- 增加对话式搜索页面。
- 若无法接真实大模型，使用本地 Mock AI 服务作为降级。

### 第 3 阶段：性能和答辩材料

目标：提高技术方案设计和答辩表现。

- 用 Coil 加载效果做首屏体验和缓存命中验证。
- 视频卡片接入 Media3 基础播放。
- 使用 Compose Stability Analyzer 或 Layout Inspector 验证重组情况。
- 完善 README。
- 录制 3-8 分钟演示视频。
- 补充学习总结。

## 13. AI 声明草案

本项目开发过程中使用了 AI 辅助编程与文档生成。AI 主要用于：

- 生成基础工程骨架。
- 辅助编写 Compose UI、ViewModel、StateFlow 状态管理代码。
- 辅助整理技术方案文档。
- 辅助分析开源参考项目。

团队对 AI 输出进行了以下验证：

- 检查代码是否能在 Android Studio 中编译运行。
- 检查 ViewModel 状态更新是否符合不可变数据原则。
- 检查 LazyColumn 是否使用稳定 key。
- 检查曝光埋点口径是否符合题目要求。
- 对关键逻辑补充中文注释，确保团队成员理解实现原因。

团队做出的优化：

- 将 AI 生成的代码按 `data` / `viewmodel` / `ui` / `tracking` 分层整理。
- 对 AI 摘要和标签设计结构化输出约束。
- 为视频播放预留播放器复用池，而不是直接在每个卡片创建播放器。
- 将曝光统计抽象为可替换的 `AdTracker`，便于后续接入真实埋点。

## 14. 答辩表达重点

可以重点讲 4 个点：

1. 状态同步：为什么用 ViewModel + StateFlow，为什么 copy 不可变数据，为什么能让列表局部刷新。
2. 列表性能：LazyColumn key、contentType、固定媒体比例、AI 文本和标签限高。
3. 曝光口径：50% 可见面积 + 1 秒停留 + 去重，如何用 LazyListState 实现。
4. 可扩展性：当前轻量 MVVM，后续可演进 Repository、Paging、Room、Qwen、Media3。
