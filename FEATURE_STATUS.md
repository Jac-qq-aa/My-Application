# 单列广告信息流 App — 功能实现状态表

> 生成日期：2026-05-26 | 分支：main | 最新提交：`20498b0`

---

## 一、信息流核心浏览

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 1.1 | 单列信息流 LazyColumn 滚动 | ✅ 已实现 | `ui/feed/FeedScreen.kt:154-187` | 启动 App 即可滚动浏览广告卡片 | 无 |
| 1.2 | LazyColumn key + contentType 优化 | ✅ 已实现 | `ui/feed/FeedScreen.kt:163-168` | Layout Inspector 观察重组范围 | 若 key 不稳定会导致全量重组，当前使用 `item.id` 已保证 |
| 1.3 | 大图卡片 (IMAGE_BIG) | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 滚动到对应类型卡片，封面占满 16:9 区域 | 使用 Coil 加载真实网络图片，渐变色作为兜底 |
| 1.4 | 小图卡片 (IMAGE_SMALL) | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 滚动到对应类型卡片，封面为 4:3 区域 | 使用 Coil 加载真实网络图片，渐变色作为兜底 |
| 1.5 | 视频卡片 (VIDEO) 播放 | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 滚动到视频卡片，点击播放按钮播放真实 MP4 | 使用 Media3 ExoPlayer + PlayerView |
| 1.6 | 卡片工厂模式 (AdCardFactory) | ✅ 已实现 | `ui/components/AdCardFactory.kt:43-65` | 根据 `FeedItemType` 枚举分发不同卡片 | 扩展新卡片类型只需加一个 when 分支 |
| 1.7 | 卡片共享组件 (BaseCard) | ✅ 已实现 | `ui/components/AdCardFactory.kt:113-389` | 所有卡片复用标题/摘要/标签/互动栏 | BaseCard 承载了大部分 UI，改动需注意影响面 |
| 1.8 | 封面图片 + 渐变兜底 | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 每张卡片的封面区域加载网络图片，失败时保留渐变兜底 | 已接入 Coil |
| 1.9 | 卡片圆角 + 阴影 | ✅ 已实现 | `ui/components/AdCardFactory.kt:117-125` | 卡片有 12.dp 圆角和默认 elevation | 无 |

---

## 二、频道切换与过滤

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 2.1 | 顶部 Tab 频道切换 | ✅ 已实现 | `ui/feed/FeedScreen.kt:106-138` | 点击"精选/电商/本地"Tab，列表自动过滤 | 过滤为本地内存过滤，数据来自同一 Mock 源 |
| 2.2 | Tab 切换时保留点赞/收藏状态 | ✅ 已实现 | `viewmodel/FeedViewModel.kt:35-40` | 在 A 频道点赞 → 切到 B 频道再切回 → 点赞保留 | `allItems` 为单一数据源，过滤不丢失状态 |
| 2.3 | AI 标签点击过滤 | ✅ 已实现 | `viewmodel/FeedViewModel.kt:42-50` | 点击卡片上的标签 → 列表只显示含该标签的广告 | 当前为 AND 逻辑，同一时间只能选一个标签 |
| 2.4 | 标签过滤条展示/清除 | ✅ 已实现 | `ui/feed/FeedScreen.kt:140-152` | 选中标签后顶部出现 chip，可点击 X 清除 | AnimatedVisibility 展开收起有动画 |
| 2.5 | 标签过滤 + Tab 切换联动 | ✅ 已实现 | `viewmodel/FeedViewModel.kt:22-40` | 选中标签后切 Tab，标签自动清除 | `selectCategory()` 中主动清除 `_selectedTag` |
| 2.6 | 标签过滤空结果提示 | ✅ 已实现 | `ui/components/ScreenStateView.kt` | 标签筛选无结果时展示空态并提供清除筛选按钮 | 使用 `FeedScreenState.Empty` 区分标签筛选空态 |
| 2.7 | 多标签组合筛选 | ❌ 未实现 | — | — | 当前仅支持单标签，不支持 AND/OR 多选 |

---

