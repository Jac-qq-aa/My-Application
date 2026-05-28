package com.example.myapplication.data.ai

class HybridSearchIntentParser(
    private val remoteParser: SearchIntentParser = OllamaQwenSearchIntentParser(),
    private val fallbackParser: SearchIntentParser = LocalRuleSearchIntentParser()
) : SearchIntentParser {
    private var remoteDisabled = false

    override suspend fun parse(query: String): SearchIntent {
        if (remoteDisabled) {
            return fallbackParser.parse(query)
        }

        return runCatching { remoteParser.parse(query) }
            .getOrElse {
                remoteDisabled = true
                fallbackParser.parse(query)
            }
    }
}
