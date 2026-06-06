# 单列广告信息流 App

训练营课题：实现一个基于 Jetpack Compose 的单列广告信息流 App。

当前项目聚焦广告流核心体验：单列浏览、多样式卡片、频道切换、详情页互动同步、刷新加载、AI 摘要标签展示、对话式搜索、曝光点击统计、Media3 视频播放与本地缓存。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel
- Coroutines
- Flow / StateFlow
- Navigation Compose
- Media3 ExoPlayer
- Coil
- SQLite 本地持久化层
- 本地 Qwen/Ollama AI 生成器

## 已实现功能

- 单列广告信息流，基于 `LazyColumn`
- 三种广告卡片：大图、小图、视频占位
- 顶部 Tab：精选 / 电商 / 本地
- 模拟网络加载
- 首屏骨架屏
- 空态 / 错误态 / 重试
- 下拉刷新
- 上拉加载更多
- 点击卡片进入详情页
- 返回列表保持滚动位置
- 列表页 / 详情页点赞状态同步
- 列表页 / 详情页收藏状态同步
- 系统分享面板真实调用
- 详情页评论区与本地评论发布
- 本地评论持久化保存
- 页面切换动画
- 点赞浮动爱心彩蛋
- 收藏缩放动效
- 分享按钮轻旋转反馈
- 视频播放按钮呼吸动效
- Media3 ExoPlayer 视频卡片点击播放
- Pexels 在线 MP4 视频源
- 视频首次播放下载到 App 本地缓存，后续直接播放本地文件
- 视频支持静音 / 开声音切换
- 视频支持全屏播放弹窗
- 视频卡片离屏暂停并释放播放器
- 使用 Pexels 在线 MP4，模拟真实广告视频加载
- 标签筛选条展开 / 收起动画
- 统计数字变化动画
- Coil 网络图片加载
- 图片 loading / error 占位
- 网络图片失败时自动显示本地广告封面
- 本地广告封面先显示，网络图成功后覆盖，弱网滚动也不空白
- 图片内存缓存 / 磁盘缓存 / 网络缓存策略
- 点赞 / 收藏状态持久化保存
- 本地 Qwen 生成广告摘要和智能标签
- Qwen 不可用时自动降级为本地规则生成
- AI 摘要展示
- AI 标签展示
- Coil 网络图片加载与缓存
- 点击标签过滤信息流
- 对话式搜索页面
- 本地关键词匹配搜索
- Qwen 搜索意图解析，失败时降级为本地规则解析
- 曝光统计：可见比例超过 50% 且停留 1 秒
- 点击统计：卡片、点赞、收藏、分享、标签
- 评论入口与评论发布点击统计
- 信息流顶部展示本地统计面板
- 统计详情页：指标卡片、CTR / 互动率、事件分布图
- Media3 ExoPlayer 播放器复用池接口与基础播放接入

## 目录结构

```text
app/src/main/java/com/example/myapplication/
  data/          数据模型、Mock 数据源与 Repository
    local/       SQLite 本地持久化存储
    repository/  FeedRepository 数据入口
  viewmodel/     FeedViewModel，负责状态管理
  ui/feed/       信息流页面
  ui/detail/     广告详情页
  ui/search/     对话式搜索页面
  ui/components/ 通用卡片组件与播放器池接口
  tracking/      本地曝光、点击、互动统计
```

## 如何运行

1. 使用 Android Studio 打开项目目录。

2. 在 Android Studio 中执行 Gradle Sync。

```text
File > Sync Project with Gradle Files
```

3. 连接真机或启动模拟器。

4. 点击右上角绿色运行按钮运行 `app`。

## 技术文档

- [技术方案](docs/TECHNICAL_DESIGN.md)
- [架构说明](docs/AD_FEED_ARCHITECTURE.md)
- [参考项目索引](docs/references/README.md)

## 参考项目

参考项目分析见 [参考项目索引](docs/references/README.md)。

## 开发规范

- UI 层只负责渲染和事件分发，不直接修改业务数据。
- 状态统一由 `FeedViewModel` 管理。
- 对外暴露只读 `StateFlow`。
- 数据更新使用不可变 `copy()`。
- `LazyColumn` 必须使用稳定 `key`。
- 卡片类型通过 `contentType` 区分。
- 曝光、点击等事件统一走 `AdTracker`。
- 图片统一使用 Coil 加载，并显式开启内存缓存、磁盘缓存和网络缓存。
- 点赞 / 收藏、评论、AI 缓存和埋点统计均通过 SQLite 持久化，ViewModel 不直接操作具体存储 API。
- 视频素材使用 Pexels 直连 MP4，首次播放由 `VideoCacheManager` 写入 `cacheDir/video_cache`，下载失败时降级为在线播放。

## 本地 Qwen 摘要和标签

项目已内置 Ollama Qwen 客户端：

```text
app/src/main/java/com/example/myapplication/data/ai/
```

推荐本地部署：

```bash
ollama pull qwen2.5:0.5b
ollama serve
```

默认地址：

```text
http://10.242.173.63:11434
```

说明：

- 当前配置为真机访问电脑 Ollama：`http://10.242.173.63:11434`。
- Android 模拟器访问电脑本机 Ollama，需要把地址改回 `http://10.0.2.2:11434`。
- 真机调试时，电脑和手机必须在同一个 Wi-Fi；Windows 防火墙需要允许 `11434` 端口访问。
- 如果 Ollama 只监听本机地址，先在 Windows 环境变量中设置 `OLLAMA_HOST=0.0.0.0:11434`，再重新执行 `ollama serve`。
- 如果 Qwen 服务没启动或访问失败，App 会自动使用本地规则生成摘要和标签。

App 端实现方式：

- `OllamaQwenAiInsightGenerator` 请求 Ollama `/api/chat`，要求模型只返回 JSON。
- `HybridAiInsightGenerator` 优先调用 Qwen，失败自动降级到 `LocalRuleAiInsightGenerator`。
- 为了避免真机 IP 配错时每条广告都超时，Qwen 首次失败后，本次运行会直接使用本地规则。
- `FeedViewModel` 不阻塞首屏，先展示 Mock 数据，再逐条把 Qwen 摘要和标签更新回 `StateFlow`。

## AI 声明

本项目使用 AI 辅助完成部分代码骨架、注释和技术文档整理。

AI 辅助内容包括：

- Compose 页面骨架
- ViewModel + StateFlow 状态管理样例
- 曝光统计逻辑初版
- 技术方案文档和 README 初版
- 开源参考项目分析

人工验证和优化包括：

- 检查项目目录和包名，确保代码放入正确位置
- 根据训练营题目调整功能范围
- 使用不可变数据和 `StateFlow` 保证状态同步
- 使用 `LazyColumn key` 和 `contentType` 优化列表复用
- 为 AI 摘要和标签添加行数约束，减少动态高度抖动
- 为首屏加载、空结果和加载失败补充明确状态视图
- 接入 Coil 加载 Mock 数据中的网络封面图
- 使用系统分享 Intent 完成真实分享调用
- 为详情页补充本地评论列表与发布入口
- 为本地埋点补充统计详情页和基础图表展示
- 为对话式搜索补充 Qwen 意图解析和本地规则降级，确保无模型时仍可演示
- 为视频流预留播放器复用池，避免每个卡片创建播放器
- 明确曝光口径：50% 可见比例 + 1 秒停留 + 单次去重

## 后续计划

- 对搜索结果排序和召回策略继续优化
- 视业务复杂度引入 Room / Paging 3 / Hilt
- 增加 Compose UI 自动化测试
