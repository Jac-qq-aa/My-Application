package com.example.myapplication.data

data class FeedComment(
    val id: String,
    val itemId: String,
    val author: String,
    val content: String,
    val timestampLabel: String
)
