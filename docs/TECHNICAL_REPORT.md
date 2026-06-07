# 单列广告信息流 App 技术方案设计文档

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0 | 2026-06-07 | 陈毓灏 & 李念 | 初稿，覆盖核心模块方案设计 |

---

## 1. 文档概述

### 1.1 背景

项目目标在于实现一个基于 Jetpack Compose 的单列广告信息流 App。核心要求实现包括单列浏览、多样式卡片、频道切换、跨页面互动同步、AI 摘要与标签、对话式搜索、视频播放与缓存等必需功能。开发中面临的主要难点囊括不同类型广告卡片的动态渲染与高度稳定性、Compose 列表重组导致的性能问题、AI 摘要与标签输出的不可控性，以及曝光统计口径的精确定义，所以本文档重点围绕这些技术难点展开。描述功能实现的同时，复盘各关键技术模块的候选方案、取舍理由及效果评估，辅助构建一个流畅、扩展性强、AI 赋能的广告信息流 App，确保列表滚动顺畅、互动状态精准同步、AI 生成内容可控且首屏快速呈现。

### 1.2 技术栈

| 类别 | 选型 |
|------|------|
| 语言 | Kotlin |
| UI 框架 | Jetpack Compose + Material 3 |
| 状态管理 | ViewModel + StateFlow |
| 导航 | Navigation Compose |
| 图片加载 | Coil 3 |
| 视频播放 | Media3 ExoPlayer |
| AI 生成 | 阿里云 DashScope Qwen (qwen3.5-plus)，降级本地规则 |
| 持久化 | SQLite (AI 缓存 / 互动状态 / 评论 / 埋点事件) |
| 构建 | Gradle 9.2.1 + Kotlin 2.2.10 + AGP 9.2.1 |

### 1.3 适用范围

本文档聚焦以下四个技术重点，每个章节独立阐述问题分析、候选方案、最终选择及设计细节：

- 卡片动态化方案
- 列表性能策略
- AI 输出约束与缓存策略
- 曝光统计口径

---

## 2. 总体架构

### 2.1 分层设计

```
┌──────────────────────────────────────────────────────┐
│ ui/feed  ui/detail  ui/search  ui/stats  ui/components
├──────────────────────────────────────────────────────┤
│              viewmodel/FeedViewModel                 │
│         (单一事实源, 四页面共享同一实例)               │
├──────────────────────────────────────────────────────┤
│   data/repository  data/ai  data/video  data/local   │
├──────────────────────────────────────────────────────┤
│         tracking/AdTracker + TrackingStore           │
└──────────────────────────────────────────────────────┘
```

### 2.2 导航与路由设计

Navigation Compose 管理四个页面，全部共享同一个 `FeedViewModel` 实例（由 `MainActivity` 层创建并传入各 Composable）：

```
NavHost(startDestination = "feed")
  ├── "feed"          → FeedScreen  (信息流主页)
  ├── "detail/{itemId}" → DetailScreen (广告详情, 路径参数传递 itemId)
  ├── "search"        → SearchScreen (对话式搜索)
  └── "stats"         → StatsScreen  (统计详情)
```

关键设计决策：

1. **四页面共享 ViewModel**：`FeedViewModel` 在 `MainActivity` 层通过 `viewModel()` 创建，作为参数传入 `FeedScreen`、`SearchScreen`、`DetailScreen`。这保证列表页的点赞/收藏操作和详情页的互动操作写入同一个 `StateFlow`，任意页面的状态变更自动反映到所有页面。

2. **详情页通过路径参数定位**：`detail/{itemId}` 使用 Navigation Compose 的 `navArgument` 传递广告 ID，详情页从共享 ViewModel 的 `allFeedItems` 中按 ID 查找数据，而非独立加载。这避免了详情页和列表页各存一份数据副本。

3. **页面转场动画**：进入详情/搜索/统计页使用 `slideInHorizontally`（从右侧滑入），返回时 `slideOutHorizontally`（向右侧滑出），动效时长统一在 220-280ms 区间，保证操作反馈感。

### 2.3 核心设计原则

1. **UI 不持有业务状态**：所有状态由 `FeedViewModel` 通过 `StateFlow` 向下暴露
2. **数据不可变**：`FeedItem` 为 `data class`，全字段 `val`，状态变更仅通过 `copy()`
3. **精准重组**：`LazyColumn` 使用 `key = item.id` + `contentType = item.type`
4. **持久化隔离**：ViewModel 不直接操作 SQLite/SharedPreferences，统一通过 `FeedRepository` 接口

### 2.4 数据流

```
用户操作 → Composable 调用 ViewModel 方法
         → ViewModel 通过 FeedRepository 读写数据
         → ViewModel 更新 StateFlow (map + copy)
         → Compose 收到新 StateFlow 值
         → key 匹配的卡片重组，其余复用
```

---

## 3. 卡片动态化方案

### 3.1 难点分析

在开发信息流时，面临的主要问题不是单纯渲染三种广告卡片，而是未来扩展性和列表稳定性。初期列表只包含大图、小图和视频，但未来项目中也可能会加入商品橱窗、直播卡等新类型。如果直接在列表页面写 when(item.type) 分支，随着类型增加，列表页逻辑会越来越臃肿，互动栏和标签逻辑重复，改动容易遗漏；同时，不同内容长度会导致 LazyColumn 滚动时高度突变，影响用户体验。

因此难点可以总结为三个方面：

1. 扩展性：新增卡片类型不能影响现有列表逻辑
2. 复用性与一致性：标题、摘要、标签、互动栏需要统一管理
3. 高度稳定性：动态内容不应导致列表跳动或重组过频

### 3.2 候选方案

#### 方案 A：FeedScreen 内联分发

直接在 `LazyColumn` 的 `items` lambda 中通过 `when(item.type)` 渲染三种卡片的全部 UI 代码。

```
FeedScreen.kt
  LazyColumn {
    items { item ->
      when(item.type) {
        IMAGE_BIG   → /* 大图卡全量 UI + 互动栏 + 标签 */ 
        IMAGE_SMALL → /* 小图卡全量 UI + 互动栏 + 标签 */
        VIDEO       → /* 视频卡全量 UI + 互动栏 + 标签 */
      }
    }
  }
```

思考：
1. 实现简单，适合快速 Demo 验证，但随着卡片类型增加，FeedScreen 会膨胀，公共逻辑重复，维护成本高
2. 互动栏、标签等逻辑修改时必须在每个分支同步改动，容易遗漏
3. 高度稳定性无法保证，动态文本长度导致滚动跳动

结论：可行，但鉴于方案思考是基于可扩展这一特性，没有那么合适。

#### 方案 B：AdCardFactory 工厂模式 + BaseCard 插槽

将不同卡片类型的分发收敛到 AdCardFactory 工厂函数，公共部分抽象到 BaseCard，媒体区域通过插槽传入。

