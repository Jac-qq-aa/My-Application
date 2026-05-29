package com.example.myapplication.ui.components

import android.view.LayoutInflater
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.myapplication.R
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.FeedItemType
import com.example.myapplication.data.video.VideoCacheManager
import kotlinx.coroutines.launch

/**
 * 广告卡片工厂。
 *
 * 信息流通常有很多卡片形态：大图、小图、视频、直播、商品橱窗等。
 * 工厂函数把“根据 type 选择卡片”的逻辑收敛在一个地方。
 */
@Composable
fun AdCardFactory(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    shouldAutoPlayVideo: Boolean = false,
    shouldPreloadVideo: Boolean = false,
    modifier: Modifier = Modifier
) {
    when (item.type) {
        FeedItemType.IMAGE_BIG -> BigImageAdCard(item, onLikeClick, onCollectClick, onShareClick, onCommentClick, onTagClick, onCardClick, modifier)
        FeedItemType.IMAGE_SMALL -> SmallImageAdCard(item, onLikeClick, onCollectClick, onShareClick, onCommentClick, onTagClick, onCardClick, modifier)
        FeedItemType.VIDEO -> VideoAdCard(
            item = item,
            onLikeClick = onLikeClick,
            onCollectClick = onCollectClick,
            onShareClick = onShareClick,
            onCommentClick = onCommentClick,
            onTagClick = onTagClick,
            onCardClick = onCardClick,
            shouldAutoPlayVideo = shouldAutoPlayVideo,
            shouldPreloadVideo = shouldPreloadVideo,
            modifier = modifier
        )
    }
}