## 三、分页加载与刷新

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 3.1 | 下拉刷新 (pullRefresh) | ✅ 已实现 | `ui/feed/FeedScreen.kt:189-197` | 在列表顶部下拉 → 出现刷新指示器 → 数据重置 | 使用 Material3 `pullRefresh` modifier |
| 3.2 | 上拉加载更多 (无限滚动) | ✅ 已实现 | `ui/feed/FeedScreen.kt:209-226` | 滚动到底部 → 自动加载下一页 → 列表追加数据 | 触发条件：最后可见 item 距底部 ≤ 4 个 |
| 3.3 | 加载更多 Footer | ✅ 已实现 | `ui/feed/FeedScreen.kt:199-207` | 底部显示加载中 spinner 或"没有更多了" | 异常简单，无"点击重试"交互 |
| 3.4 | 防重复加载 | ✅ 已实现 | `viewmodel/FeedViewModel.kt:145-146` | 快速滚动到底部不会触发多次加载 | `isLoadingMore` 和 `hasMore` 双重守卫 |
| 3.5 | 分页上限控制 | ✅ 已实现 | `data/MockFeedDataSource.kt:18-20` | 加载到第 5 页后 `hasMore` 变为 false | Mock 数据共 5 页 × 20 条 = 100 条 |
| 3.6 | 加载失败/错误状态 | ✅ 已实现 | `viewmodel/FeedViewModel.kt`, `ui/components/ScreenStateView.kt` | Mock 抛异常时展示错误提示和重试按钮 | `MockFeedDataSource.failCount` 可手动模拟失败 |
| 3.7 | 加载中骨架屏 (Shimmer) | ✅ 已实现 | `ui/components/ScreenStateView.kt` | 首次加载时显示 3 张卡片骨架和 shimmer 动效 | 仅用于首屏 Loading，不替代下拉刷新 |
| 3.8 | 空数据状态 (EmptyView) | ✅ 已实现 | `ui/components/ScreenStateView.kt` | 加载成功但列表为空时展示空态提示 | 标签筛选空态提供清除筛选入口 |

---

## 四、详情页与导航

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 4.1 | 点击卡片进入详情页 | ✅ 已实现 | `ui/feed/FeedScreen.kt:177-184` | 点击任意卡片 → 导航到详情页 | 使用 Navigation Compose，传递 itemId |
| 4.2 | 详情页展示完整信息 | ✅ 已实现 | `ui/detail/DetailScreen.kt:78-229` | 详情页显示封面/标题/描述/AI摘要/标签/互动按钮 | 所有元素可滚动 |
| 4.3 | 返回列表保持滚动位置 | ✅ 已实现 | `MainActivity.kt:37-58` | 列表页滚到中间 → 点卡片 → 返回 → 列表位置不变 | `LazyListState` 由 Compose 自动保持 |
| 4.4 | 页面切换动画 (slide + fade) | ✅ 已实现 | `MainActivity.kt:40-58` | 进入详情：右侧滑入 + 淡入；返回：滑出 + 淡出 | enterTransition / exitTransition 均已配置 |
| 4.5 | 详情页"找不到"处理 | ✅ 已实现 | `ui/detail/DetailScreen.kt:61-76` | 若 itemId 无效（如数据被刷新），显示"未找到该广告" | 边界情况已有处理 |
| 4.6 | 详情页返回按钮 | ✅ 已实现 | `ui/detail/DetailScreen.kt` | 点击 TopAppBar 返回箭头回到列表 | 使用 AutoMirrored ArrowBack 图标 |
| 4.7 | 详情页分享按钮 | ✅ 已实现 | `ui/detail/DetailScreen.kt` | 详情页底部互动区提供分享按钮 | 当前仍为模拟分享/埋点，未调系统 Intent |
| 4.8 | Deep Link 支持 | ❌ 未实现 | — | — | 无外部跳转支持 |

---

