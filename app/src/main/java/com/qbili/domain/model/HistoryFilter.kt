package com.qbili.domain.model

enum class HistoryContentType(val label: String) {
    ALL("全部"), VIDEO("视频"), PGC("番剧／影视"), OTHER("其他");

    fun matches(item: WatchHistoryItem): Boolean = when (this) {
        ALL -> true
        VIDEO -> item.business == "archive"
        PGC -> item.business == "pgc"
        OTHER -> item.business != "archive" && item.business != "pgc"
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
