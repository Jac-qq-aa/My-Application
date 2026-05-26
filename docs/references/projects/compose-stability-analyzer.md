# compose-stability-analyzer

## URL

https://github.com/skydoves/compose-stability-analyzer

## Local Source

```text
external/compose-stability-analyzer/
```

## 参考价值

这个工具用于分析 Jetpack Compose 项目中的稳定性信息，帮助发现可能导致不必要重组的类型和参数。

本项目里的动态卡片问题，本质上也和 Compose 性能相关：

- 数据类型是否稳定
- LazyColumn 是否有稳定 key
- item 内容是否频繁改变尺寸
- 文本和标签是否导致多次测量

## 对本项目的借鉴

当前项目已经做了几项基础优化：

- `FeedItem` 使用不可变 `data class`
- `LazyColumn.items` 设置 `key = item.id`
- `contentType = item.type` 帮助 LazyColumn 复用相同类型 item
- 媒体区域使用 `aspectRatio`
- AI 摘要 `maxLines = 2`
- AI 标签 `FlowRow(maxLines = 2)`

## 后续可验证点

当项目变大后，可以用稳定性分析工具检查：

- `FeedItem` 是否被 Compose 识别为稳定类型
- 卡片参数是否导致过度重组
- 大列表滚动时是否有明显 recomposition 热点
