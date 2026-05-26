package com.example.myapplication.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.FeedItemType

/**
 * 广告卡片工厂。
 *
 * 信息流通常有很多卡片形态：大图、小图、视频、直播、商品橱窗等。
 * 工厂函数把“根据 type 选择卡片”的逻辑收敛在一个地方，
 * FeedScreen 只负责列表、滚动、曝光和事件分发，不关心每种卡片怎么画。
 */
@Composable
fun AdCardFactory(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when (item.type) {
        FeedItemType.IMAGE_BIG -> BigImageAdCard(item, onLikeClick, onCollectClick, onShareClick, onTagClick, onCardClick, modifier)
        FeedItemType.IMAGE_SMALL -> SmallImageAdCard(item, onLikeClick, onCollectClick, onShareClick, onTagClick, onCardClick, modifier)
        FeedItemType.VIDEO -> VideoAdCard(item, onLikeClick, onCollectClick, onShareClick, onTagClick, onCardClick, modifier)
    }
}

@Composable
private fun BigImageAdCard(
    item: FeedItem,
    onLikeClick: (String) -> Unit,
    onCollectClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier
) {
    BaseCard(item, onLikeClick, onCollectClick, onShareClick, onTagClick, onCardClick, modifier) {
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
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier
) {
    BaseCard(item, onLikeClick, onCollectClick, onShareClick, onTagClick, onCardClick, modifier) {
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
    onTagClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier
) {
    BaseCard(item, onLikeClick, onCollectClick, onShareClick, onTagClick, onCardClick, modifier) {
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

        Box {
            AdCoverImage(
                item = item,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Black.copy(alpha = 0.48f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier
                        .padding(14.dp)
                        .size(32.dp)
                )
            }
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

                // 标签数量不固定时，FlowRow 会自动换行；maxLines 限制最大高度，减少 LazyColumn 滚动中反复测量带来的抖动。
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
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = "评论",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(text = item.commentsCount.toString(), style = MaterialTheme.typography.labelLarge)
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
    var imageState by remember { mutableStateOf(CoverImageState.Loading) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.coverUrl)
                // Coil 默认就有内存/磁盘缓存；这里显式打开，方便在答辩中说明缓存策略。
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .crossfade(true)
                .build(),
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            onLoading = { imageState = CoverImageState.Loading },
            onSuccess = { imageState = CoverImageState.Success },
            onError = { imageState = CoverImageState.Error },
            modifier = Modifier.matchParentSize()
        )

        if (imageState == CoverImageState.Loading) {
            CoverFallback(
                title = "图片加载中",
                modifier = Modifier.matchParentSize()
            )
        }

        if (imageState == CoverImageState.Error) {
            CoverFallback(
                title = "图片加载失败",
                modifier = Modifier.matchParentSize()
            )
        }

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

private enum class CoverImageState {
    Loading,
    Success,
    Error
}

@Composable
private fun CoverFallback(
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                colors = listOf(
                    Color(0xFF1B5E20),
                    Color(0xFF00695C),
                    Color(0xFF455A64)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.86f)
        )
    }
}
