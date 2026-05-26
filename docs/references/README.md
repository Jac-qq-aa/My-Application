# Reference Projects

这个目录用于沉淀训练营项目的开源参考资料。

当前广告信息流 App 的核心挑战和参考项目对应关系：

| 设计问题 | 参考项目 | 借鉴点 | 本项目落点 |
| --- | --- | --- | --- |
| 状态同步：详情页更新后列表页同步刷新 | Hilt-MVVM-Compose-Movie | ViewModel + StateFlow 管理页面状态 | `FeedViewModel` 作为单一事实源，`toggleLike` / `toggleCollect` 更新同一份状态 |
| 工程分层与可扩展骨架 | BaseApp-Jetpack-Compose-Android-Kotlin | Compose + MVVM / Clean Architecture 分层 | 当前先用 `data` / `viewmodel` / `ui` / `tracking`，后续可扩展 `repository` / `domain` |
| 动态卡片高度与 Compose 性能 | compose-stability-analyzer | 分析 Compose 稳定性和重组风险 | 使用不可变 `FeedItem`、`LazyColumn key`、固定媒体比例、限制摘要和标签行数 |
| 曝光埋点 | compose-impression-tracker | LazyColumn / LazyGrid 可见性追踪 | `TrackEffectiveExposure` 计算可见比例，超过 50% 且停留 1 秒才上报 |
| 视频播放 | androidx/media | Media3 ExoPlayer 官方实现 | `VideoPlayerPool` 预留播放器复用接口 |

## Reference Index

- [Hilt-MVVM-Compose-Movie](projects/hilt-mvvm-compose-movie.md)
- [BaseApp-Jetpack-Compose-Android-Kotlin](projects/baseapp-jetpack-compose.md)
- [compose-stability-analyzer](projects/compose-stability-analyzer.md)
- [compose-impression-tracker](projects/compose-impression-tracker.md)
- [androidx/media](projects/androidx-media.md)

## Local Source Directories

参考项目源码已 clone 到项目根目录的 `external/` 下：

```text
external/
  BaseApp-Jetpack-Compose-Android-Kotlin/
  Hilt-MVVM-Compose-Movie/
  androidx-media/
  compose-impression-tracker/
  compose-stability-analyzer/
```
