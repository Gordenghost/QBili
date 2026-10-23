package com.qbili.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CourseProgress(
    val accountMid: Long,
    val seasonId: Long,
    val episodeId: Long,
    val cid: Long,
    val title: String,
    val cover: String,
    val author: String,
    val episodeTitle: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val completed: Boolean = false,
) {
    val key: String get() = "$accountMid:$seasonId"
    val valid: Boolean get() = accountMid >= 0 && seasonId > 0 && episodeId > 0 && cid > 0 &&
        durationMs > 0 && positionMs in 0..durationMs && updatedAt > 0

    fun resumePosition(detail: CourseDetail): Long {
        if (!valid || completed || seasonId != detail.id || positionMs < 5_000) return 0
        val episode = detail.episodes.firstOrNull { it.id == episodeId && it.cid == cid } ?: return 0
        if (!episode.video || !episode.playable) return 0
        val duration = episode.durationSeconds.takeIf { it > 0 }?.let { minOf(durationMs, it * 1_000L) } ?: durationMs
        return positionMs.takeIf { it < duration - 5_000 } ?: 0
    }
}

fun List<CourseProgress>.withCourseProgress(progress: CourseProgress): List<CourseProgress> {
    if (!progress.valid || any { it.key == progress.key && it.updatedAt > progress.updatedAt }) return this
    return (filterNot { it.key == progress.key } + progress).sortedByDescending { it.updatedAt }.take(100)
}

fun courseMediaId(episodeId: Long, generation: Long) = "course:$episodeId:$generation"

data class CoursePlaybackSnapshot(
    val mediaId: String,
    val positionMs: Long,
    val durationMs: Long,
    val played: Boolean,
    val completed: Boolean = false,
)
