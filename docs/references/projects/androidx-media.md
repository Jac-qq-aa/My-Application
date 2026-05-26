# androidx/media

## URL

https://github.com/androidx/media

## Local Source

```text
external/androidx-media/
```

## 参考价值

这是 AndroidX Media3 的官方源码仓库，适合参考 ExoPlayer、播放器生命周期和 Media3 组件设计。

## 对本项目的借鉴

当前项目已经添加 Media3 ExoPlayer 依赖，并预留：

```text
ui/components/VideoPlayerPool.kt
```

设计目标：

- 不给每个视频卡片都创建播放器
- 当前可见视频从池中获取播放器
- 离屏时归还或暂停播放器
- 页面销毁时统一释放

## 后续实现建议

下一阶段可以实现：

- VIDEO 卡片接入真实 `PlayerView` 或 Compose bridge
- 当前可见视频自动播放
- 滑出屏幕后暂停
- 静音自动播放
- 首帧预加载
- 播放进度和曝光口径联动