## 五、互动功能（点赞/收藏/分享）

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 5.1 | 列表页点赞 | ✅ 已实现 | `ui/components/AdCardFactory.kt:236-262` | 点击卡片上的爱心图标 → 图标填色 + 计数变化 | 首次点赞触发浮动爱心彩蛋 |
| 5.2 | 列表页收藏 | ✅ 已实现 | `ui/components/AdCardFactory.kt:322-343` | 点击收藏图标 → 图标变化 + 缩放动效 | 首次收藏触发 scale 动画 |
| 5.3 | 列表页分享 | ✅ 已实现 | `ui/components/AdCardFactory.kt`, `ui/share/ShareUtils.kt` | 点击分享图标 → 系统分享面板 + 轻微旋转反馈 + 计数变化 | 使用 Android `ACTION_SEND` |
| 5.4 | 详情页点赞 | ✅ 已实现 | `ui/detail/DetailScreen.kt:163-178` | 详情页点爱心 → 同步到列表页 | 调用同一个 `viewModel.toggleLike()` |
| 5.5 | 详情页收藏 | ✅ 已实现 | `ui/detail/DetailScreen.kt:180-194` | 详情页点收藏 → 同步到列表页 | 调用同一个 `viewModel.toggleCollect()` |
| 5.6 | 跨页面状态同步 | ✅ 已实现 | `viewmodel/FeedViewModel.kt` | 详情页点赞 → 返回列表 → 对应卡片已更新 | 共享 ViewModel 保证单一事实源 |
| 5.7 | 点赞浮动爱心彩蛋 | ✅ 已实现 | `ui/components/AdCardFactory.kt:241-260` | 首次点赞时爱心图标向上飘出 + 缩放消失 | 使用 `Animatable` + `withAnimation` 实现 |
| 5.8 | 收藏缩放动效 | ✅ 已实现 | `ui/components/AdCardFactory.kt:326-340` | 点击收藏 → 图标 scale 变化 | 使用 `animateFloatAsState` |
| 5.9 | 分享旋转动效 | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 点击分享 → 图标轻微旋转反馈 | 使用 `animateFloatAsState` |
| 5.10 | 视频播放按钮呼吸动效 | ✅ 已实现 | `ui/components/AdCardFactory.kt:101-110` | 视频卡片显示呼吸灯效果的播放按钮 | `rememberInfiniteTransition` + scale 1.0→1.08 |
| 5.11 | 互动计数动画 | ✅ 已实现 | `ui/feed/FeedScreen.kt:207-218` | 统计数据变化时数字以动画过渡 | `animateIntAsState` |
| 5.12 | 评论功能 | ✅ 已实现 | `ui/detail/DetailScreen.kt`, `viewmodel/FeedViewModel.kt`, `data/FeedComment.kt` | 详情页展示评论列表，可发布本地评论，列表评论数同步增加 | 评论暂存内存，未接数据库 |
| 5.13 | 分享真实调用 (Intent) | ✅ 已实现 | `ui/share/ShareUtils.kt` | 列表页和详情页分享按钮均可拉起系统分享面板 | 保留原有分享埋点统计 |

---

## 六、AI 摘要与标签

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 6.1 | AI 摘要展示 | ✅ 已实现 | `ui/components/AdCardFactory.kt:192-205` | 卡片上显示 2 行 AI 摘要（蓝色文字） | 数据来自 Mock 固定字符串，非 AI 生成 |
| 6.2 | AI 标签展示 | ✅ 已实现 | `ui/components/AdCardFactory.kt:208-229` | 卡片底部 FlowRow 显示彩色标签 chip | `maxLines=2`，超出溢出隐藏 |
| 6.3 | 摘要限行 (maxLines=2) | ✅ 已实现 | `ui/components/AdCardFactory.kt:198` | 超长摘要截断显示"..." | 防止卡片高度抖动 |
| 6.4 | 标签限行 (FlowRow maxLines) | ✅ 已实现 | `ui/components/AdCardFactory.kt:213` | 标签超过 2 行时隐藏溢出项 | 同上 |
| 6.5 | 标签点击过滤 | ✅ 已实现 | `viewmodel/FeedViewModel.kt:42-50` | 见 2.3 | — |
| 6.6 | 真实 AI 生成摘要/标签 | ❌ 未实现 | — | — | 计划接入 Qwen 或本地 Mock AI 服务 |
| 6.7 | AI 摘要/标签缓存 | ❌ 未实现 | — | — | 计划以 adId 为 key 缓存 summary/tags/modelVersion |
| 6.8 | 结构化 AI 输出约束 | ❌ 未实现 | — | — | 文档设计了 JSON 格式但未实现校验 |

