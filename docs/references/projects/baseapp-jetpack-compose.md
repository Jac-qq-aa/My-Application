# BaseApp-Jetpack-Compose-Android-Kotlin

## URL

https://github.com/merttoptas/BaseApp-Jetpack-Compose-Android-Kotlin

## Local Source

```text
external/BaseApp-Jetpack-Compose-Android-Kotlin/
```

## 参考价值

这个项目适合作为 Compose 工程骨架和 Clean Architecture 参考，重点关注：

- 多层目录组织
- UI、ViewModel、数据访问的边界
- 可测试架构
- 后续引入网络、本地缓存、依赖注入时的扩展方式

## 对本项目的借鉴

当前项目先保持轻量分层：

```text
data/
viewmodel/
ui/
tracking/
```

这样初学阶段更容易理解。等功能继续增加，可以进一步拆出：

```text
domain/
repository/
network/
database/
di/
```

## 不直接照搬的点

不要一开始就把训练营项目拆成过多层，否则会增加学习负担。

当前更重要的是先把以下链路跑通：

```text
Mock 数据 -> ViewModel StateFlow -> Compose UI -> 用户操作 -> StateFlow 更新 -> UI 局部刷新
```