```
AdCardFactory (when type 分发)
  ├── BigImageAdCard   → BaseCard { AdCoverImage(16:9) }
  ├── SmallImageAdCard → BaseCard { AdCoverImage(4:3) }
  └── VideoAdCard      → BaseCard { AdVideoMedia(playerKey) }

BaseCard:
  ├── 媒体区域插槽 (lambda 注入)
  ├── 标题 (maxLines=2, Ellipsis)
  ├── 描述 (maxLines=2, Ellipsis)
  ├── AI 摘要 (maxLines=2, primary 色)
  ├── 标签 FlowRow (maxLines=2, 可点击)
  └── 互动栏 (点赞/评论/分享/收藏, 交互动效)
```

思考：
1. 将公共 UI 提取到 BaseCard，互动栏、标签逻辑集中管理
2. 媒体部分可插槽传入，新增类型无需改动 FeedScreen 主体
3. 高度限制（文本行数 + 图片比例）保证滚动稳定性
4. 工厂职责单一，代码更易维护、可扩展性高

结论：兼顾扩展性、复用性和稳定性，比较适合。、

#### 方案 C：DSL / 配置化动态渲染

把卡片结构抽象成一套配置协议，服务端或本地 JSON 下发卡片布局，客户端根据配置动态生成 UI。渲染时不需要再手写具体的 BigImageAdCard、SmallImageAdCard，而是由统一的 DynamicCardRenderer 根据 schema 渲染。

```
CardSchema(
    type = "image_big",
    blocks = listOf(
        MediaBlock(ratio = "16:9"),
        TextBlock(field = "title", maxLines = 2),
        TextBlock(field = "summary", maxLines = 2),
        TagBlock(maxLines = 2),
        ActionBarBlock(actions = listOf("like", "comment", "share", "collect"))
    )
)
```

思考：
1. 灵活度高，可服务端下发卡片布局调整；适合商业化信息流中卡片类型多且经常变化的场景。
2. 实现成本高，需要设计 DSL 协议、动态渲染器、字段绑定、异常兜底和版本兼容逻辑；调试复杂，可能导致项目时间成本过高。

结论：
训练营项目卡片类型有限（大图、小图、视频），且主要目标是展示客户端架构能力和实现稳定、可扩展的列表，因此 DSL 方案的设计复杂度可能过高。

### 3.3 最终选型

**采用方案 B（AdCardFactory + BaseCard）**。

核心决策理由：

1. **高度稳定性**：媒体区域通过 `Modifier.aspectRatio(16f/9f)` 或 `aspectRatio(4f/3f)` 固定比例；标题/描述/摘要各限 `maxLines = 2`；标签 `FlowRow(maxLines = 2)`。三段限行 + 固定比例共同保证卡片高度的可预测性，避免 LazyColumn 在滚动中因高度突变而反复测量和跳动。

2. **复用与一致性**：BaseCard 承载所有公共 UI（文本区、标签区、互动栏），三种卡片仅传入不同的媒体 Composable。互动栏样式、标签点击行为、动效逻辑全部在 BaseCard 内统一约束，不会出现"大图卡点赞有动画但小图卡没有"的不一致。

3. **工厂职责单一**：`AdCardFactory` 的唯一职责是根据 `FeedItemType` 组装"前缀差异化卡片"，而不关心卡片内部实现。

方案 C 虽然灵活性和可扩展性最强，但对于当前项目来说，投入成本和调试复杂度过高，不符合训练营项目规模和目标。相比之下，方案 B（AdCardFactory + BaseCard 插槽）能够满足多类型卡片扩展、公共交互复用以及高度稳定性要求，同时实现成本更低、结构清晰、易于维护。因此最终选择方案 B，而方案 C 可作为未来商业化或大规模信息流的扩展方案参考。

### 3.4 关键实现细节

**高度稳定约束**：媒体区域通过 `Modifier.aspectRatio(16f/9f)`（大图、视频）或 `aspectRatio(4f/3f)`（小图）固定比例；标题、描述、AI 摘要三层文本统一 `maxLines = 2, overflow = TextOverflow.Ellipsis`；标签使用 `FlowRow(maxLines = 2)` 限制最大行数。这三项约束共同保证卡片高度可预测，LazyColumn 滚动时无需因内容差异反复测量。

**图片加载的双层渲染策略**：

```kotlin
// 1. 本地兜底图先渲染（基于 id.hashCode 取模选用 5 张内置封面之一）
Image(painter = painterResource(item.localFallbackCoverRes()), ...)
// 2. Coil 异步加载网络图，成功后 crossfade 覆盖本地图
AsyncImage(model = item.coverUrl, onSuccess = { alpha = 1f }, onError = { alpha = 0f }, ...)
// 3. 底部渐变遮罩 + 标题叠加
Box(Modifier.background(verticalGradient(Transparent → Black(0.58f)))) { Text(title) }
```

效果验证：
新增卡片类型无需改 FeedScreen；点赞、收藏等操作在所有类型一致；滚动过程中卡片高度稳定，无跳动。
此策略保证弱网或网络失败时，用户至少看到本地封面，不会出现空白区域。

---

## 4. 列表性能策略

### 4.1 难点分析

在实现 LazyColumn 信息流时，我们发现性能问题比预期复杂。Compose 与 RecyclerView 不同，它没有内置 ViewHolder 机制，列表的重组和组件复用完全依赖 key 和 contentType 两个 Compose 原生 API。

1. 单条状态变化导致全列表重组
初期实现中，点赞或收藏单条卡片时，整个列表都会重新组合（recomposition），导致滚动卡顿。原因是 item 没有正确使用 key，Compose 无法区分“同一 item 的数据变化”与“新 item 替换旧 item”。
2. 视频卡片频繁创建/销毁播放器
滚动列表时，如果每个视频卡片独立创建 ExoPlayer，快速滑动就会触发多个播放器频繁初始化和释放，消耗内存和解码器资源，影响流畅度。
3. 相邻异类卡片无法复用 Composition slot
列表中大图、小图和视频卡片交错时，如果不设置 contentType，同类型卡片之间无法复用已有的组合节点，导致额外内存开销和重组频繁。

因此关注的重点在于：
单纯依赖 LazyColumn { items(items) { ... } } 会隐藏性能陷阱，需要显式为每条 item 设置 唯一 key 和 contentType。
对状态的管理和组件复用必须提前规划，否则即便 UI 渲染正确，也会因为 Compose 重组逻辑导致滚动掉帧
视频播放器、互动状态、AI 摘要等异步数据，需要和重组策略配合，避免资源浪费。

### 4.2 候选方案

#### 方案 A：无 key + 无 contentType

```kotlin
items(items) { item -> AdCardFactory(item, ...) }
```

思考：
1. 实现最简单，代码量最少
2. 点赞、收藏等操作会触发全列表重组；视频播放器频繁创建/销毁；内存开销高，滚动容易掉帧

结论：仅在 Demo 或卡片数量极少时可行。

#### 方案 B：key + contentType

```kotlin
items(items, key = { it.id }, contentType = { it.type }) { ... }
```

思考：
1. 点赞、收藏只重组目标卡片
2. 相同类型卡片复用 Composition slot
3. 视频播放器可通过 pool 复用，减少资源浪费
4. 需要为每条 item 明确 key 和 contentType

