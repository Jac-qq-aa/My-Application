package com.example.myapplication.data.video

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 视频缓存管理器。
 *
 * 设计目标：
 * 1. 首次播放在线视频时，把 MP4 下载到 App 私有缓存目录；
 * 2. 后续再次播放同一个 URL 时，直接返回本地文件 Uri，避免重复走网络；
 * 3. 下载失败时返回远程 URL，让播放器仍有机会在线播放，保证功能不被缓存失败阻断。
 *
 * 这里使用 Context.cacheDir，是因为训练营 Demo 的视频属于“可再生成缓存”：
 * - 优点：不需要申请存储权限，卸载 App 会自动清理；
 * - 取舍：系统存储紧张时可能清理 cacheDir。生产环境可以升级为 filesDir 或数据库记录缓存索引。
 */
object VideoCacheManager {
    private const val VIDEO_CACHE_DIR = "video_cache"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * 获取可播放的视频 Uri。
     *
     * suspend 表示这个函数会做磁盘和网络 IO，必须放在协程里调用。
     * withContext(Dispatchers.IO) 会把耗时操作切到 IO 线程池，避免阻塞 Compose 主线程。
     */
    suspend fun getPlayableVideoUri(
        context: Context,
        remoteUrl: String
    ): Uri = withContext(Dispatchers.IO) {
        val cacheFile = getCacheFile(context, remoteUrl)
        if (cacheFile.exists() && cacheFile.length() > 0L) {
            return@withContext Uri.fromFile(cacheFile)
        }

        runCatching {
            downloadToCache(remoteUrl, cacheFile)
            Uri.fromFile(cacheFile)
        }.getOrElse {
            // 网络、证书、服务端限流等异常都不应该让 UI 崩溃。
            // 返回远程 Uri 作为降级方案，Media3 会继续尝试在线播放。
            Uri.parse(remoteUrl)
        }
    }

    private fun getCacheFile(context: Context, remoteUrl: String): File {
        val cacheDir = File(context.applicationContext.cacheDir, VIDEO_CACHE_DIR)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return File(cacheDir, "${sha256(remoteUrl)}.mp4")
    }

    private fun downloadToCache(remoteUrl: String, targetFile: File) {
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }

        val connection = (URL(remoteUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "video/mp4,*/*")
            setRequestProperty("User-Agent", "AdFeedTrainingCamp/1.0")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                error("视频下载失败，HTTP $code")
            }

            connection.inputStream.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() <= 0L) {
                error("视频缓存文件为空")
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                error("视频缓存文件移动失败")
            }
        } finally {
            connection.disconnect()
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
