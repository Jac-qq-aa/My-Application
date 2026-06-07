# 单列广告信息流 App — 学习总结

> 2026-06-07 | 陈毓灏 & 李念

---

本次训练营的课题是实现一个单列广告信息流 App。对我们来说，这是第一次完整搭建一个 Compose 客户端项目——从零开始设计架构、接入 AI 能力、处理视频播放与缓存、搭建埋点体系。项目初期，我们的关注点主要在"把功能做出来"，但随着开发深入，信息流场景的复杂度逐步暴露：卡片类型扩展、列表重组性能、AI 输出稳定性、曝光口径定义——这些才是真正需要思考和权衡的地方。

下面从实际遇到的技术决策、踩坑经历和学习体会进行总结。

---

## 一、关键设计决策与踩坑修复

### 决策 1：卡片架构 — 为什么第一天就用工厂模式

项目从一开始就没有在 `FeedScreen` 的 `LazyColumn` 里直接写 `when(item.type)` 分支渲染三种卡片，而是建了 `AdCardFactory.kt`（工厂分发）+ `BaseCard`（公共骨架）的结构。这不是"遇到问题再重构"，而是动手之前先想清楚了扩展性需求。

**设计思路**：信息流卡片不只是三种，后续可能加入商品橱窗、直播卡等。如果卡片渲染逻辑散落在列表页，FeedScreen 行数会随卡片类型线性增长，且互动栏、标签点击、点赞动画等公共行为会在多个分支重复。工厂模式把"根据类型选择卡片"收敛到一个函数，BaseCard 通过 Composable lambda 插槽注入不同的媒体区域（大图 16:9 / 小图 4:3 / 视频），公共 UI 只写一次。

**代码中的体现**：`AdCardFactory.kt:108-123` — 工厂函数根据 `FeedItemType` 枚举分发；`AdCardFactory.kt:504-694` — `BaseCard` 承载标题（`maxLines=2`）、描述（`maxLines=2`）、AI 摘要（`maxLines=2`）、标签（`FlowRow(maxLines=2)`）和互动栏，三种卡片仅传入不同的 `media` lambda。

**但仍有一个遗漏**：`localFallbackCoverRes()` — 决定用哪张本地兜底图的函数，在 `AdCardFactory.kt:759-768` 和 `DetailScreen.kt:407-416` 各写了一份完全相同的实现。这说明即使整体架构是对的，公共逻辑的抽取仍需持续审视——每个新页面都可能成为复制粘贴的温床。后续应抽到 `FeedItem.kt` 或独立工具文件。

---

### 决策 2：列表性能 — key/contentType/不可变数据为什么缺一不可

Compose `LazyColumn` 的性能瓶颈和 RecyclerView 不同。RecyclerView 默认基于 position 做 ViewHolder 复用，天然有局部刷新的概念。但 Compose 的重组机制需要显式告诉它两件事：什么是"同一个 item"（key），什么是"同一种 item"（contentType）。缺一个就可能导致不该重组的卡片被重组。

**代码中的体现**：

- `FeedScreen.kt:292-296` — `items(items, key = { it.id }, contentType = { it.type })` 同时设置了两个参数
- `FeedItem.kt:38-52` — 全部字段 `val`，`data class`，状态变更仅通过 `copy()`
- `FeedViewModel.kt:248-255` — `toggleLike` 中用 `map + copy` 更新单个 item，其余保持原引用

三者协同的机制是：`key` 锚定 item 身份 → `copy()` 只改变目标 item 的引用 → Compose 通过引用对比判断"其余 item 没变" → 仅重组目标卡片。

这并不是"遇到性能问题后优化的结果"。在写第一行 `LazyColumn` 代码之前，我们先研究了 Compose 官方文档中关于列表性能的说明，也参考了 `compose-stability-analyzer` 等开源项目对重组机制的分析。核心认知是：**Compose 没有 RecyclerView 的 ViewHolder，key 和 contentType 不是"优化选项"，而是让列表正确运行的必需品**。理解这一点后，从第一天就写对了，没有在这个坑上浪费过时间。

---

### 踩坑 1：AI 输出格式不可控 — System Prompt ≠ 保证