结论：列表长度中等、适合需要支持多类型卡片的场景。

#### 方案 C：引入 Paging 3 + LazyPagingItems

核心思路是使用 Android 官方 Paging 3 管理分页数据，把列表数据封装成 PagingData<FeedItem>，Compose 层通过 collectAsLazyPagingItems() 渲染。

```
val lazyPagingItems = viewModel.feedPagingData.collectAsLazyPagingItems()

LazyColumn {
    items(
        count = lazyPagingItems.itemCount,
        key = { index -> lazyPagingItems[index]?.id ?: index },
        contentType = { index -> lazyPagingItems[index]?.type }
    ) { index ->
        val item = lazyPagingItems[index]
        if (item != null) {
            AdCardFactory(item = item, ...)
        }
    }
}
```

思考：
1. 分页能力完整，能自动处理加载状态、错误重试、预加载距离、数据追加等问题
2. 如果项目后续接入真实后端分页接口，Paging 3 会比手写 loadMore() 更规范，也更容易扩展到复杂的数据源
3. 为了使用 Paging 3，需要额外引入 PagingSource、Pager、PagingData、LazyPagingItems，还要处理刷新、追加、错误态和空态

结论：在当前项目中，数据主要来自本地 Mock 和少量缓存，分页逻辑并不复杂。使用这个方案工程复杂度会明显上升，但收益有限。


### 4.3 最终选型

**采用方案 B**，并在此基础上叠加六层性能策略。

方案 C 更适合真实线上信息流或后端分页场景。当前项目的数据规模和分页需求较轻，手写分页配合 key + contentType + StateFlow 已经可以满足性能和交互要求。因此不采用 Paging 3，而是作为后续接入真实网络分页接口时的升级方向。

#### 策略 1：状态不可变 + copy() 更新

```
FeedItem: data class, 全部字段 val
状态变更: allItems.value = allItems.value.map { if (id == target) it.copy(...) else it }
```

StateFlow 的 `equals` 判断基于引用。`copy()` 生成新引用 → StateFlow 可以发出通知 → Compose 感知变化。未修改的 item 保持原引用，`key` 匹配后 Compose 判定"未变 → 跳过重组"。

#### 策略 2：状态上移到 ViewModel

互动状态（`isLiked`、`isCollected`、`likesCount` 等）存储在 `FeedItem` 中，由 ViewModel 通过 `StateFlow<List<FeedItem>>` 统一持有。不将状态以 `remember { mutableStateOf() }` 保存在 Composable 内。

Composable 离开组合时 `remember` 状态会丢失；而 ViewModel 的生命周期跨越页面返回/重新进入，天然适合"列表 ↔ 详情"跨页面同步场景。

#### 策略 3：播放器复用池

```
FeedVideoPlayerPool:
  acquire(context, playerKey) → ExoPlayer
  release(playerKey)
```

视频卡片通过 `playerKey` 从池中租借播放器，`DisposableEffect.onDispose` 中自动归还。避免每张视频卡片独立 `new ExoPlayer()` 导致内存和解码器资源浪费。

#### 策略 4：可见自动播放与首帧预加载

```kotlin
// FeedScreen 中通过 snapshotFlow 持续追踪可见视频的比例
rememberAutoPlayVideoId(listState, items)
  → 可见比例 ≥ 60% 的视频中，取比例最高者 → 自动静音播放

rememberPreloadVideoIds(listState, items)
  → 所有可见视频的 ID 集合 → 提前 prepare（不播放）
```

两层分开的原因：自动播放只能有一个（避免多个视频同时发声/争抢带宽），但 prepare 可以是批量的（提前初始化解码器，用户点击后零延迟起播）。

#### 策略 5：分页双守卫防抖

```kotlin
fun loadMore() {
    if (isRefreshing.value || isLoadingMore.value || !hasMoreItems.value) return
    // ...
}
```

三个布尔标志确保不会出现"快速滚底触发多次加载"或"下拉刷新和加载更多同时进行"的竞态。配合 `snapshotFlow.distinctUntilChanged()` 避免重复触发。

#### 策略 6：加载更多提前触发

```kotlin
if (lastVisibleIndex >= totalCount - 4) viewModel.loadMore()
```

在距底部还有 4 个 item 时即触发加载，而非滚到底才加载，给网络请求留出预加载窗口。

### 4.4 最终结论

综合对比，方案 B 是最佳选择：

1. 避免全列表重组，保持滚动流畅
2. 支持多类型卡片，Composition slot 可复用
3. 视频播放器资源复用，性能开销可控
4. 与状态不可变策略结合，保证跨页面互动状态同步

| 验证项    | 方法                      | 预期结果                          |
| ------ | ----------------------- | ----------------------------- |
| 精准重组   | Layout Inspector 查看重组计数 | 点赞单条卡片 → 仅该卡片重组计数 +1          |
| 视频资源复用 | 快速滚动视频列表 → Logcat       | 离屏视频触发 pause + release，避免频繁创建 |
| 加载去重   | 快速触底加载                  | 不重复请求相同数据                     |
| 滚动帧率   | GPU 渲染模式分析              | 无持续掉帧，滚动流畅                    |

---

## 5. AI 输出约束与缓存策略

### 5.1 难点分析

本项目中的 AI 能力主要用于为广告生成摘要和智能标签，模型侧使用阿里云 DashScope Qwen。最初实现时，AI 功能看起来只是一次接口调用：把广告标题、描述和品类传给模型，再把返回结果展示到卡片上。但在实际接入过程中发现，真正的难点并不在于“能不能调用模型”，而在于模型输出和网络环境都存在不确定性。如果客户端直接信任模型返回结果，一旦输出格式异常或接口不可用，就可能影响整个信息流的展示。

具体问题主要包括以下几类：

1. 输出格式不稳定
虽然 Prompt 中要求模型返回 JSON，但模型仍可能返回 Markdown 包裹的内容，例如 json ... ，也可能在 JSON 前后追加解释性文字，导致客户端无法直接解析。
2. 字段内容不可控
摘要和标签需要展示在广告卡片中，如果模型生成的摘要过长、标签数量过多，或者单个标签字数过长，就会破坏卡片布局，影响列表高度稳定性。
3. 接口可用性不可控
AI 能力依赖网络请求和 API Key 配置。如果网络波动、接口超时、API Key 未配置或额度不足，客户端不能因此出现白屏、崩溃或长时间等待。
4. 重复请求造成性能和成本浪费
同一条广告的摘要和标签结果相对稳定，如果每次进入列表或详情页都重新请求模型，会增加接口调用次数，也会影响首屏加载速度。

因此，AI 模块不能只设计成“请求模型并展示结果”，而需要在客户端侧增加一套完整的输出约束、容错解析、失败降级和本地缓存机制。这样即使模型输出异常或接口不可用，App 也能正常展示内容；在缓存命中时，也可以避免重复调用模型，提升响应速度并降低调用成本。

### 5.2 候选方案

#### 方案 A：纯本地规则生成