---

## 七、埋点与统计

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 7.1 | 有效曝光追踪 | ✅ 已实现 | `ui/feed/FeedScreen.kt:231-272` | 卡片可见 ≥50% 且停留 1 秒 → Log.d 输出 | 口径：50% 可见面积 + 1 秒连续停留 + 去重 |
| 7.2 | 曝光去重 | ✅ 已实现 | `tracking/AdTracker.kt:17-24` | 同一条广告只上报一次曝光 | 使用 `exposedIds: MutableSet<String>` |
| 7.3 | 点击追踪（卡片） | ✅ 已实现 | `ui/feed/FeedScreen.kt:179` | 点击卡片 → 记录 click 事件 | — |
| 7.4 | 点击追踪（点赞） | ✅ 已实现 | `ui/components/AdCardFactory.kt:260` | 点赞 → 记录 like 事件 | — |
| 7.5 | 点击追踪（收藏） | ✅ 已实现 | `ui/components/AdCardFactory.kt:343` | 收藏 → 记录 collect 事件 | — |
| 7.6 | 点击追踪（分享） | ✅ 已实现 | `ui/components/AdCardFactory.kt`, `ui/detail/DetailScreen.kt` | 分享 → 拉起系统分享并记录 share 事件 | — |
| 7.7 | 点击追踪（标签） | ✅ 已实现 | `ui/feed/FeedScreen.kt:220` | 点击标签 → 记录 tag 事件 | — |
| 7.7.1 | 点击追踪（评论） | ✅ 已实现 | `ui/feed/FeedScreen.kt`, `ui/detail/DetailScreen.kt` | 点击评论入口或发布评论 → 记录 comment 相关事件 | 本地统计归入点击数 |
| 7.8 | 统计面板 | ✅ 已实现 | `ui/feed/FeedScreen.kt:74-100` | 列表顶部显示曝光/点击/点赞/收藏/分享计数 | 数字有动画 |
| 7.9 | 统计面板入口 | ✅ 已实现 | `ui/feed/FeedScreen.kt` | 点击首页统计面板进入统计详情页 | 使用 `animateContentSize` 保持数字变化平滑 |
| 7.10 | 批量事件上报 | ❌ 未实现 | — | — | 当前每次事件即时 Log，无队列批量上报 |
| 7.11 | 统计详情页/图表 | ✅ 已实现 | `ui/stats/StatsScreen.kt`, `MainActivity.kt` | 展示指标卡片、CTR/互动率圆环、事件分布柱状图 | 纯 Compose 绘制，无额外图表库 |
| 7.12 | 埋点上报到真实服务 | ❌ 未实现 | — | — | 当前仅 `Log.d`，未接入任何埋点 SDK |

---

## 八、视频播放

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 8.1 | VideoPlayerPool 接口 | ✅ 已实现 | `ui/components/VideoPlayerPool.kt:5-12` | 查看接口定义：acquire/release/releaseAll | 接口已定义，等待实现 |
| 8.2 | SimpleVideoPlayerPool 实现 | ✅ 已实现 | `ui/components/VideoPlayerPool.kt` | 基于 `Map<String, ExoPlayer>` 的简单实现 | 已通过视频卡片租借/释放播放器 |
| 8.3 | Media3 ExoPlayer / UI 依赖 | ✅ 已引入 | `app/build.gradle.kts` | Gradle 已引入 `media3-exoplayer` 和 `media3-ui` | 依赖就绪 |
| 8.4 | 真实视频播放 | ✅ 已实现 | `ui/components/AdCardFactory.kt`, `data/MockFeedDataSource.kt` | 滚动到视频卡片，点击播放按钮播放在线 MP4 | 使用开发测试视频源，当前为点击播放 |
| 8.5 | 离屏暂停 / 释放 | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 视频卡片离开组合时 pause 并 release | 基于 Compose dispose 生命周期 |
| 8.6 | 静音播放 | ✅ 已实现 | `ui/components/AdCardFactory.kt` | 视频默认音量 0，适合信息流场景 | 可在后续加静音按钮 |
| 8.7 | 首帧预加载 | ❌ 未实现 | — | — | 计划特性 |

