package com.example.myapplication.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.FeedItem
import com.example.myapplication.data.ai.HybridSearchIntentParser
import com.example.myapplication.data.ai.SearchIntent
import com.example.myapplication.data.ai.SearchIntentSource
import com.example.myapplication.tracking.AdTracker
import com.example.myapplication.tracking.ClickEvent
import com.example.myapplication.ui.components.AdCardFactory
import com.example.myapplication.ui.share.shareFeedItem
import com.example.myapplication.viewmodel.FeedViewModel
import kotlinx.coroutines.launch

private data class SearchMessage(
    val id: Long,
    val query: String,
    val resultCount: Int,
    val intent: SearchIntent
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: FeedViewModel,
    tracker: AdTracker,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allItems by viewModel.allFeedItems.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searchIntentParser = remember { HybridSearchIntentParser() }
    var query by remember { mutableStateOf("") }
    var activeQuery by remember { mutableStateOf("") }
    var activeIntent by remember { mutableStateOf<SearchIntent?>(null) }
    var isParsing by remember { mutableStateOf(false) }
    val messages = remember { mutableStateListOf<SearchMessage>() }
    val results = remember(activeQuery, activeIntent, allItems) {
        val intent = activeIntent
        if (activeQuery.isBlank() || intent == null) {
            emptyList()
        } else {
            allItems.matchIntent(activeQuery, intent)
        }
    }
    val animatedCount by animateIntAsState(targetValue = results.size, label = "searchResultCount")

    fun submitSearch(text: String = query) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        query = ""
        isParsing = true
        scope.launch {
            val intent = searchIntentParser.parse(trimmed)
            val matchedItems = allItems.matchIntent(trimmed, intent)
            activeQuery = trimmed
            activeIntent = intent
            messages.add(
                0,
                SearchMessage(
                    id = System.nanoTime(),
                    query = trimmed,
                    resultCount = matchedItems.size,
                    intent = intent
                )
            )
            tracker.trackClick(ClickEvent("search", "query:${intent.source}:$trimmed"))
            isParsing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话式搜索") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "search_intro", contentType = "intro") {
                    SearchIntro(
                        activeQuery = activeQuery,
                        activeIntent = activeIntent,
                        resultCount = animatedCount,
                        isParsing = isParsing,
                        onQuickSearch = ::submitSearch
                    )
                }

                items(
                    items = messages,
                    key = { message -> message.id },
                    contentType = { "message" }
                ) { message ->
                    SearchMessageBubble(message = message)
                }

                items(
                    items = results,
                    key = { item -> "search_${item.id}" },
                    contentType = { item -> item.type }
                ) { item ->
                    AdCardFactory(
                        item = item,
                        onLikeClick = { id ->
                            viewModel.toggleLike(id)
                            tracker.trackClick(ClickEvent(id, "search_like"))
                        },
                        onCollectClick = { id ->
                            viewModel.toggleCollect(id)
                            tracker.trackClick(ClickEvent(id, "search_collect"))
                        },
                        onShareClick = {
                            shareFeedItem(context, item)
                            tracker.trackClick(ClickEvent(item.id, "search_share"))
                        },
                        onCommentClick = { id ->
                            tracker.trackClick(ClickEvent(id, "search_comment_entry"))
                            onNavigateToDetail(id)
                        },
                        onTagClick = { tag ->
                            submitSearch(tag)
                            tracker.trackClick(ClickEvent(item.id, "search_tag:$tag"))
                        },
                        onCardClick = { id ->
                            tracker.trackClick(ClickEvent(id, "search_card"))
                            onNavigateToDetail(id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SearchInputBar(
                value = query,
                onValueChange = { query = it },
                onSubmit = { submitSearch() }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SearchIntro(
    activeQuery: String,
    activeIntent: SearchIntent?,
    resultCount: Int,
    isParsing: Boolean,
    onQuickSearch: (String) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = when {
                    isParsing -> "Qwen 正在解析搜索意图"
                    activeQuery.isBlank() -> "描述你想看的广告"
                    else -> "正在搜索：$activeQuery"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isParsing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = when {
                        isParsing -> "正在把自然语言转换成关键词、标签、频道和媒体类型。"
                        activeQuery.isBlank() -> "可输入“适合学生党的性价比运动装备”“附近周末优惠”“视频创意”等自然语言。"
                        else -> "找到 $resultCount 条匹配广告。解析来源：${activeIntent?.source.displayName()}；解析词：${activeIntent?.terms.orEmpty().joinToString("、")}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("学生党 性价比", "附近优惠", "视频创意").forEach { sample ->
                    AssistChip(
                        onClick = { onQuickSearch(sample) },
                        label = { Text(sample) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchMessageBubble(message: SearchMessage) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically()
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "我想看：${message.query}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "系统已匹配 ${message.resultCount} 条广告",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "解析来源：${message.intent.source.displayName()}；解析词：${message.intent.terms.joinToString("、")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入自然语言需求") },
                maxLines = 2
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSubmit, enabled = value.isNotBlank()) {
                Icon(imageVector = Icons.Default.Send, contentDescription = "搜索")
            }
        }
    }
}

private fun List<FeedItem>.matchIntent(query: String, intent: SearchIntent): List<FeedItem> {
    val tokens = (intent.terms + query
        .lowercase()
        .split(" ", "，", ",", "。", "、"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    if (tokens.isEmpty()) return emptyList()

    return mapNotNull { item ->
        val searchableText = buildString {
            append(item.title.lowercase())
            append(' ')
            append(item.description.lowercase())
            append(' ')
            append(item.aiSummary.lowercase())
            append(' ')
            append(item.aiTags.joinToString(" ").lowercase())
            append(' ')
            append(item.category.title.lowercase())
            append(' ')
            append(item.type.name.lowercase())
        }
        val score = tokens.count { token -> searchableText.contains(token) } +
            item.aiTags.count { tag -> tokens.any { token -> tag.contains(token, ignoreCase = true) } }
        if (score > 0) item to score else null
    }
        .sortedWith(compareByDescending<Pair<FeedItem, Int>> { it.second }.thenBy { it.first.id })
        .map { it.first }
}

private fun SearchIntentSource.displayName(): String {
    return when (this) {
        SearchIntentSource.QWEN -> "Qwen"
        SearchIntentSource.LOCAL_RULE -> "本地规则"
    }
}
