package com.example.myapplication.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Media3 ExoPlayer 复用池接口。
 *
 * 单列视频流不能给每个 VIDEO 卡片都创建播放器，否则内存、解码器和首帧耗时都会失控。
 * 更合理的策略是维护少量 Player，根据当前可见视频卡片租借/归还。
 * 这个训练项目先展示封面占位，后续接入真实播放时可以从这里扩展。
 */
interface VideoPlayerPool {
    fun acquire(context: Context, itemId: String): ExoPlayer
    fun release(itemId: String)
    fun releaseAll()
}

@OptIn(UnstableApi::class)
class SimpleVideoPlayerPool : VideoPlayerPool {
    private val players = mutableMapOf<String, ExoPlayer>()

    override fun acquire(context: Context, itemId: String): ExoPlayer {
        return players.getOrPut(itemId) {
            ExoPlayer.Builder(context.applicationContext).build()
        }
    }

    override fun release(itemId: String) {
        players.remove(itemId)?.release()
    }

    override fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
    }
}

/**
 * 当前训练营 Demo 先用一个进程内共享池。
 *
 * 真实项目可以进一步按页面生命周期创建/销毁池，或者限制池大小，只保留当前可见视频附近的播放器。
 */
object FeedVideoPlayerPool : VideoPlayerPool by SimpleVideoPlayerPool()
