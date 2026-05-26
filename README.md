# 单列广告信息流 App

训练营课题：实现一个基于 Jetpack Compose 的单列广告信息流 App。

当前项目聚焦广告流核心体验：单列浏览、多样式卡片、频道切换、详情页互动同步、刷新加载、AI 摘要标签展示、曝光点击统计、视频播放器复用接口预留。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3
- ViewModel
- Coroutines
- Flow / StateFlow
- Navigation Compose
- Media3 ExoPlayer

## 已实现功能

- 单列广告信息流，基于 `LazyColumn`
- 三种广告卡片：大图、小图、视频占位
- 顶部 Tab：精选 / 电商 / 本地
- 模拟网络加载
- 下拉刷新
- 上拉加载更多
- 点击卡片进入详情页
- 返回列表保持滚动位置
- 列表页 / 详情页点赞状态同步
- 列表页 / 详情页收藏状态同步
- 分享交互模拟
- 页面切换动画
- 点赞浮动爱心彩蛋
- 收藏缩放动效
- 分享按钮轻旋转反馈
- 视频播放按钮呼吸动效
- 标签筛选条展开 / 收起动画
- 统计数字变化动画
- AI 摘要展示
- AI 标签展示
- 点击标签过滤信息流
- 曝光统计：可见比例超过 50% 且停留 1 秒
- 点击统计：卡片、点赞、收藏、分享、标签
- 信息流顶部展示本地统计面板
- Media3 ExoPlayer 播放器复用池接口预留

## 目录结构

```text
app/src/main/java/com/example/myapplication/
  data/          数据模型与 Mock 数据源
  viewmodel/     FeedViewModel，负责状态管理
  ui/feed/       信息流页面
  ui/detail/     广告详情页
  ui/components/ 通用卡片组件与播放器池接口
  tracking/      本地曝光、点击、互动统计
```

## 如何运行

1. 使用 Android Studio 打开项目目录。

```text
C:\Users\12487\AndroidStudioProjects\MyApplication
```

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

参考源码已 clone 到：

```text
external/
  Hilt-MVVM-Compose-Movie/
  BaseApp-Jetpack-Compose-Android-Kotlin/
  compose-impression-tracker/
  compose-stability-analyzer/
  androidx-media/
```

## 开发规范

- UI 层只负责渲染和事件分发，不直接修改业务数据。
- 状态统一由 `FeedViewModel` 管理。
- 对外暴露只读 `StateFlow`。
- 数据更新使用不可变 `copy()`。
- `LazyColumn` 必须使用稳定 `key`。
- 卡片类型通过 `contentType` 区分。
- 曝光、点击等事件统一走 `AdTracker`。

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
- 为视频流预留播放器复用池，避免每个卡片创建播放器
- 明确曝光口径：50% 可见比例 + 1 秒停留 + 单次去重

## 后续计划

- 接入 Coil 图片加载和缓存
- 接入 Media3 真实视频播放
- 增加对话式搜索页面
- 接入 Qwen 或本地 Mock AI 服务生成摘要和标签
- 增加统计详情页或图表
- 增加 Compose UI 测试和 ViewModel 单元测试
