# Hilt-MVVM-Compose-Movie

## URL

https://github.com/piashcse/Hilt-MVVM-Compose-Movie

## Local Source

```text
external/Hilt-MVVM-Compose-Movie/
```

## 参考价值

这个项目适合作为 Compose + MVVM 状态管理参考，重点关注：

- ViewModel 如何持有页面状态
- UI 如何订阅状态并响应变化
- 数据层、ViewModel、UI 层如何分离
- Hilt 依赖注入在中大型项目中的组织方式

## 对本项目的借鉴

当前广告信息流项目已经采用同类思路：

- `FeedViewModel` 持有 `StateFlow<List<FeedItem>>`
- `FeedScreen` 使用 `collectAsState()` 订阅状态
- 点赞 / 收藏只调用 ViewModel 方法，不在 Composable 中直接改数据
- 详情页未来也应该复用同一状态源，避免列表和详情各存一份状态

## 不直接照搬的点

当前训练营项目还处于核心骨架阶段，暂时没有引入 Hilt、Repository、UseCase。

后续项目变复杂后，可以逐步演进为：

```text
data -> repository -> domain/usecase -> viewmodel -> ui
```
