package com.example.myapplication.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.example.myapplication.R
import com.example.myapplication.data.FeedComment
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.FeedItemType
import com.example.myapplication.tracking.AdTracker
import com.example.myapplication.tracking.ClickEvent
import com.example.myapplication.ui.components.AdVideoMedia
import com.example.myapplication.ui.share.shareFeedItem
import com.example.myapplication.viewmodel.FeedViewModel

/**
 * 广告详情页。
 *
 * 详情页不维护自己的点赞/收藏副本，而是按 itemId 订阅 FeedViewModel 中的同一份数据。
 * 因此这里点击点赞/收藏后，列表页对应卡片会同步变化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    itemId: String,
    viewModel: FeedViewModel,
    tracker: AdTracker,
    onBack: () -> Unit
) {
    val allItems by viewModel.allFeedItems.collectAsState()
    val commentsByItemId by viewModel.comments.collectAsState()
    val item = allItems.firstOrNull { it.id == itemId }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("广告详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        val currentItem = item
        if (currentItem == null) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("广告不存在或已被刷新")
            }
            return@Scaffold
        }

        DetailContent(
            item = currentItem,
            comments = commentsByItemId[currentItem.id].orEmpty(),
            onLikeClick = {
                viewModel.toggleLike(currentItem.id)
                tracker.trackClick(ClickEvent(currentItem.id, "like"))
            },
            onCollectClick = {
                viewModel.toggleCollect(currentItem.id)
                tracker.trackClick(ClickEvent(currentItem.id, "collect"))
            },
            onShareClick = {
                shareFeedItem(context, currentItem)
                tracker.trackClick(ClickEvent(currentItem.id, "share"))
            },
            onCommentSubmit = { content ->
                viewModel.addComment(currentItem.id, content)
                tracker.trackClick(ClickEvent(currentItem.id, "comment"))
            },
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    item: FeedItem,
    comments: List<FeedComment>,
    onLikeClick: () -> Unit,
    onCollectClick: () -> Unit,
    onShareClick: () -> Unit,
    onCommentSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        DetailMedia(item = item)

        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = item.aiSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item.aiTags.forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = onLikeClick, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (item.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.isLiked) "已点赞" else "点赞")
            }
            Button(onClick = onCollectClick, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (item.isCollected) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (item.isCollected) "已收藏" else "收藏")
            }
            IconButton(onClick = onShareClick) {
                Icon(imageVector = Icons.Default.Share, contentDescription = "分享")
            }
        }

        CommentSection(
            item = item,
            comments = comments,
            onCommentSubmit = onCommentSubmit
        )
    }
}

@Composable
private fun CommentSection(
    item: FeedItem,
    comments: List<FeedComment>,
    onCommentSubmit: (String) -> Unit
) {
    var input by remember(item.id) { mutableStateOf("") }
    val previewComments = comments + defaultComments(item)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "评论 ${item.commentsCount}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("写下你的想法") },
                maxLines = 3
            )
            Button(
                onClick = {
                    onCommentSubmit(input)
                    input = ""
                },
                enabled = input.isNotBlank()
            ) {
                Text("发布")
            }
        }
        previewComments.take(4).forEach { comment ->
            CommentItem(comment = comment)
        }
    }
}

@Composable
private fun CommentItem(comment: FeedComment) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = comment.author.take(1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = comment.author,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = comment.timestampLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = comment.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun defaultComments(item: FeedItem): List<FeedComment> {
    return listOf(
        FeedComment(
            id = "${item.id}_default_1",
            itemId = item.id,
            author = "用户A",
            content = "封面和标题都很清楚，第一眼就能看懂卖点。",
            timestampLabel = "2分钟前"
        ),
        FeedComment(
            id = "${item.id}_default_2",
            itemId = item.id,
            author = "用户B",
            content = "这个素材适合放在信息流前几屏，点击欲望还不错。",
            timestampLabel = "8分钟前"
        )
    )
}

@Composable
private fun DetailMedia(item: FeedItem) {
    if (item.type == FeedItemType.VIDEO) {
        AdVideoMedia(
            item = item,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
        return
    }

    val context = LocalContext.current
    var imageLoaded by remember(item.id) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (item.type == FeedItemType.IMAGE_SMALL) 4f / 3f else 16f / 9f)
            .background(detailCoverBrush(), shape = RoundedCornerShape(8.dp))
    ) {
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

private fun detailCoverBrush(): Brush {
    return Brush.linearGradient(
        colors = listOf(
            Color(0xFF004D40),
            Color(0xFF1565C0),
            Color(0xFF37474F)
        )
    )
}