@Composable
private fun BigImageAdCard(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier
) {
    BaseCard(item, onLikeClick, onCollectClick, onShareClick, onCommentClick, onTagClick, onCardClick, modifier) {
        AdCoverImage(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
    }
}

@Composable
private fun SmallImageAdCard(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier
) {
    BaseCard(item, onLikeClick, onCollectClick, onShareClick, onCommentClick, onTagClick, onCardClick, modifier) {
        AdCoverImage(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        )
    }
}

@Composable
private fun VideoAdCard(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    shouldAutoPlayVideo: Boolean,
    shouldPreloadVideo: Boolean,
    modifier: Modifier
) {
    BaseCard(item, onLikeClick, onCollectClick, onShareClick, onCommentClick, onTagClick, onCardClick, modifier) {
        AdVideoMedia(
            item = item,
            shouldAutoPlay = shouldAutoPlayVideo,
            shouldPreload = shouldPreloadVideo
        )
    }
}

@Composable
@OptIn(UnstableApi::class)
fun AdVideoMedia(
    item: FeedItem,
    shouldAutoPlay: Boolean = false,
    shouldPreload: Boolean = false,
    modifier: Modifier = Modifier
) {
    val videoUrl = item.videoUrl
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var isPreparing by remember(item.id, videoUrl) { mutableStateOf(false) }
    var playableUri by remember(item.id, videoUrl) { mutableStateOf<Uri?>(null) }
    var isPrepared by remember(item.id, videoUrl) { mutableStateOf(false) }
    var isMuted by remember(item.id) { mutableStateOf(true) }
    var isFullscreen by remember(item.id) { mutableStateOf(false) }
    var manualPlayRequested by remember(item.id) { mutableStateOf(false) }
    val shouldPlay = shouldAutoPlay || manualPlayRequested
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(item.id, videoUrl) {
        if (videoUrl == null) {
            null
        } else {
            FeedVideoPlayerPool.acquire(context, item.id).apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (isMuted) 0f else 1f
            }
        }
    }

    LaunchedEffect(player, isMuted) {
        player?.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(player, playableUri) {
        val uri = playableUri ?: return@LaunchedEffect
        val currentPlayer = player ?: return@LaunchedEffect
        currentPlayer.setMediaItem(MediaItem.fromUri(uri))
        currentPlayer.prepare()
        currentPlayer.playWhenReady = shouldPlay
        isPreparing = false
        isPrepared = true
        isPlaying = shouldPlay
    }

    LaunchedEffect(player, shouldPlay, isPrepared) {
        val currentPlayer = player ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        currentPlayer.playWhenReady = shouldPlay
        isPlaying = shouldPlay
    }

    LaunchedEffect(videoUrl, shouldAutoPlay, shouldPreload, playableUri, isPreparing) {
        if (videoUrl == null || playableUri != null || isPreparing || (!shouldAutoPlay && !shouldPreload)) {
            return@LaunchedEffect
        }
        isPreparing = true
        playableUri = VideoCacheManager.getPlayableVideoUri(context, videoUrl)
    }

    DisposableEffect(item.id, player) {
        onDispose {
            player?.pause()
            isFullscreen = false
            FeedVideoPlayerPool.release(item.id)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
    ) {
        if (player != null && isPrepared && playableUri != null && !isFullscreen) {
            AndroidView(
                factory = { viewContext ->
                    LayoutInflater.from(viewContext)
                        .inflate(R.layout.view_texture_player, null, false) as PlayerView
                },
                update = { playerView ->
                    playerView.player = player
                    player.playWhenReady = shouldPlay
                },
                modifier = Modifier.matchParentSize()
            )
            VideoInlineControls(
                isMuted = isMuted,
                onMuteToggle = { isMuted = !isMuted },
                onFullscreenClick = { isFullscreen = true }
            )
        } else {
            AdCoverImage(
                item = item,
                modifier = Modifier.matchParentSize()
            )
            VideoPlayOverlay(
                enabled = videoUrl != null,
                loading = isPreparing,
                onClick = {
                    if (videoUrl == null || isPreparing) {
                        return@VideoPlayOverlay
                    }
                    if (playableUri != null) {
                        manualPlayRequested = true
                        return@VideoPlayOverlay
                    }

                    scope.launch {
                        manualPlayRequested = true
                        isPreparing = true
                        // 第一次播放先下载到本地缓存；已缓存时这个函数会直接返回本地文件 Uri。
                        playableUri = VideoCacheManager.getPlayableVideoUri(context, videoUrl)
                    }
                }
            )
        }
    }

    if (player != null && isPlaying && playableUri != null && isFullscreen) {
        FullscreenVideoDialog(
            player = player,
            isMuted = isMuted,
            onMuteToggle = { isMuted = !isMuted },
            onDismiss = { isFullscreen = false }
        )
    }
}

@Composable
@OptIn(UnstableApi::class)
private fun FullscreenVideoDialog(
    player: Player,
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { viewContext ->
                    LayoutInflater.from(viewContext)
                        .inflate(R.layout.view_texture_player, null, false) as PlayerView
                },
                update = { playerView ->
                    playerView.player = player
                    player.playWhenReady = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .aspectRatio(16f / 9f)
            )

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.52f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "退出全屏",
                        tint = Color.White
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.52f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                IconButton(onClick = onMuteToggle) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (isMuted) "打开声音" else "静音",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.VideoInlineControls(
    isMuted: Boolean,
    onMuteToggle: () -> Unit,
    onFullscreenClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
    ) {
        VideoControlButton(onClick = onMuteToggle) {
            Icon(
                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                contentDescription = if (isMuted) "打开声音" else "静音",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        VideoControlButton(onClick = onFullscreenClick) {
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = "全屏播放",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun VideoControlButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.Black.copy(alpha = 0.52f)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clickable(onClick = onClick)
        ) {
            content()
        }
    }
}

@Composable
private fun BoxScope.VideoPlayOverlay(
    enabled: Boolean,
    loading: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "videoPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "videoPulseScale"
    )

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Black.copy(alpha = 0.48f),
        modifier = Modifier
            .align(Alignment.Center)
            .clickable(enabled = enabled, onClick = onClick)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            }
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .padding(14.dp)
                    .size(32.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = if (enabled) "播放" else "暂无视频",
                tint = Color.White,
                modifier = Modifier
                    .padding(14.dp)
                    .size(32.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BaseCard(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onCommentClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier,
    media: @Composable () -> Unit
) {
    var showLikeBurst by remember { mutableStateOf(false) }
    var likeBurstSeed by remember { mutableIntStateOf(0) }
    val likeScale by animateFloatAsState(
        targetValue = if (item.isLiked) 1.18f else 1f,
        label = "likeScale"
    )
    val collectScale by animateFloatAsState(
        targetValue = if (item.isCollected) 1.14f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "collectScale"
    )
    val likeBurstAlpha by animateFloatAsState(
        targetValue = if (showLikeBurst) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "likeBurstAlpha"
    )
    val likeBurstOffset by animateFloatAsState(
        targetValue = if (showLikeBurst) -42f else -12f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "likeBurstOffset"
    )

    LaunchedEffect(likeBurstSeed) {
        if (likeBurstSeed > 0) {
            showLikeBurst = true
            kotlinx.coroutines.delay(650)
            showLikeBurst = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick(item.id) },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            media()

            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = item.aiSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxLines = 2
                ) {
                    item.aiTags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable { onTagClick(tag) }
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                onLikeClick(item.id)
                                if (!item.isLiked) {
                                    likeBurstSeed += 1
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (item.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "点赞",
                                tint = if (item.isLiked) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = likeScale
                                    scaleY = likeScale
                                }
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer {
                                    alpha = likeBurstAlpha
                                    translationY = likeBurstOffset
                                    scaleX = 1.2f
                                    scaleY = 1.2f
                                }
                        )
                    }
                    Text(text = item.likesCount.toString(), style = MaterialTheme.typography.labelLarge)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { onCommentClick(item.id) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = "评论",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(text = item.commentsCount.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    var shareSpinSeed by remember { mutableIntStateOf(0) }
                    val shareRotation by animateFloatAsState(
                        targetValue = shareSpinSeed * 18f,
                        animationSpec = tween(durationMillis = 180),
                        label = "shareRotation"
                    )
                    IconButton(
                        onClick = {
                            shareSpinSeed += 1
                            onShareClick(item.id)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "分享",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                rotationZ = shareRotation
                            }
                        )
                    }
                    IconButton(onClick = { onCollectClick(item.id) }) {
                        Icon(
                            imageVector = if (item.isCollected) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "收藏",
                            tint = if (item.isCollected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                scaleX = collectScale
                                scaleY = collectScale
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdCoverImage(
    item: FeedItem,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageLoaded by remember(item.id) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            .background(coverBrush())
    ) {
        // 本地封面永远先显示：首屏、弱网、网络失败、列表复用回来时都有稳定画面。
        Image(
            painter = painterResource(id = item.localFallbackCoverRes()),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.coverUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            onSuccess = { imageLoaded = true },
            onError = { imageLoaded = false },
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (imageLoaded) 1f else 0f }
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.58f)
                        )
                    )
                )
        )
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        )
    }
}

private fun FeedItem.localFallbackCoverRes(): Int {
    val covers = listOf(
        R.drawable.ad_cover_1,
        R.drawable.ad_cover_2,
        R.drawable.ad_cover_3,
        R.drawable.ad_cover_4,
        R.drawable.ad_cover_5
    )
    return covers[kotlin.math.abs(id.hashCode()) % covers.size]
}

private fun coverBrush(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF1B5E20),
            Color(0xFF00695C),
            Color(0xFF455A64)
        )
    )
}