接入 Qwen 时，我们做了两件事：System Prompt 约束输出格式（JSON + 长度限制 + 标签数量），客户端对返回结果做字段裁剪（`summary.take(60)`、`tags.take(6)`）和非空校验。设计阶段认为这两层足够了。

但实际测试中遇到了设计时没预料到的情况：

- 模型有时返回 Markdown 包裹的 JSON（`````json {...} ``` ``），`JSONObject` 直接抛异常
- 有时在 JSON 前面加了"好的，这是为您生成的广告摘要："，合法 JSON 藏在解释文本后面

这两类问题不是 Prompt 能解决的——模型在"意图上"遵守了指令（确实返回了 JSON），但在"格式上"加了额外内容。于是补了两层清洗：Markdown 前缀/后缀去除，以及截取第一个 `{` 到最后一个 `}`。最终形成四层容错，其中字段裁剪和校验是设计阶段规划的，Markdown 清洗和 JSON 截取是测试后追加的。

**代码中的体现**（`DashScopeQwenAiInsightGenerator.kt:83-123`）：

| 容错层 | 触发场景 | 代码逻辑 |
|--------|----------|----------|
| Markdown 清洗 | 模型加 ` ```json ` 包裹 | `content.removePrefix("```json").removePrefix("```").removeSuffix("```")` |
| JSON 截取 | 模型加解释文本前缀 | `content.substring(content.indexOf('{'), content.lastIndexOf('}') + 1)` |
| 字段裁剪 | 模型超量输出标签/长度 | `summary.take(60)`, `tags.take(6)`, 每个 tag `.take(6)` |
| 校验与降级 | 以上全部失败 | `require(summary.isNotEmpty() && tags.isNotEmpty())` → 抛异常 → `HybridAiInsightGenerator` 降级 |

**体会**：大模型输出不能当成稳定接口使用。System Prompt 可以"建议"模型怎么做，但不能"保证"它一定照做。客户端必须把模型输出当成不可信输入，解析、裁剪、校验、降级四层缺一不可。

---

### 踩坑 2：Ollama 本地部署 → DashScope 云端迁移

**背景**：项目早期使用本地 Ollama 部署 Qwen（`b4752d3`），电脑端运行 `ollama serve`，手机通过局域网 IP 访问。真机调试时遇到两个问题：一是电脑和手机必须在同一 Wi-Fi，且 Windows 防火墙要放行 11434 端口，网络环境稍有变化就连不上；二是答辩演示时依赖电脑环境，如果电脑没开机或没运行 Ollama，AI 功能直接不可用。

**迁移过程**：后续迁移到阿里云 DashScope（`d7eff52`），文件从 `OllamaQwenAiInsightGenerator.kt` 重命名为 `DashScopeQwenAiInsightGenerator.kt`，`OllamaQwenSearchIntentParser.kt` 同理。API 格式兼容 OpenAI 风格，改动量主要在 HTTP 请求地址和认证方式（本地无鉴权 → Bearer Token）。

**代码中的体现**：`build.gradle.kts:32-46` 通过 `buildConfigField` 定义 `QWEN_API_URL`、`QWEN_API_KEY`、`QWEN_MODEL`，默认值指向 DashScope，同时支持从 `local.properties` 覆盖。`DashScopeQwenAiInsightGenerator.kt:21` 在 API Key 为空时通过 `require(apiKey.isNotBlank())` 快速失败，由 `HybridAiInsightGenerator` 捕获后降级。

---

### 踩坑 3：AI 超时阻塞首屏，引入熔断机制

**现象**：AI 摘要采用串行生成（`FeedViewModel.kt:209-210`），逐条调 Qwen。如果每条等 10 秒超时才失败，20 条广告就是 200 秒。测试中遇到一次 API Key 没配，进入列表后 App 长时间无响应。

**解决方案**（`HybridAiInsightGenerator.kt:14-28`）：

```kotlin
private var remoteDisabled = false

