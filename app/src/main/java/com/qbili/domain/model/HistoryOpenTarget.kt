package com.qbili.domain.model

import java.net.URI

sealed interface HistoryOpenTarget {
    data object Video : HistoryOpenTarget
    data object Season : HistoryOpenTarget
    data class Article(val id: Long) : HistoryOpenTarget
    data class Opus(val id: String) : HistoryOpenTarget
    data class Live(val roomId: Long) : HistoryOpenTarget
    data class Course(val target: CourseTarget) : HistoryOpenTarget
    data object Unsupported : HistoryOpenTarget
}

fun WatchHistoryItem.openTarget(): HistoryOpenTarget {
    val linkTarget = historyLinkTarget(sourceUri)
    return when (business) {
        "archive" -> if ((aid > 0 || bvid.isNotBlank()) && (bvid.isBlank() || bvid.matches(Regex("BV[0-9A-Za-z]+")))) {
            HistoryOpenTarget.Video
        } else HistoryOpenTarget.Unsupported
        "pgc" -> if (episodeId > 0) HistoryOpenTarget.Season else HistoryOpenTarget.Unsupported
        "article" -> aid.takeIf { it > 0 }?.let { HistoryOpenTarget.Article(it) }
            ?: linkTarget.takeIf { it is HistoryOpenTarget.Article } ?: HistoryOpenTarget.Unsupported
        "article-list" -> cid.takeIf { it > 0 }?.let { HistoryOpenTarget.Article(it) }
            ?: linkTarget.takeIf { it is HistoryOpenTarget.Article } ?: HistoryOpenTarget.Unsupported
        "live" -> aid.takeIf { it > 0 }?.let { HistoryOpenTarget.Live(it) }
            ?: linkTarget.takeIf { it is HistoryOpenTarget.Live } ?: HistoryOpenTarget.Unsupported
        "opus" -> linkTarget.takeIf { it is HistoryOpenTarget.Opus }
            ?: aid.takeIf { it > 0 }?.let { HistoryOpenTarget.Opus(it.toString()) } ?: HistoryOpenTarget.Unsupported
        "dynamic" -> linkTarget.takeIf { it is HistoryOpenTarget.Opus } ?: HistoryOpenTarget.Unsupported
        "cheese", "pugv" -> linkTarget.takeIf { it is HistoryOpenTarget.Course }
            ?: episodeId.takeIf { it > 0 }?.let { HistoryOpenTarget.Course(CourseTarget(episodeId = it)) }
            ?: HistoryOpenTarget.Unsupported
        else -> linkTarget
    }
}

internal fun historyLinkTarget(source: String): HistoryOpenTarget {
    if (source.any { it == '\r' || it == '\n' }) return HistoryOpenTarget.Unsupported
    val raw = source.trim()
    val uri = runCatching { URI(if (raw.startsWith("//")) "https:$raw" else raw) }.getOrNull()
        ?: return HistoryOpenTarget.Unsupported
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("https", "http") || uri.rawUserInfo != null ||
        uri.port !in setOf(-1, if (scheme == "https") 443 else 80)) return HistoryOpenTarget.Unsupported
    val host = uri.host?.lowercase()
    val path = uri.rawPath.orEmpty()
    if (host in setOf("www.bilibili.com", "bilibili.com", "m.bilibili.com")) {
        parseCourseTarget(raw)?.let { return HistoryOpenTarget.Course(it) }
        Regex("/read/cv([1-9][0-9]*)/?").matchEntire(path)?.groupValues?.get(1)
            ?.toLongOrNull()?.let { return HistoryOpenTarget.Article(it) }
        Regex("/opus/([1-9][0-9]{0,29})/?").matchEntire(path)?.groupValues?.get(1)
            ?.let { return HistoryOpenTarget.Opus(it) }
    }
    if (host in setOf("live.bilibili.com", "m.live.bilibili.com")) {
        Regex("/([1-9][0-9]*)/?").matchEntire(path)?.groupValues?.get(1)
            ?.toLongOrNull()?.let { return HistoryOpenTarget.Live(it) }
    }
    return HistoryOpenTarget.Unsupported
}

val WatchHistoryItem.contentLabel: String
    get() = when (openTarget()) {
        HistoryOpenTarget.Video -> "视频"
        HistoryOpenTarget.Season -> "番剧／影视"
        is HistoryOpenTarget.Article -> "专栏"
        is HistoryOpenTarget.Opus -> "图文"
        is HistoryOpenTarget.Live -> "直播"
        is HistoryOpenTarget.Course -> "课程"
        HistoryOpenTarget.Unsupported -> "其他内容"
    }