---

## 九、图片加载与缓存

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 9.1 | Coil 图片加载 | ✅ 已实现 | `ui/components/AdCardFactory.kt`, `ui/detail/DetailScreen.kt` | 列表卡片和详情页封面加载 `coverUrl` 网络图片 | 使用 Coil 3 + `AsyncImage` |
| 9.2 | 图片内存缓存 | ✅ 已实现 | `app/build.gradle.kts` | Coil 加载图片时自动使用内存缓存 | 使用 Coil 默认缓存策略 |
| 9.3 | 图片磁盘缓存 | ✅ 已实现 | `app/build.gradle.kts` | Coil 加载图片时自动使用磁盘缓存 | 使用 Coil 默认缓存策略 |
| 9.4 | 图片加载占位/错误态 | ✅ 已实现 | `ui/components/AdCardFactory.kt`, `ui/detail/DetailScreen.kt` | 图片加载前或失败时保留渐变封面兜底 | 标题仍覆盖在封面底部 |

---

## 十、搜索功能

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 10.1 | 对话式搜索页面 | ✅ 已实现 | `ui/search/SearchScreen.kt` | 点击首页右上角搜索按钮，输入自然语言需求 | 使用聊天式输入 + 结果列表 |
| 10.2 | 关键词匹配搜索 | ✅ 已实现 | `ui/search/SearchScreen.kt` | 输入“学生党 性价比”“附近优惠”“视频创意”等 | 基于标题、描述、AI摘要、标签、本地频道字段匹配 |
| 10.3 | AI 意图解析搜索 | ✅ 已实现 | `data/ai/OllamaQwenSearchIntentParser.kt`, `ui/search/SearchScreen.kt` | 搜索时先用 Qwen 解析 keywords/tags/category/mediaType，再匹配广告 | Qwen 不可用时自动降级为本地规则解析 |

---

## 十一、数据层架构

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 11.1 | FeedItem 不可变数据模型 | ✅ 已实现 | `data/FeedItem.kt:1-33` | 所有字段 val + data class + copy() 模式 | 13 个字段完整 |
| 11.2 | FeedItemType 枚举 | ✅ 已实现 | `data/FeedItem.kt:6-8` | 三种类型：IMAGE_BIG / IMAGE_SMALL / VIDEO | — |
| 11.3 | FeedCategory 枚举 | ✅ 已实现 | `data/FeedItem.kt:10-14` | 三种频道：FEATURED / ECOMMERCE / LOCAL | 含中文 displayTitle |
| 11.4 | Mock 数据源 | ✅ 已实现 | `data/MockFeedDataSource.kt` | 模拟网络延迟 1s，生成 20 条/页 | 最多 5 页 |
| 11.5 | Repository 层 | ❌ 未实现 | — | — | 当前 ViewModel 直接调用 MockDataSource |
| 11.6 | UseCase / Domain 层 | ❌ 未实现 | — | — | 后续演进方案 |
| 11.7 | 真实网络请求 (Retrofit) | ❌ 未实现 | — | — | — |
| 11.8 | Room 本地数据库 | ❌ 未实现 | — | — | — |
| 11.9 | Paging 3 分页 | ❌ 未实现 | — | — | 当前手写分页，后续可替换 |
| 11.10 | 依赖注入 (Hilt/Koin) | ❌ 未实现 | — | — | 当前手动创建 ViewModel |

---

## 十二、工程化与测试

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 12.1 | 版本目录 (libs.versions.toml) | ✅ 已实现 | `gradle/libs.versions.toml` | 集中管理 35+ 依赖版本 | — |
| 12.2 | Material 3 主题 | ✅ 已实现 | `ui/theme/Theme.kt` | 支持亮色/暗色 + Android 12 动态取色 | — |
| 12.3 | 暗色模式 | ✅ 已实现 | `ui/theme/Theme.kt:30-40` | 系统切换暗色模式 → App 自动跟随 | — |
| 12.4 | ViewModel 单元测试 | ❌ 未实现 | — | — | 仅有一个 ExampleUnitTest |
| 12.5 | Compose UI 测试 | ❌ 未实现 | — | — | 依赖已声明但无实际测试 |
| 12.6 | 插桩测试 | ❌ 未实现 | — | — | 仅有一个 ExampleInstrumentedTest |
| 12.7 | ProGuard 混淆 | ❌ 未配置 | `app/proguard-rules.pro` | — | 文件为空，发布前需配置 |
| 12.8 | CI/CD | ❌ 未实现 | — | — | — |
| 12.9 | 性能分析 (Layout Inspector) | ❌ 未进行 | — | — | 文档建议但未执行 |
| 12.10 | Compose Stability 分析 | ❌ 未进行 | — | — | 文档建议但未执行 |

