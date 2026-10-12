package com.qbili.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class VideoProgress(
    val accountMid: Long = 0,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val page: Int,
    val part: String,
    val title: String,
    val cover: String,
    val author: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean = false,
) {
    val key: String get() = "$accountMid:$aid"
    val resumePositionMs: Long get() = if (completed) 0 else positionMs.coerceAtLeast(0)
}

fun List<VideoProgress>.withVideoProgress(progress: VideoProgress): List<VideoProgress> {
    if (progress.aid <= 0 || progress.cid <= 0 || progress.page <= 0 || progress.positionMs < 0) return this
    if (any { it.key == progress.key && it.updatedAt > progress.updatedAt }) return this
    return (filterNot { it.key == progress.key } + progress)
        .sortedByDescending { it.updatedAt }.take(500)
}

data class VideoResume(val cid: Long, val page: Int, val positionMs: Long) {
    fun pageIndex(pages: List<VideoPage>): Int? {
        val matchingCid = pages.indexOfFirst { cid > 0 && it.cid == cid }
        if (matchingCid >= 0) return matchingCid
        if (cid > 0) return null
        return pages.indexOfFirst { it.index == page }.takeIf { it >= 0 }
    }

    fun position(durationSeconds: Int): Long = positionMs.coerceAtLeast(0).let {
        if (durationSeconds > 0 && it >= durationSeconds * 1000L) 0 else it
    }
}

data class HistoryCursor(val max: Long = 0, val viewAt: Long = 0, val business: String = "")
data class HistoryPage(val items: List<WatchHistoryItem>, val cursor: HistoryCursor, val hasMore: Boolean)

data class WatchHistoryItem(
    val id: Long,
    val business: String,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val page: Int,
    val part: String,
    val title: String,
    val cover: String,
    val author: String,
    val viewedAt: Long,
    val positionMs: Long,
    val durationSeconds: Int,
    val completed: Boolean,
    val episodeId: Long = 0,
) {
    val key: String get() = "$business:$id"
    val deleteKey: String get() = "${business}_$id"
    val videoId: String get() = bvid.ifBlank { "av$aid" }
    val resume: VideoResume get() = VideoResume(cid, page, if (completed) 0 else positionMs)
}

fun VideoProgress.toHistoryItem() = WatchHistoryItem(
    id = aid, business = "archive", aid = aid, bvid = bvid, cid = cid, page = page,
    part = part, title = title, cover = cover, author = author, viewedAt = updatedAt / 1000,
    positionMs = positionMs, durationSeconds = (durationMs / 1000).toInt(), completed = completed,
)

fun historyDateLabel(viewedAt: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val date = Instant.ofEpochSecond(viewedAt.coerceAtLeast(0)).atZone(zone).toLocalDate()
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.toString()
    }
}
