package com.example.myapplication.data.ai

data class SearchIntent(
    val keywords: List<String>,
    val tags: List<String>,
    val category: String?,
    val mediaType: String?,
    val source: SearchIntentSource
) {
    val terms: List<String>
        get() = (keywords + tags + listOfNotNull(category, mediaType))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

enum class SearchIntentSource {
    QWEN,
    LOCAL_RULE
}
