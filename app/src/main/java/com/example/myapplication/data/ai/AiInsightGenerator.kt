package com.example.myapplication.data.ai

import com.example.myapplication.data.FeedItem

interface AiInsightGenerator {
    suspend fun generate(item: FeedItem): AiAdInsight
}
