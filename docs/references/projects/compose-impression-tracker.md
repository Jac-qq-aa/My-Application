# compose-impression-tracker

## URL

https://github.com/abema/compose-impression-tracker

## Local Source

```text
external/compose-impression-tracker/
```

## 参考价值

这个项目专注于 Jetpack Compose 中的曝光追踪，适合参考：

- LazyColumn / LazyGrid 中 item 可见性的判断
- 曝光事件如何去重
- 曝光逻辑如何和 UI 组合
- 如何避免滚动过程中的误报

## 对本项目的借鉴

当前项目在 `FeedScreen.kt` 中实现了一个轻量版曝光追踪：

```text
卡片可见比例 >= 50%
并且连续停留 >= 1 秒
才上报有效曝光
```

实现方式：

- 从 `LazyListState.layoutInfo.visibleItemsInfo` 读取可见 item
- 计算 item 可见高度 / item 总高度
- 使用协程 `delay(1_000)` 做停留时间确认
- 使用 `reportedIds` 避免重复曝光

## 后续升级方向

如果训练营后续要求更严谨，可以参考该项目继续增强：

- 多列表容器支持
- 卡片局部遮挡判断
- 页面切后台时暂停曝光计时
- 曝光批量上报
- 曝光事件缓存和失败重试
