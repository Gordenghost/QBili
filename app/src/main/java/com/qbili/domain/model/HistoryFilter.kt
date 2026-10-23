package com.qbili.domain.model

enum class HistoryContentType(val label: String) {
    ALL("全部"), VIDEO("视频"), PGC("番剧／影视"), ARTICLE("图文／专栏"), LIVE("直播"), COURSE("课程"), OTHER("其他");

    fun matches(item: WatchHistoryItem): Boolean = when (this) {
        ALL -> true
        VIDEO -> item.business == "archive"
        PGC -> item.business == "pgc"
        ARTICLE -> item.business in setOf("article", "article-list", "opus", "dynamic") ||
            item.openTarget().let { it is HistoryOpenTarget.Article || it is HistoryOpenTarget.Opus }
        LIVE -> item.business == "live" || item.openTarget() is HistoryOpenTarget.Live
        COURSE -> item.business in setOf("cheese", "pugv") || item.openTarget() is HistoryOpenTarget.Course
        OTHER -> !VIDEO.matches(item) && !PGC.matches(item) && !ARTICLE.matches(item) && !LIVE.matches(item) && !COURSE.matches(item)
    }
}

fun List<WatchHistoryItem>.filterHistory(
    query: String,
    contentType: HistoryContentType = HistoryContentType.ALL,
): List<WatchHistoryItem> {
    val terms = query.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (terms.isEmpty() && contentType == HistoryContentType.ALL) return this
    return filter { item ->
        val fields = listOf(item.title, item.author, item.part, item.bvid, "av${item.aid}")
        contentType.matches(item) && terms.all { term ->
            fields.any { it.contains(term, ignoreCase = true) }
        }
    }
}
