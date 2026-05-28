package com.example.myapplication.data.ai

interface SearchIntentParser {
    suspend fun parse(query: String): SearchIntent
}
