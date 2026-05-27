package com.example.myapplication.ui.share

import android.content.Context
import android.content.Intent
import com.example.myapplication.data.FeedItem

fun shareFeedItem(context: Context, item: FeedItem) {
    val shareText = buildString {
        appendLine(item.title)
        appendLine()
        appendLine(item.aiSummary)
        appendLine()
        appendLine(item.coverUrl)
    }
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(
        Intent.createChooser(sendIntent, "分享广告")
    )
}