override suspend fun generate(item: FeedItem): AiAdInsight {
    if (remoteDisabled) return fallbackGenerator.generate(item)
    return runCatching { remoteGenerator.generate(item) }
        .getOrElse {
            remoteDisabled = true     // 首次失败 → 本次运行全部走本地规则
            fallbackGenerator.generate(item)
        }
}
```

同时首屏不阻塞：ViewModel 先展示 Mock 数据（含预置摘要），AI 结果通过 `map + copy` 逐条异步回填（`FeedViewModel.kt:204-223`）。同样的熔断模式也复用在搜索意图解析（`HybridSearchIntentParser.kt`）和统计洞察生成（`HybridStatsInsightGenerator.kt`），三个模块共用同一套"远程优先 + 失败降级 + 首次熔断"的架构。

**体会**：调外部 API 时，不是"偶尔失败一次怎么办"，而是"全部失败会怎样"。熔断不是性能优化，是功能必需。

---

### 决策 3：播放器池 — 被硬件上限倒逼的前置设计

**背景**：视频源从本地打包资源（`aa674eb`）改为 Pexels 在线 MP4（`ea26b72`），信息流中可能同时存在多张视频卡片，播放需求从单视频变成了多视频并发。

**设计考量**：在接入 Media3 ExoPlayer 之前，先查了官方文档和社区讨论，发现一个关键约束——**硬件解码器有数量上限**，一台手机通常只能同时解码 4-8 路视频。如果每张视频卡片独立创建 `ExoPlayer`，快速滚动时多个播放器同时抢占解码器，后来的 `prepare()` 会静默失败，表现为黑屏。

**解决方案**（`VideoPlayerPool.kt:22-38`）：基于这个认知，从一开始就设计了播放器复用池。`SimpleVideoPlayerPool` 维护 `Map<String, ExoPlayer>`，卡片通过 `playerKey` 租借，`DisposableEffect.onDispose` 中归还。同时配合 `rememberAutoPlayVideoId` 和 `rememberPreloadVideoIds`（`FeedScreen.kt:526-563`），只有"可见比例 ≥ 60% 的最高视频"自动静音播放，其余可见视频只 prepare 预加载不播放。

**体会**：硬件资源有物理上限，不是"代码可以无限 new"就真的可以无限用。播放器池在这个场景下不是设计模式，是硬约束。如果等黑屏了再排查解码器报错，定位问题的成本远高于提前研究清楚上限。

---

### 决策 4：Compose 生命周期管理 — 两个容易踩坑但第一天就做对的事

作为客户端新人，有两个 Compose 独有的问题容易在后期才暴露，但本项目在搭建页面框架时就处理了。它们不涉及复杂算法，但体现了对 Compose 机制的提前理解。

**4.1 `rememberSaveable` 保存 `LazyListState` 防止返回丢位置**

问题：用户从列表页进入详情页再返回，`LazyListState` 中的滚动位置可能丢失，列表跳回顶部。原因是 `remember` 在 Composable 离开组合时会被销毁。

解决方案（`FeedScreen.kt:107-109`）：

```kotlin
val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
```

`rememberSaveable` 将 `LazyListState` 序列化到 `SavedStateHandle` 中，页面被系统回收或导航离开后重新进入时自动恢复滚动位置。`LazyListState.Saver` 是 Compose 内置的 Saver 实现，不需要手写序列化逻辑。

没有这个处理的话，用户在列表第 3 页点进详情 → 返回 → 列表跳回第 1 页，体验会很差。而且这个问题容易在开发阶段被忽略——因为开发者通常只测"点进去再回来"，而不会触发系统回收场景（旋转屏幕、后台被杀）。

**4.2 `AndroidView` 嵌入 `PlayerView` 的正确生命周期**

问题：Media3 的 `PlayerView` 是传统 Android View，需要用 `AndroidView` 桥接到 Compose 中。如果生命周期管理不当，播放器可能在 Composable 离开组合后仍然持有，或者在组合重建时重复创建。

解决方案（`AdCardFactory.kt:278-288`）：`AndroidView` 的 `factory` 只创建一次 View，`update` 在每次重组时被调用。关键是在 `DisposableEffect` 的 `onDispose` 中执行 `player.pause()` 和 `pool.release(playerKey)`（`AdCardFactory.kt:264-269`），确保卡片离屏时释放播放器资源。

如果没有这个处理，从全屏播放切回列表时可能残留一个不可见但在播放的 PlayerView，占着解码器不放。

**体会**：Compose 和传统 View 混用时，生命周期的分界线在 `DisposableEffect.onDispose`。创建在 `remember`/`factory`，更新在 `LaunchedEffect`/`update`，销毁在 `onDispose`——少一个就没闭环。

---

### 决策 5：动画反馈系统 — 让交互有"确认感"

在实现点赞、收藏、分享等交互时，不只是改变图标状态，还加了一套轻量动画反馈：

| 交互 | 动效 | 实现 |
|------|------|------|
| 点赞 | 图标缩放 + 爱心上飘淡出 | `animateFloatAsState` + `Animatable` 偏移 + alpha |
| 收藏 | 图标缩放 | `animateFloatAsState(targetValue = 1.14f)` |
| 分享 | 图标旋转反馈 | `animateFloatAsState` 每次点击累加 18° |
| 视频播放按钮 | 呼吸缩放 | `rememberInfiniteTransition` + scale 1.0→1.08 |
| 标签筛选条 | 脉冲 + 数字过渡 | `rememberInfiniteTransition` + `animateIntAsState` |
| 统计面板 | 数字变化动画 | `animateIntAsState` 计数过渡 |

设计原则：动画只做"反馈"不做"表演"——时长控制在 180-650ms，不阻塞交互，`rememberInfiniteTransition` 只用于呼吸灯这种持续性效果，不滥用。

**体会**：动画不只是"好看"，它的核心功能是告诉用户"你的操作被系统接收了"。点赞点下去，图标立刻缩放 + 爱心飘出——用户就知道点到了。如果没有这个反馈，用户在点完爱心到 UI 更新之间会有一个不确定的间隙。

---

### 决策 6：Mock 数据设计 — 在没有后端的条件下模拟真实场景

项目没有后端接口，但我们不能让 App 看起来像"硬编码的静态列表"。`MockFeedDataSource.kt` 做了几件事：

- **模拟网络延迟**：`delay(1_000)` 模拟首屏加载，`delay(700)` 模拟加载更多，让骨架屏和 loading 状态有真实的展示时机
- **分页上限**：最多 5 页 × 20 条 = 100 条，`hasMore` 在第 5 页后变为 false，模拟后端分页耗尽
- **`refreshSeed` 机制**：每次下拉刷新生成不同的数据组合，让"刷新"看起来有真实效果
- **预置 AI 摘要和标签**：每条 Mock 广告自带摘要和标签，保证 AI 服务未返回时列表也能完整展示，AI 结果只是"替换"而非"填补"

这些设计让 App 在没有后端的条件下，依然能展示完整的信息流交互闭环——加载、刷新、分页、空态、AI 增强，一个不少。

**体会**：Demo 项目最容易忽略的是"边界状态"。列表有数据只是第 0 步；加载中、加载失败、没有更多、空结果——这些状态各需要对应的 UI。Mock 数据层的职责不只是"造几条假数据"，而是"模拟出所有需要前端处理的状态"。

---

### 决策 7：工程化细节 — 依赖管理、密钥安全与代码规范

几个虽然小但体现工程意识的设计：

**版本目录管理**：`gradle/libs.versions.toml` 集中管理 35+ 依赖版本，避免每个 `build.gradle.kts` 中散落版本号。后续升级依赖时只需改一个文件。

**API Key 不硬编码**：`build.gradle.kts:32-46` 通过 `buildConfigField` 从 `local.properties` 读取 `qwen.apiKey`，默认值为空字符串。代码中用 `require(apiKey.isNotBlank())` 快速失败，由降级链路兜底。`local.properties` 已被 `.gitignore` 排除，不会意外提交密钥。

**Java/Kotlin 混合**：训练要求用 Java 实现持久化存储。`FeedInteractionStore.java` 使用 Java + SQLite，Kotlin ViewModel 通过 Repository 接口调用，两种语言在清晰的边界处协作——Java 负责存储细节，Kotlin 负责业务逻辑和 UI。

**统一的代码规范**：多层架构有明确的依赖方向（UI → ViewModel → Repository → Data），不跨层调用；`FeedItem` 全字段 `val` + `copy()` 保证不可变；`LazyColumn` 统一使用 `key` + `contentType`；曝光和点击事件统一走 `AdTracker`。这些规范不是写在 README 里的空话，而是贯穿每个文件的实践。

**体会**：工程化不是"用了什么工具"（Gradle、Hilt、Room），而是"不给后续维护埋坑"——密钥不写死、版本不散落、规范不只在文档里。

---

### 踩坑 5：SharedPreferences 存大量数据导致启动卡顿

**背景**：早期 AI 缓存、互动状态、评论都用 SharedPreferences 存储。SP 适合少量配置键值对，但当 AI 缓存 key（`modelVersion:itemId`）累积到 100+ 条时，`getAll()` 返回大 Map，在 `Synchronized` 块中遍历耗时增加，App 冷启动时偶尔出现 200-300ms 的主线程卡顿。

**解决方案**：统一迁移到 SQLite。

| 数据 | 存储位置 | 关键设计 |
|------|----------|----------|
| AI 缓存 | `AiInsightCache.kt` — `(item_id, model_version)` 联合主键 | 旧 SP 数据通过 `migrateLegacyCacheIfNeeded()` 一次性迁移 |
| 互动状态 | `FeedInteractionStore.java` — SQLite | 同上，首次打开时迁移旧 SP 数据 |
| 评论 | `FeedCommentStore.kt` — SQLite | 同上 |
| 埋点事件 | `TrackingStore.kt` — SQLite | 同时支持跨重启恢复已上报曝光集合和统计计数 |

**代码中的体现**：每个存储类都包含 `migrateLegacyCacheIfNeeded()` 或类似的一次性迁移逻辑，迁移完成后标记 `migrated = true`，后续不再读 SP（`AiInsightCache.kt:66-105`、`FeedCommentStore.kt:88-96`）。

**体会**：选存储方案不能只看"当前数据量"，要看"数据量的增长趋势"。以 item ID 为 key 的 KV 存储，条目数随时间线性增长，SP 的 `getAll()` 迟早会成为性能瓶颈。另外 AI 缓存用 `model_version` 作为复合主键的一部分，模型升级后旧缓存自动失效——缓存从一开始就应该有版本意识。

---

### 踩坑 6：网络图片弱网空白，引入本地兜底

**背景**：Mock 数据使用 Unsplash 远程图片 URL，校园网信号差时图片加载很慢，封面区域长时间空白。

**解决方案**（`AdCardFactory.kt:697-757`）：双层渲染。底层先用本地 `drawable-nodpi` 中的 5 张内置封面（`ad_cover_1~5.jpg`，根据 `id.hashCode` 取模选择）渲染；上层用 Coil 异步加载网络图，成功后 `crossfade` 覆盖。同时显式开启 Coil 三级缓存（`memoryCachePolicy(ENABLED)` + `diskCachePolicy(ENABLED)` + `networkCachePolicy(ENABLED)`）。

这一策略的演进在 git 中有清晰记录：`449ceeb` 引入本地兜底图 → `4695afc` 调整为本地图先渲染、网络图覆盖，确保划出再划回时至少本地封面立即可见。

**体会**：网络图片不是"一定会加载成功的"。本地兜底 + 网络覆盖 = 把"网络加速"变成了"渐进增强"——网络好时画质更好，网络差时至少不白屏。

---

### 踩坑 7：曝光统计快速划过时大量误报

**背景**：早期 `AdTracker.trackExposure()` 只在 `visibleItemsInfo` 变化时被调用，没有延时确认。快速 Fling 列表时，大量 item 短暂进入可见区后立即离开，全部被上报为曝光。

**解决方案**（`FeedScreen.kt:574-618`）：引入三层守卫：

1. 可见比例 ≥ 50% 时启动 `launch { delay(1_000) }`
2. 1 秒后重新计算可见比例，仍 ≥ 50% 才上报
3. 1 秒内离开可见区 → `pendingJobs[id]?.cancel()` 取消倒计时

同时用 `reportedIds: Set<String>` 做生命周期内去重（`AdTracker.kt:23`），`TrackingStore` 做跨重启恢复。

**代码演进**：初始版 `AdTracker` 只有 `exposedIds.add()` + `Log.d`（`f85f894`），后来在 `FeedScreen.kt` 中增加了协程延时 + 二次确认的曝光检测逻辑，`TrackingStore.kt` 增加了 SQLite 持久化。

**体会**：埋点不是"触发就报"，而是"满足条件才报"。曝光口径（可见比例 + 停留时长 + 去重）需要在代码里有精确表达，且应该先对标行业标准（MRC: ≥50% 像素 + ≥1s 停留），再动手实现。

---

## 二、决策与踩坑速查

| 序号 | 类型 | 要点 | 核心收获 |
|------|------|------|----------|
| 1 | 决策 | 卡片工厂 + BaseCard 插槽 | 公共 UI 收敛一处，新增类型不加页面复杂度 |
| 2 | 决策 | `key` + `contentType` + 不可变数据 | 理解 Compose 重组机制，从第一天写对 |
| 3 | 踩坑 | AI 输出带 Markdown 包裹或解释前缀 | System Prompt 约束不够，客户端必须加格式清洗和内容截取 |
| 4 | 踩坑 | 本地 Ollama → DashScope 云端迁移 | 演示稳定性优先，同时必须保留降级链路 |
| 5 | 踩坑 | AI 串行调用无熔断，200s 无响应 | 首次失败立即熔断，后续直走本地规则 |
| 6 | 决策 | 播放器池 + 可见自动播放 | 提前研究硬件解码器上限，避免资源争抢 |
| 7 | 决策 | `rememberSaveable` + `DisposableEffect` 生命周期 | Compose/View 混用时创建/更新/销毁分属三个钩子 |
| 8 | 决策 | 动画反馈系统 | 动画不是装饰，是告诉用户"操作被接收了" |
| 9 | 决策 | Mock 数据模拟真实场景 | Demo 不只是"有数据"，要覆盖加载/失败/分页耗尽/空态 |
| 10 | 决策 | 依赖版本目录 + BuildConfig 管理密钥 | 工程化不是用工具，是不给后续维护埋坑 |
| 11 | 踩坑 | SP 存大量 KV 数据，`getAll()` 卡主线程 | 迁移 SQLite + 一次性旧数据迁移 |
| 12 | 踩坑 | 网络图片弱网空白 | 本地封面先渲染 + 网络图 crossfade 覆盖 |
| 13 | 踩坑 | 曝光无延时确认，快速划过误报 | 先对标 MRC 标准，再动手实现 |
| 14 | 遗漏 | AI 缓存无版本号 | `(item_id, model_version)` 联合主键 |
| 15 | 遗漏 | `localFallbackCoverRes()` 在两个文件中重复 | 公共逻辑应早抽到共享位置 |

---

## 三、后续可改进点

### 3.1 测试

- 当前仅 4 个有效测试文件（10 个用例），集中在 AI 缓存和统计派生。缺失：降级熔断逻辑、分页防抖、曝光去重、视频缓存命中/失败的单测
- `localFallbackCoverRes()` 重复定义在两个文件中，应抽到 `FeedItem.kt` 或独立工具文件
- 可引入 Compose UI 测试覆盖列表滚动、点赞同步、搜索交互

### 3.2 性能

- 视频缓存无淘汰策略（`cacheDir` 只写入不清理），应增加总大小上限或 LRU
- AI 缓存无过期时间，当前仅在 `model_version` 变更时失效
- 视频预加载可扩展到可见区 ±2 个视频

### 3.3 架构

- `DefaultFeedRepository` 硬编码 `MockFeedDataSource`，应通过构造函数注入，便于切换网络数据源
- 手写分页适合 Mock 场景，接入真实接口后可引入 Paging 3
- 搜索召回当前仅 token 命中 + 标签加权计分，可引入语义相似度辅助排序
- `FeedScreenState` 可进一步拆分，将 UI 状态（Loading/Empty/Error）和业务状态（items/tag/category）解耦

### 3.4 用户体验

- 标签过滤仅支持单选，可增加 AND/OR 多标签组合筛选
- 曝光事件缺少批量上报和失败重试机制
- 暗色模式下 `background`/`surface` 等沿用 Material3 默认值，未专门调优

---

## 四、总结

这次项目最大的收获是，对"完成一个功能"和"设计一个可维护的功能"有了更清晰的区分。信息流 App 看起来只是列表、卡片和点击交互，但实际涉及组件拆分、状态管理、性能约束、AI 容错、缓存设计和数据统计口径。这些细节在项目初期容易被忽略，但它们恰恰决定了代码质量和后续扩展性。

几点最深的体会：

1. **先定架构约束，再写 UI**：不可变数据、`key`/`contentType`、工厂模式、播放器池这些不是后期优化，是第一天就该遵守的规范。项目之所以在这些点上没踩坑，是因为动手之前研究了 Compose 重组机制和硬件约束——但如果没提前做功课，等到掉帧、黑屏再排查，代价会大得多。

2. **Compose 生命周期是隐形的坑**：`remember` vs `rememberSaveable`、`AndroidView` 的 factory/update/dispose、`DisposableEffect` 的 onDispose——这些 API 的区别不显眼，但用错一个就会导致滚动位置丢失、播放器泄漏、或者返回页面后状态错乱。作为客户端新人，最容易忽略的不是复杂算法，而是框架的生命周期契约。

3. **大模型输出不是稳定接口**：Prompt 约束只能减少异常概率，不能消除。设计时规划的字段裁剪和校验是必要的，但不够——Markdown 包裹和解释文本前缀这类问题是实际调用中才暴露的，客户端必须把模型输出当成不可信输入对待。

4. **存储方案要看增长趋势**：SharedPreferences 在 10 条数据时没问题，100 条时 `getAll()` 就成了瓶颈。选方案不能看"当前数据量"，要看"数据量的增长曲线"。缓存设计第一天就该有版本意识。

5. **埋点先定义口径，再写代码**："出现即上报"和"50%+1s 二次确认"产出的数据完全不同。先对标行业标准（MRC），再动手实现。`snapshotFlow` + `delay` + `cancel` + 去重集合的组合，把"满足条件才报"这一语义精确翻译成了协程逻辑。

后续做类似项目时，会更早地从方案对比、边界条件、降级策略和效果验证几个角度思考，而不是只追求先把页面做出来。

---

回顾整个训练营过程，除了技术层面的收获，还有几点关于学习和协作的体会：

关于**学习方法**：这个项目的大部分知识点——Compose 重组机制、Media3 生命周期、Coil 缓存策略、MRC 曝光标准——都不是课堂上教的，而是通过看官方文档、读开源项目源码、调试 Logcat、反复试错学到的。客户端开发的特点是"概念不难，细节很多"，一个 `remember` 和 `rememberSaveable` 的区别，不看文档用错了就是滚动位置丢失的 bug。主动查阅和验证，比被动接受知识点重要得多。

关于**团队分工**：项目采用"模块分工 + 交叉 Review"的方式——一人主攻 UI 层（Compose 页面、动画、交互），一人主攻数据层（Repository、AI 管道、存储、埋点），核心模块（ViewModel 状态管理、导航架构）两人共同讨论确定。这种分工的好处是每个人有自己完整负责的模块链路，不会出现"大家都写了一点但都不深入"的情况；交叉 Review 则保证没有人对整体架构失控。

关于**定位**：作为客户端新人，我们在这个项目里最想证明的不是"用了多少技术栈"，而是"对每个技术选型都能说清楚为什么选它、放弃了什么、代价是什么"。这也是这份总结花了大量篇幅写方案对比和踩坑复盘的原因——用过的技术不一定算掌握，能讲清楚取舍的才算。

---

## 五、参考资料

项目开发过程中，以下资料对技术决策和实现有直接帮助：

**开源参考项目**（位于 `external/`，分析文档在 `docs/references/`）：

| 项目 | 借鉴内容 |
|------|----------|
| Hilt-MVVM-Compose-Movie | Compose + ViewModel + Flow 的 MVVM 架构，Repository 分层 |
| BaseApp-Jetpack-Compose-Android-Kotlin | Compose 项目基础工程分层和模块化组织 |
| compose-impression-tracker | Compose 列表曝光追踪实现，曝光时长和可见比例计算 |
| compose-stability-analyzer | Compose 参数稳定性分析，辅助理解重组机制 |
| androidx/media | Media3 ExoPlayer 官方实现，播放器生命周期管理 |

**官方文档与技术标准**：

| 资料 | 关联 |
|------|------|
| Android Compose 官方文档 — Lists 与 key/contentType | 决策 2（列表性能） |
| Android Compose 官方文档 — Lifecycle 与 SideEffects | 决策 4（rememberSaveable / DisposableEffect） |
| Media3 ExoPlayer 官方文档 — 播放器资源管理 | 决策 3（播放器池） |
| Coil 3 文档 — 缓存策略 | 踩坑 6（图片兜底） |
| MRC (Media Rating Council) 可见曝光标准 | 踩坑 7（曝光口径） |
| 阿里云 DashScope API 文档 — Qwen 模型调用 | 踩坑 1-3（AI 接入） |

---

## 六、技能与认知收获

**技术技能**：

- **Jetpack Compose**：`LazyColumn` 性能优化（`key`/`contentType`/不可变数据）、自定义动画（`animateFloatAsState`/`rememberInfiniteTransition`）、Compose 与传统 View 的互操作（`AndroidView`）
- **状态管理**：ViewModel + StateFlow + `combine` 派生状态的 MVVM 模式，跨页面共享 ViewModel 实现状态同步
- **AI 集成**：大模型 API 调用（DashScope Qwen）、Prompt 工程、客户端容错与降级、缓存与熔断
- **视频播放**：Media3 ExoPlayer 接入、播放器池复用、视频缓存（`cacheDir` + SHA-256 哈希）、全屏播放
- **图片加载**：Coil 3 三级缓存策略、本地兜底 + 网络覆盖的双层渲染
- **数据持久化**：SQLiteOpenHelper 的使用、SharedPreferences → SQLite 迁移策略
- **埋点与统计**：基于 MRC 标准的曝光口径设计、`snapshotFlow` + 协程延时实现停留计时

**工程意识**：

- 设计前先做方案对比，不是"想到什么写什么"
- 外部依赖必须有降级和熔断，不是"偶然失败怎么办"而是"全部失败会怎样"
- 埋点和统计类功能先定义口径再写代码，否则数据没有意义
- 选存储方案不能只看当前数据量，要看增长趋势
- 动画不是装饰，是给用户操作确认感
- 代码规范（不可变数据、统一接口、明确的依赖方向）要从第一天执行

---

## 七、自我评价

**做得好的地方**：

- 架构设计上，多个模块（卡片工厂、播放器池、AI 降级链路、曝光检测）从第一天就用了正确的模式，没有经历"写了再重构"的弯路。这得益于在动手前花时间研究了官方文档和参考项目。
- AI 容错和缓存体系设计得比较完整——从 Prompt 约束到四层解析到熔断到 SQLite 缓存，链路清晰，降级后用户侧零影响。
- 文档输出完整——技术方案设计文档和学习总结都覆盖了方案对比、实现细节和效果评估，不只是"做了什么"，还有"为什么这样做"。

**可以做得更好的地方**：

- 测试覆盖不足。当前的单元测试集中在 AI 缓存和统计派生，降级熔断、分页防抖、曝光去重等关键逻辑缺少测试。后续应该做到"核心逻辑有单测，关键路径有 UI 测试"。
- `localFallbackCoverRes()` 在两个文件中重复定义，属于公共逻辑抽取遗漏。应该在新增每个页面时审视是否有可复用的公共组件。
- 搜索召回排序目前仅基于 token 匹配 + 标签加权，未引入语义相似度，长尾查询的召回率和排序质量还有提升空间。

**如果重新做这个项目，会改变什么**：

- 从第一周就建立测试习惯，不让测试积压到最后。
- 更早地引入团队内部的 Code Review 流程，让代码质量问题在提交时就暴露，而不是等后期文档复盘才发现遗漏。
- 对搜索和推荐类的功能，先定义评估指标（召回率/准确率/排序质量），再迭代算法，而不是"感觉匹配得不错"就停手。