最简单的方式是完全不依赖外部模型，通过模板和关键词匹配生成摘要和标签。这种方式实现成本最低，零网络依赖，可以保证 100% 可用。

思考：
1. 零依赖，100% 可用 
2. 摘要生硬，标签泛化，缺乏"AI 能力"的差异化 
3. 响应即时，无网络延迟 
4. 无法解析复杂语义 

结论：适合快速 Demo 或当作模型不可用时的兜底方案。

#### 方案 B：纯大模型生成

另一种思路是每次直接调用 DashScope Qwen，不做本地缓存和降级处理。这样可以充分发挥大模型生成摘要和标签的能力。

思考：
1. 摘要质量高，标签更精准，能够处理复杂语义
2. 实现简单，逻辑直接
3. 模型或网络不可用时，会导致页面显示空白或崩溃
4. 同一条广告每次都重复调用接口，浪费 API 配额，增加首屏加载延迟

结论：
适合小规模测试，或对网络和 API 完全可控的环境。对于当前项目来说风险过高，用户体验不可控。

#### 方案 C：混合架构 + 缓存（最终采用）

```
CachingAiInsightGenerator
  ├── AiInsightCache.get(itemId, modelVersion) → 命中 → 返回缓存
  └── 未命中 → HybridAiInsightGenerator
                 ├── DashScopeQwen (优先) → 成功 → 写入缓存
                 └── 失败 → LocalRuleAiInsightGenerator (降级) → 写入缓存
```

思考：
1. 大模型可用时生成质量最优，同时不可用时自动降级，保证 App 永不断路
2. SQLite 缓存命中后无需再次调用模型，提高响应速度并节省配额
3. 首次调用失败后启用熔断，后续广告直接走本地规则，避免重复等待
4. 架构复杂度中等，需要管理缓存、版本和降级逻辑
5. 需要维护缓存有效期和模型版本，以保证更新后的模型结果生效

结论：适合当前项目，展示 AI 加持的广告信息流的同时，能够保证首屏快速和稳定性。

### 5.3 最终选型

**采用方案 C**。

经过对三种候选方案的权衡，项目最终选择了 方案 C：混合架构 + 缓存。大模型可用时生成高质量摘要和标签，不可用时自动降级到本地规则，同时使用 SQLite 缓存结果，保证 App 在任何情况下都能正常展示内容，并优化首屏加载速度。核心设计包括四个层面。

#### 层面 1：System Prompt 硬约束

为了尽量保证模型输出稳定性，我们在 Prompt 中对模型严格约束，这样即使模型倾向于生成解释性文字，也能降低解析异常风险。

```text
你是广告内容理解助手。只输出严格 JSON，不要输出 Markdown。
JSON 格式：{"summary":"20到60字中文摘要","tags":["品类","风格","受众","场景"]}
tags 最多 6 个，每个 tag 最多 6 个中文字符。
```

#### 层面 2：客户端四层容错解析

由于模型仍可能返回 Markdown 包裹的 JSON 或额外文本，我们在客户端增加了多层解析和校验：

| 容错层 | 策略 | 代码逻辑 |
|--------|------|----------|
| Markdown 清洗 | 去除模型可能添加的 ` ```json` / ` ``` ` 包裹 | `content.removePrefix("```json").removePrefix("```").removeSuffix("```")` |
| JSON 截取 | 截取第一个 `{` 到最后一个 `}`，过滤前后解释文本 | `content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1)` |
| 字段裁剪 | 强制 `summary.take(60)`, `tags.take(6)`, 每个 tag `.take(6)` | 防止模型输出超长内容撑破卡片 |
| 校验与重试 | `require(summary.isNotEmpty() && tags.isNotEmpty())` | 不满足则抛异常，触发降级 |

#### 层面 3：降级与熔断

在远程模型不可用或返回异常时，系统会自动降级：

```kotlin
class HybridAiInsightGenerator : AiInsightGenerator {
    private var remoteDisabled = false  // 一次失败后永久熔断

    override suspend fun generate(item: FeedItem): AiAdInsight {
        if (remoteDisabled) return fallbackGenerator.generate(item)
        return runCatching { remoteGenerator.generate(item) }
            .getOrElse {
                remoteDisabled = true      // 熔断
                fallbackGenerator.generate(item)  // 本地规则兜底
            }
    }
}
```

如果 API Key 未配置或网络不通，每条广告的 `withTimeout(10_000)` 会导致串行生成 20 条广告耗时 200+ 秒。首次失败后直接熔断到本地规则，后续 19 条即时返回。
也就是首次失败后立即熔断，后续广告直接使用本地规则生成，避免逐条等待超时。这样可以保证列表首屏快速渲染，用户体验不受远程模型不可用影响。

#### 层面 4：SQLite 缓存

所有 AI 输出结果均写入 SQLite，实现结果复用和版本控制：

```sql
CREATE TABLE ai_insights (
    item_id       TEXT NOT NULL,
    model_version TEXT NOT NULL,
    summary       TEXT NOT NULL,
    tags_json     TEXT NOT NULL,  -- JSON Array
    source        TEXT NOT NULL,  -- QWEN / LOCAL_RULE
    updated_at    INTEGER NOT NULL,
    PRIMARY KEY (item_id, model_version)
);
```

设计要点：

- **联合主键 `(item_id, model_version)`**：模型版本升级时（如 `qianwen3.5plus-v1` → `-v2`），旧缓存自动被新版本覆盖，不会读到过期数据。
- **与 SharedPreferences 的对比**：SP 存储 JSON 数组需要序列化为字符串再反序列化，条目增多（>100 条）时 `getAll()` 性能退化。SQLite 支持精确查询和 `CONFLICT_REPLACE`，更适配缓存场景。
- **旧数据迁移**：首次访问时，`migrateLegacyCacheIfNeeded()` 将 SharedPreferences 中的历史缓存批量迁移到 SQLite，迁移完成后标记 `KEY_LEGACY_MIGRATED = true`。

### 5.4 首屏不阻塞设计

为了保证首屏快速显示，AI 生成与列表渲染完全解耦：

```
加载流程:
  1. FeedRepository.loadFeedItems() → 返回 Mock 数据 (含预置摘要)
  2. ViewModel.allItems.value = loadedItems → StateFlow 发出 → 首屏渲染完成
  3. ViewModel.generateAiInsights(loadedItems) 在协程中启动
     3a. 逐条调用 CachingAiInsightGenerator
     3b. 每次返回后: allItems.value = allItems.value.map { copy(aiSummary, aiTags) }
     3c. StateFlow 局部更新 → 卡片摘要从 Mock 替换为 AI 结果
```

关键点：第 2 步和第 3 步是异步解耦的。用户看到首屏的时间 ≈ 网络延迟 + 列表渲染，不包含 AI 生成耗时。
那么首屏展示 Mock 或缓存内容，后台异步回填 AI 输出，这样用户看到列表时间 ≈ 网络延迟 + 列表渲染，不受 AI 调用耗时影响。

### 5.5 效果验证方法

