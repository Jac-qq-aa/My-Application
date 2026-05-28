package com.example.myapplication.data.ai

class LocalRuleSearchIntentParser : SearchIntentParser {
    override suspend fun parse(query: String): SearchIntent {
        val tokens = query
            .split(" ", "，", ",", "。", "、")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val inferredTags = buildList {
            if (query.contains("学生")) add("学生党")
            if (query.contains("性价比") || query.contains("便宜") || query.contains("优惠")) add("性价比")
            if (query.contains("附近") || query.contains("本地") || query.contains("周末")) add("附近优惠")
            if (query.contains("运动") || query.contains("装备")) add("运动")
            if (query.contains("视频") || query.contains("创意")) add("视频创意")
        }

        val category = when {
            query.contains("电商") || query.contains("爆款") || query.contains("包邮") -> "电商"
            query.contains("附近") || query.contains("本地") || query.contains("到店") -> "本地"
            query.contains("精选") || query.contains("推荐") -> "精选"
            else -> null
        }
        val mediaType = when {
            query.contains("视频") -> "video"
            query.contains("大图") -> "image_big"
            query.contains("小图") || query.contains("图文") -> "image_small"
            else -> null
        }

        return SearchIntent(
            keywords = tokens,
            tags = inferredTags,
            category = category,
            mediaType = mediaType,
            source = SearchIntentSource.LOCAL_RULE
        )
    }
}