---

## 十三、文档

| 序号 | 功能 | 状态 | 对应文件 | 演示方式 | 风险 / 备注 |
|------|------|------|----------|----------|-------------|
| 13.1 | README 项目说明 | ✅ 已实现 | `README.md` | — | 包含功能列表、技术栈、运行方式 |
| 13.2 | 技术方案文档 | ✅ 已实现 | `docs/TECHNICAL_DESIGN.md` | — | 614 行，覆盖架构/方案/性能 |
| 13.3 | 架构说明文档 | ✅ 已实现 | `docs/AD_FEED_ARCHITECTURE.md` | — | 313 行，分层说明 |
| 13.4 | 参考项目索引 | ✅ 已实现 | `docs/references/README.md` | — | 5 个参考项目分析 |
| 13.5 | 演示视频 | ❌ 未录制 | — | — | 文档建议录制 3-8 分钟 |

---

## 汇总统计

| 类别 | 已实现 | 未实现 | 完成率 |
|------|--------|--------|--------|
| 信息流核心浏览 | 9 | 0 | 100% |
| 频道切换与过滤 | 6 | 1 | 86% |
| 分页加载与刷新 | 8 | 0 | 100% |
| 详情页与导航 | 7 | 1 | 88% |
| 互动功能 | 13 | 0 | 100% |
| AI 摘要与标签 | 5 | 3 | 63% |
| 埋点与统计 | 11 | 2 | 85% |
| 视频播放 | 3 | 4 | 43% |
| 图片加载与缓存 | 4 | 0 | 100% |
| 搜索功能 | 3 | 0 | 100% |
| 数据层架构 | 4 | 6 | 40% |
| 工程化与测试 | 3 | 7 | 30% |
| 文档 | 4 | 1 | 80% |
| **合计** | **80** | **24** | **77%** |

---

## 建议优先补充项目（按重要程度排序）

### 高优先级（核心体验缺失）

| 优先级 | 功能 | 原因 |
|--------|------|------|
| P0 | 加载中骨架屏 / 空态 / 错误态 | 已完成，后续可补充更细的分页错误重试 |
| P0 | 详情页返回按钮 | 已完成 |
| P0 | Coil 图片加载 | 已完成，后续可优化图片占位图和加载策略 |
| P1 | Media3 真实视频播放 | VIDEO 类型卡片无实际播放能力 |

### 中优先级（功能完整度）

| 优先级 | 功能 | 原因 |
|--------|------|------|
| P1 | 对话式搜索页面 | README 列为后续计划，是差异化亮点 |
| P1 | 真实 AI 摘要/标签生成 | 当前全为 Mock 固定字符串 |
| P1 | 统计详情页/图表 | 已完成基础指标和图表，后续可扩展趋势分析 |
| P2 | 评论功能 | 已完成基础本地评论，后续可接入持久化和评论详情 |
| P2 | 分享真实调用 (Intent) | 已完成 |

### 低优先级（架构演进）

| 优先级 | 功能 | 原因 |
|--------|------|------|
| P2 | Repository + UseCase 层 | 当前简单分层够用，功能增多后再拆 |
| P2 | Room 本地缓存 | 无网络请求，暂无缓存需求 |
| P2 | Hilt 依赖注入 | 单 ViewModel 场景够用 |
| P3 | Paging 3 | 手写分页可控，接入收益不大 |
| P3 | Compose UI 测试 + ViewModel 单元测试 | 训练营阶段可后补 |
| P3 | ProGuard 混淆 | 开发阶段不需要 |
| P3 | 演示视频录制 | 答辩前完成即可 |