| 验证项 | 方法 | 预期结果 |
|--------|------|----------|
| 正常流程 | 配置正确 API Key → 刷新列表 | 摘要逐步替换为 "Qwen摘要：xxx"，标签更新 |
| 缓存命中 | 同一广告二次进入 | 立即展示 AI 结果，无网络请求 |
| API 不可用 | 不配置 API Key → 刷新 | 展示本地规则生成摘要，无 crash |
| 熔断生效 | 第 1 条广告调用失败 | 第 2~20 条广告直接走本地规则，不等超时 |
| 首屏速度 | 冷启动后首屏展示时间 | Mock 内容 < 1.5s 出现，AI 结果 2~5s 后逐条回填 |

---

## 6. 曝光统计口径

### 6.1 难点分析

在广告信息流中，曝光统计是衡量广告效果和计算 CTR 的核心指标，但实现精准的“有效曝光”并不简单。初期如果直接按照“只要卡片出现在屏幕上就上报”，或者“用户停留超过一定时间才上报”，都会出现问题：

1. 口径过宽
如果任何经过屏幕的广告都上报曝光，快速滑动列表时可能上百条广告瞬间被统计，导致 CTR 数据虚高，难以反映真实用户关注。
2. 口径过窄
如果仅在用户明确停留较长时间才上报，样本量会显著减少。用户快速浏览时，很多有效曝光无法被记录，CTR 可能偏低，投放优化依据不足。

所以我们需要在可见比例和停留时间之间找到平衡点，同时上报逻辑必须考虑去重和防止重复计数，也要保证算法实现简单，性能可控，避免频繁计算导致列表滚动卡顿。

因此，曝光统计不仅是一个技术实现问题，更是一个产品决策问题：需要定义一个既符合行业标准，又兼顾性能和用户体验的曝光口径。

### 6.2 行业标准参考

本项目曝光口径参考 MRC（Media Rating Council）标准：≥50% 像素面积 + ≥1 秒连续停留。在单列纵向滚动场景下，卡片宽度固定（`fillMaxWidth`），可见高度比例等价于可见面积比例，以下方案均基于此前提。

在项目的单列纵向滚动场景中，卡片宽度固定为 fillMaxWidth，因此可见高度比例可以近似等价于可见面积比例。这为我们在客户端设计曝光统计提供了简化计算依据，也保证了单列流下的统计精度与行业标准接近。

### 6.3 候选方案

#### 方案 A：item 进入可见区即上报

最直接的实现方式是，只要广告卡片的任何部分进入屏幕可见区域，就立即上报曝光。

```kotlin
// 伪代码
visibleItems.forEach { item ->
    if (!reportedIds.contains(item.id)) {
        tracker.trackExposure(item.id)
    }
}
```

思考：
1. 实现最简单，代码量少（约 8 行），无需协程或延迟逻辑
2. 快速 Fling 划过时，短暂经过屏幕的广告也会全量上报，CTR 数据虚高

结论：虽然容易实现，但在实际列表滚动中容易产生大量误报，不符合行业标准。

#### 方案 B：50% 可见 + 1 秒停留 + 去重（当前采用）

在方案 A 的基础上增加了 可见比例阈值、停留时间确认以及去重，接近 MRC 标准，同时避免误报。

```kotlin
// 核心流程
snapshotFlow { visibleItemsInfo }
    .collectLatest { items ->
        items.forEach { item ->
            val ratio = visibleRatio(item)   // 可见高度 / 总高度
            if (ratio >= 0.5f && !reported && !pending) {
                pendingJob = launch {
                    delay(1_000)              // 等待 1 秒
                    if (recheckRatio() >= 0.5f) {  // 二次确认
                        tracker.trackExposure(item)
                    }
                }
            }
            if (ratio < 0.5f) cancel(pendingJob)  // 离开 → 取消确认
        }
    }
```

思考：
1. 曝光口径明确，可解释
2. 快速划过不会误报
3. 接近行业标准 MRC，实现科学统计
4. 实现复杂度中等，需要管理协程 Job 生命周期
5. 单列流下采用高度比例近似面积，仍不支持多列或像素级精确

结论：在训练营项目的单列信息流中，方案 B 在实现成本与准确性之间取得了平衡，是比较合适的选择

#### 方案 C：MRC 像素级实现

更严格的实现方式是计算每条广告在屏幕内的像素级可见数量，与广告总像素数做精确比例，再结合停留时间判断曝光。

思考：
1. 与商业广告系统完全一致，可支持多列布局
2. 精度最高，适合正式广告投放
3. 需要获取每个 item 的屏幕绝对坐标和像素数据
4. 单列场景下效果与方案 B 相差不大，投入产出比低

结论：方案 C 精度高，但对当下项目而言复杂度过高，不如方案 B 实用。

### 6.4 最终选型

**采用方案 B**。在单列纵向滚动的场景下，卡片宽度固定（`fillMaxWidth`），可见高度比例等价于可见面积比例，方案 B 与 MRC 标准实质等效。

### 6.5 关键实现设计

#### 可见比例计算

```kotlin
fun LazyListItemInfo.visibleRatio(viewportStart: Int, viewportEnd: Int): Float {
    val itemStart = offset
    val itemEnd = offset + size
    val visibleStart = max(itemStart, viewportStart)
    val visibleEnd = min(itemEnd, viewportEnd)
    val visibleSize = (visibleEnd - visibleStart).coerceAtLeast(0)
    return visibleSize.toFloat() / size.toFloat()
}
```

三个输入：`LazyListItemInfo.offset`（item 顶部距列表顶部的距离）、`LazyListItemInfo.size`（item 高度）、`LazyListState.layoutInfo.viewportStartOffset / viewportEndOffset`（屏幕可见范围）。

#### 协程 Job 生命周期管理

```kotlin
val pendingJobs = remember { mutableStateMapOf<String, Job>() }  // itemId → 待确认的 delay Job
val reportedIds = remember { mutableStateMapOf<String, Boolean>() }  // 已上报集合
```

- item 可见比例 ≥ 50% → 启动 `launch { delay(1000); recheck; report }`，存入 `pendingJobs[id]`
- item 可见比例 < 50% → `pendingJobs[id]?.cancel()` 取消待确认的延时任务
- `collectLatest` 保证屏幕快速变化时，旧的 `collect` 被取消（避免堆积过时的检查逻辑）

#### 去重与持久化

```
同一列表生命周期内: reportedIds (内存 Set)
跨 App 重启: TrackingStore (SQLite) — 恢复已上报的曝光 ID 和统计计数
```

### 6.6 效果验证

为了验证曝光统计策略是否有效，我们设计了多种场景测试，覆盖正常浏览、快速滚动、重复进入及边界条件：

| 验证项       | 方法                    | 预期结果与设计意图                         |
| --------- | --------------------- | --------------------------------- |
| 正常浏览 | 缓慢滑动列表，每条广告停留 ≥1 秒 | 每条停留的广告各触发一次曝光，验证基本曝光统计逻辑正确 |
| 快速 Fling | 用户手指快速划过列表后松手 | 途经的广告不触发曝光，验证快速滑动不会误报，保证 CTR 数据真实 |
| 反复进出 | 同一条广告滚入 → 滚出 → 再滚入可见区 | 仅触发一次曝光，验证去重逻辑有效，避免重复上报 |
| 边界条件（50%）| 广告刚好露出 50% 高度，停留 ≥1 秒 | 触发曝光，验证可见比例阈值正确 |
| 边界条件（49%）| 广告仅露出 49% 高度，停留 ≥1 秒  | 不触发曝光，验证阈值生效，避免误报 |

---

## 7. 其他技术设计

除了卡片动态化、列表性能、AI 缓存和曝光统计四个核心难点外，项目中还涉及跨页面状态同步、视频播放缓存、对话式搜索和本地持久化等设计。这些模块虽然不是文档的主线，但直接影响 App 的完整体验和后续扩展能力。

### 7.1 跨页面状态同步

**问题**：在信息流 App 中，用户可能在列表页点赞，也可能进入详情页后再点赞或收藏。如果两个页面各自维护一份状态，就容易出现“详情页已点赞，返回列表仍是未点赞”的不一致问题。因此，本项目没有让列表页和详情页分别保存互动状态，而是将点赞、收藏、评论数等状态统一放到 FeedViewModel 中管理。

**设计**：具体实现上，FeedScreen 和 DetailScreen 共享同一个 FeedViewModel 实例。用户在任意页面点击点赞或收藏时，统一调用 viewModel.toggleLike(id) 或 viewModel.toggleCollect(id)。ViewModel 内部通过 map + copy 的方式只更新目标广告。

这样做有两个好处。第一，列表页和详情页读取的是同一份 StateFlow 数据，状态天然同步；第二，配合 LazyColumn items(key = item.id)，只有目标卡片发生重组，不会因为一次点赞导致整个列表刷新。

也考虑过让详情页独立维护状态，并在返回时通过 savedStateHandle 将结果传回列表页。但这种方式只适合页面入口较少的场景。本项目中详情页可能来自列表、搜索结果、标签筛选等多个入口，如果每个入口都实现一套回传逻辑，容易遗漏，维护成本也更高。因此最终采用共享 ViewModel 作为单一状态源。

### 7.2 视频播放与缓存

视频广告相比图片广告更容易带来性能问题。一方面，视频需要播放器、解码器和网络资源；另一方面，信息流滚动时视频卡片会频繁进入和离开屏幕。如果每次进入屏幕都重新创建播放器、重新请求网络视频，会导致卡顿、黑屏或重复缓冲。因此，本项目将视频播放拆成三个部分处理：播放地址获取、播放器复用、离屏释放。

**播放链路**：

```
触发播放 (用户点击 / 自动播放)
  → VideoCacheManager.getPlayableVideoUri(context, remoteUrl)
      ├── cacheDir/video_cache/{sha256(url)}.mp4 已存在 → 返回本地 file:// Uri
      └── 不存在 → 返回在线 https:// Uri → 后台 launch { cacheVideo() }
  → FeedVideoPlayerPool.acquire(context, playerKey) → ExoPlayer
  → player.setMediaItem(uri) → prepare() → play()
  → DisposableEffect.onDispose → pause() → pool.release(playerKey)
```

**缓存目录选择**：缓存目录选择 context.cacheDir，而不是 filesDir。原因是项目中的视频属于可重新下载的缓存资源，不是用户生成内容。放在 cacheDir 中不需要额外存储权限，卸载 App 时也会自动清理。这样的话，系统存储紧张时可能会清理缓存，但对于 Demo 视频场景可以接受。

**缓存 Key 生成 & 全屏播放**：缓存文件名使用远程 URL 的 SHA-256 结果生成，避免直接使用 URL 带来的特殊字符问题，也避免不同视频之间命名冲突。全屏播放时，使用 Dialog(usePlatformDefaultWidth = false) 承载全屏 PlayerView，并复用当前 ExoPlayer 实例，避免从卡片页进入全屏时重新缓冲。

这样普通播放时可以优先使用本地缓存，首次播放时也能在线加载并后台缓存；列表滚动时播放器不会频繁创建；进入全屏播放时不会重新加载视频，体验更连贯。

### 7.3 搜索链路设计

搜索功能是本项目中体现 AI 能力的另一部分。传统搜索一般依赖关键词匹配，而本项目希望用户可以输入更自然的查询，例如“适合学生党的性价比运动装备”，我们想让系统去理解其中包含品类、受众和场景等区分性信息，再返回相关广告，这样对用户来说更垂直。

**整体链路**：

```
用户输入自然语言
  → HybridSearchIntentParser.parse(query)
      ├── DashScopeQwenSearchIntentParser (优先)
      │     调用 DashScope Qwen，要求返回结构化 JSON:
      │       {"keywords":["运动装备"],"tags":["学生党","性价比"],"category":"电商","mediaType":"image_big"}
      └── LocalRuleSearchIntentParser (降级)
            本地正则 + 关键词词典匹配，从 query 中提取品类/场景/受众词
  → SearchIntent { keywords, tags, category, mediaType, source }
  → matchIntent(query, intent): List<FeedItem>
      对 allItems 中每条广告的 title/description/aiSummary/aiTags/category/type
      做多字段分词匹配 + 标签加权计分 → 按分数降序排列
  → 搜索结果以信息流卡片形式展示
```

**搜索匹配算法**：

匹配算法没有直接依赖单一字段，而是把广告标题、描述、AI 摘要、AI 标签、品类和卡片类型都纳入搜索文本。这样即使用户输入的词没有出现在标题中，只要命中了 AI 标签或摘要，也有机会被召回。

```kotlin
// 分词来源：Qwen keywords + tags + 用户原始 query 分词
// 搜索字段：title + description + aiSummary + aiTags + category + type
// 计分 = token命中数 + aiTags额外加权命中数
val score = tokens.count { searchableText.contains(it) } +
    item.aiTags.count { tag -> tokens.any { tag.contains(it, ignoreCase = true) } }
// 按分数降序，同分按 id 稳定排序
```

**降级与熔断**：搜索模块的降级策略与 AI 摘要生成保持一致：Qwen 优先，失败后切换到本地规则解析，并开启熔断，避免每次搜索都等待远程超时。搜索结果仍然复用 AdCardFactory 渲染，因此点赞、收藏、分享、标签点击等交互与首页信息流保持一致。这样的话，用户输入搜索，我们给出的方案既能体现 AI 对自然语言的理解，又不会完全依赖远程模型。即使模型不可用，用户仍然可以通过本地规则完成基础搜索。

**搜索页交互设计**：
- 搜索结果复用 `AdCardFactory` 渲染，点赞/收藏/分享/标签点击行为与信息流完全一致
- 搜索历史以聊天气泡形式展示（query → 匹配结果数 → 解析来源）
- 提供快捷搜索词（"学生党 性价比"、"附近优惠"、"视频创意"）降低输入成本
- 点击搜索结果中的标签 → 以该标签为 query 发起二次搜索

### 7.4 持久化存储选型

在当前的项目中，我们需要做持久化操作的数据不止一种，包括点赞收藏状态、本地评论、AI 生成结果和埋点事件。如果这些数据都放在内存中，App 重启后状态会丢失；如果全部放在 SharedPreferences 中，随着条目增加，查询和批量写入也不方便。因此本项目统一使用 SQLite 进行本地存储。

| 数据 | 存储 | 原因 |
|------|------|------|
| 点赞/收藏状态 | SQLite (`FeedInteractionStore`) | 按广告 ID 精确查询，支持旧 SP 数据迁移 |
| 本地评论 | SQLite (`FeedCommentStore`) | 按广告 ID 关联查询 |
| AI 缓存 | SQLite (`AiInsightCache`) | 联合主键 `(item_id, model_version)` |
| 埋点事件 | SQLite (`TrackingStore`) | 可作为后续批量上报的队列基础 |

最终没有继续使用 SharedPreferences，主要有三个原因。
1. SharedPreferences 更适合保存少量配置项，不适合保存大量结构化数据
2. 评论、AI 标签、埋点事件都需要按广告 ID 查询或批量处理，SQLite 更合适
3. 第三，SQLite 支持主键约束、条件查询和事务，后续如果项目规模扩大，也可以平滑迁移到 Room。

因此，本项目把 SQLite 作为统一的本地持久化基础，既满足当前训练营项目的数据保存需求，也为后续扩展预留了空间。

## 8. 未采用方案及决策记录

在项目实现过程中，除了最终采用的方案外，也评估过一些更完整或更工程化的技术方案。由于本项目属于训练营课题，核心目标是完成一个功能闭环清晰、体验流畅、可解释性强的信息流 App，因此技术选型时重点考虑了投入产出比、实现复杂度和后续扩展空间。

| 未采用的方案 | 考虑原因 | 决策原因  | 后续引入条件  |
| Paging 3 | Paging 3 是 Android 官方分页方案，能够统一管理刷新、追加加载、错误重试和预加载状态 | 当前 Mock 数据仅 ~50 行手写逻辑已覆盖，Paging 3 增加配置约 150 行，投入产出比不足 | 数据源切换为真实网络分页接口时引入 |
| Room  | Room 提供 Entity 映射、DAO 封装和编译期 SQL 校验，更适合中大型本地数据库管理 | 数据量小 (<100 条) SQLiteOpenHelper 足够；缺少 Room 编译时优势不显著 | 实体超过 3 个或迁移需求复杂时引入 |
| Hilt | Hilt 可以统一管理 ViewModel、Repository、Store 等对象的创建和生命周期 | 单 ViewModel 构造简单，手动创建零开销   | 模块数量 > 5 且跨模块依赖出现时引入 |
| DSL 动态渲染 | DSL 或 JSON Schema 动态渲染可以让服务端控制卡片布局，灵活性最高 | 架构复杂，需自建解析和布局引擎，训练营项目成本过高 | 卡片类型 > 10 且需要服务端下发时考虑 |
| 每卡片独立 ExoPlayer | 每条视频独立播放器，支持全屏 | 内存和解码器资源浪费，3 张视频卡同时存在即可能争抢 | 不采用，播放器池设计满足生产标准 |

总体来看，本项目没有一味选择最复杂或最“生产级”的方案，而是根据当前项目规模选择了相对轻量但可扩展的实现方式。例如分页先采用手写状态管理，数据库先采用 SQLiteOpenHelper，依赖关系先手动构造；但在关键设计上预留了 Paging 3、Room、Hilt 和服务端动态渲染的升级方向，保证后续扩展时不会推倒重来。

## 9.工程化补充设计

为了使项目不仅停留在功能实现层面，我们做了一定的工程化思考，补充说明项目目标边界、关键指标、风险兜底和可观测性设计。这些内容主要用于明确项目范围、评估实现效果，并说明出现异常时系统如何保证可用性。

## 9.1. 目标与非目标

## 目标

本项目的目标是实现一个基于 Jetpack Compose 的单列广告信息流 App，重点验证客户端信息流场景中的核心技术能力，包括多类型卡片渲染、列表性能优化、跨页面状态同步、AI 摘要与标签生成、对话式搜索、视频播放缓存和曝光统计。

具体目标包括：

1. 完成信息流核心体验闭环
支持单列浏览、多样式广告卡片、频道切换、详情页跳转、点赞收藏、评论展示、下拉刷新和上拉加载。
2. 保证列表滚动体验稳定
通过 key、contentType、不可变数据更新、播放器池和卡片高度约束，减少无效重组和滚动跳动。
3. 实现 AI 能力可用且可降级
使用 DashScope Qwen 生成广告摘要和标签，同时通过本地规则、缓存和熔断机制保证 AI 接口异常时 App 仍能正常展示。
4. 定义可解释的曝光统计口径
参考 MRC 标准，采用“50% 可见 + 1 秒停留 + 去重”的曝光规则，避免快速滑动造成曝光数据虚高。
5. 预留后续扩展空间
在分页、数据库、依赖注入和动态卡片渲染方面保留可升级路径，后续可根据数据规模和业务复杂度引入Paging 3、Room、Hilt 或服务端动态渲染。

## 非目标

由于本项目是训练营课题，重点在于客户端技术方案设计和核心链路实现，因此不包含以下内容：

1. 不实现真实广告投放系统
本项目不涉及广告竞价、投放策略、人群定向、预算控制和商业化结算。
2. 不接入真实后端推荐接口
当前数据主要来自 Mock 数据和本地缓存，重点验证客户端信息流展示和交互逻辑，不实现完整推荐系统。
3. 不实现服务端动态下发卡片布局
当前卡片类型由客户端工厂函数控制，暂不支持服务端 JSON Schema 或 DSL 动态渲染。
4. 不实现完整埋点上报平台
曝光、点击等事件当前主要写入本地 SQLite，作为后续批量上报和数据分析的基础，不包含服务端接收、清洗和分析链路。
5. 不追求生产级安全和发布配置
当前项目以训练营 Demo 为主，ProGuard/R8、API Key 安全管理、灰度发布等内容仅作为后续优化方向。

## 8.2 关键指标设计

为了评估项目效果，除了功能是否完成，还需要定义一些可验证的技术指标。当前项目主要关注首屏速度、列表流畅度、AI 可用性、缓存命中和曝光统计准确性。

1. 首屏展示时间	
从进入首页到第一屏广告可见的时间	冷启动后手动计时或日志打点	
目标结果：内容优先展示，首屏不等待 AI 生成
2. 动流畅性	滚动过程中是否出现明显卡顿、跳动或掉帧	
GPU 呈现模式、Layout Inspector、手动快速滑动	
目标结果：卡片高度稳定，快速滚动无明显卡顿
3. 互动重组范围	点赞或收藏单条广告时是否导致全列表重组	Layout Inspector 查看 recomposition count	目标结果：仅目标卡片发生重组
4. AI 缓存命中率	同一广告二次进入是否重复请求模型	查看日志或网络请求次数	
目标结果：缓存命中时直接读取 SQLite，不再调用远程模型
5. AI 降级可用性	Qwen 接口异常时 App 是否仍能展示摘要和标签	关闭 API Key 或模拟网络失败	
目标结果：自动切换本地规则生成，无崩溃、无白屏
6. 曝光误报控制	快速划过广告时是否错误上报曝光	快速 Fling 列表并观察埋点日志	
目标结果：未满足 1 秒停留的广告不触发曝光
7. 视频播放体验	视频首次播放、再次播放和全屏播放是否流畅	手动播放、滚动离屏、进入全屏	
目标结果：缓存命中时优先本地播放，全屏不重新缓冲

这些指标不一定全部达到生产级精度，但可以帮助说明项目不是只完成页面效果，而是从性能、稳定性和数据准确性角度进行了验证。

## 8.3 风险与兜底方案

训练营项目虽然规模有限，但仍然存在网络、模型、缓存、播放器和埋点等不确定因素。针对这些风险，本项目在客户端侧设计了兜底策略，尽量保证用户体验不断路。

风险点 && 可能影响 && 兜底方案
1. AI 接口不可用	摘要和标签无法生成，页面可能等待或空白	
兜底：首次失败后触发熔断，后续直接走本地规则生成
2. AI 输出格式异常	JSON 解析失败，影响卡片展示	
兜底：先做 Markdown 清洗、JSON 截取、字段裁剪和非空校验，失败后降级
3. AI 结果重复请求	增加接口成本，拖慢展示速度	使用 SQLite 按 (item_id, model_version) 缓存结果
网络图片加载失败	卡片媒体区域出现空白	
兜底：先展示本地兜底图，再使用 Coil 异步加载网络图
4. 视频缓存失败	视频无法离线复用或播放等待变长	
兜底：缓存失败时继续使用在线 URL 播放，不阻塞用户观看
5. 视频播放器频繁创建	快速滚动时资源浪费、卡顿或黑屏	使用播放器池复用 ExoPlayer，离屏时暂停并释放引用
曝光重复上报	CTR 数据失真	
兜底：使用内存 reportedIds 去重，并将事件写入 TrackingStore
6. 快速滑动误报曝光	曝光数虚高，无法反映真实浏览	
兜底：采用 50% 可见 + 1 秒停留 + 二次确认
7. 本地缓存数据过期	模型升级后仍展示旧摘要	
兜底：缓存主键加入 modelVersion，模型版本变化后重新生成
8. 页面返回状态不一致	详情页点赞后列表未同步	
兜底：列表页、详情页、搜索页共享同一个 ViewModel

整体思路是：对于外部依赖失败，优先降级；对于重复操作，优先去重；对于可能影响首屏的耗时任务，全部放到后台异步执行。

## 8.4 可观测性与日志设计

为了便于调试和答辩展示，项目中对关键链路预留了日志和本地记录能力。可观测性设计的目标不是实现完整监控平台，而是在 Demo 阶段能够快速定位问题，例如 AI 是否命中缓存、曝光是否触发、视频是否走本地缓存等。

模块	&& 观察内容	&& 记录方式
1. AI 摘要生成	结果来源是 Qwen、缓存还是本地规则	在 AiAdInsight.source 中记录 QWEN / LOCAL_RULE / CACHE
2. AI 调用异常	接口超时、解析失败、API Key 缺失	Logcat 输出失败原因，并触发本地规则降级
3. AI 缓存	是否命中 SQLite 缓存	缓存命中时输出 itemId 和 modelVersion
4. 曝光统计	哪条广告触发曝光、触发时间、是否重复	写入 TrackingStore，并在调试日志中输出
5. 点击统计	用户点击广告卡片、标签、搜索结果	统一通过 AdTracker 记录事件
6. 视频缓存	当前播放使用本地文件还是在线 URL	输出 cache hit / miss 和缓存文件路径
7. 播放器生命周期	播放器创建、复用、暂停、释放	在 FeedVideoPlayerPool 中记录 acquire / release
8. 分页加载	刷新、加载更多、重复触发拦截	输出当前页码、加载状态和 hasMore 标志

---

## 10. 答辩要点

本文档聚焦四个核心技术决策，答辩时可重点展开：

| 序号 | 要点 | 一句话结论 | 对应章节 |
|------|------|-----------|----------|
| 1 | **卡片动态化** | 工厂模式 + BaseCard 插槽 + 固定比例限高，三种卡片共享骨架，新增类型不改列表页 | §3 |
| 2 | **列表性能** | `key` + `contentType` + 不可变 `copy()` + 播放器池 + 可见自动播放，六层策略保证流畅 | §4 |
| 3 | **AI 容错** | System Prompt 约束 → 四层解析 → 首次失败熔断 → SQLite 缓存 → 首屏不阻塞，永不断路 | §5 |
| 4 | **曝光口径** | ≥50% 可见比例 + 1s 协程二次确认 + 去重集合，对标 MRC 标准 | §6 |

**补充亮点**：

| 要点 | 一句话结论 |
|------|-----------|
| 跨页面状态同步 | 四页面共享同一 ViewModel 实例，`map + copy` 精准重组，详情页点赞返回列表自动同步 |
| 视频播放与缓存 | SHA-256 哈希 → `cacheDir` 本地文件 → 在线降级，全屏复用 ExoPlayer 不重缓冲 |
| 对话式搜索 | DashScope Qwen 意图解析 + 多字段加权分词匹配 + 熔断降级，与 AI 摘要同构设计 |
| 双层图片渲染 | 本地兜底图先渲染 + Coil 网络图 crossfade 覆盖，弱网不空白 |
| 扩展性预留 | 手写分页/Paging 3、SQLite/Room、手动构造/Hilt 均预留低成本升级路径（§9） |

### 11. 后续规划

后续优化方向按照优先级划分为三类：

|优先级	| 优化事项	| 说明
|P1	| Compose UI 自动化测试	| 当前主要验证集中在功能测试和手动验证，后续可增加列表滚动、曝光触发、搜索交互、详情页状态同步等 UI 测试 |
|P1	| ProGuard / R8 混淆配置 | 当前 Demo 阶段混淆配置较少，发布前需要补充模型解析、网络请求、数据库实体等相关 keep 规则 |
|P2	| 曝光事件批量上报 | 当前埋点主要写入本地 SQLite，后续可以设计批量上报、失败重试和定时清理机制 |
|P2	| 多标签组合筛选 | 当前主要支持单标签筛选，后续可以支持 AND / OR 组合筛选，提高搜索和频道过滤能力 |
|P3	| Room 替换 SQLiteOpenHelper | 当数据表增多、版本迁移复杂后，可以使用 Room 提升类型安全和迁移便利性 |
|P3 |	Hilt 依赖注入	| 当 Repository、Store、Parser、Generator 等模块继续增多后，可以引入 Hilt 降低对象创建和依赖管理成本 |

总结来说，本项目当前阶段已经完成了广告信息流的核心体验闭环，包括多类型卡片、状态同步、AI 摘要与标签、视频播放缓存和曝光统计。后续如果继续向真实业务场景扩展，重点应放在真实网络数据接入、埋点批量上报、UI 自动化测试和架构组件标准化上。

---



