package com.qbili.domain.model

import kotlinx.serialization.Serializable

data class SeasonDetail(
    val id: Long,
    val title: String,
    val cover: String,
    val description: String,
    val typeName: String,
    val progress: String,
    val score: Double,
    val styles: List<String>,
    val areas: List<String>,
    val followed: Boolean,
    val areaLimited: Boolean,
    val sections: List<SeasonSection>,
) {
    val episodes: List<SeasonEpisode> get() = sections.flatMap { it.episodes }.distinctBy { it.id }
}

data class SeasonSection(val title: String, val episodes: List<SeasonEpisode>)

data class SeasonEpisode(
    val id: Long,
    val aid: Long,
    val cid: Long,
    val title: String,
    val cover: String,
    val durationSeconds: Int,
    val badge: String,
    val status: Int,
    val areaLimited: Boolean,
) {
    val accessHint: String
        get() = when {
            areaLimited -> "当前地区可能无法播放"
            badge.isNotBlank() -> badge
            status == 13 -> "大会员"
            status == 12 -> "付费"
            else -> ""
        }
}

data class SeasonPlayback(val playurl: PlayurlResult, val preview: Boolean, val quality: Int)

@Serializable
data class SeasonProgress(
    val episodeId: Long,
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAt: Long,
)

@Serializable
data class SeasonProgressEntry(val seasonId: Long, val progress: SeasonProgress)

fun List<SeasonProgressEntry>.withProgress(seasonId: Long, progress: SeasonProgress): List<SeasonProgressEntry> {
    if (seasonId <= 0 || progress.episodeId <= 0 || progress.positionMillis < 0) return this
    val existing = firstOrNull { it.seasonId == seasonId }
    if (existing != null && existing.progress.updatedAt > progress.updatedAt) return this
    return (listOf(SeasonProgressEntry(seasonId, progress)) + filterNot { it.seasonId == seasonId })
        .sortedByDescending { it.progress.updatedAt }.take(100)
}

fun SeasonProgress.resumePosition(episode: SeasonEpisode): Long {
    if (episodeId != episode.id || positionMillis < 5_000L) return 0L
    val duration = durationMillis.takeIf { it > 0 } ?: episode.durationSeconds * 1_000L
    if (duration > 0 && positionMillis >= duration - 5_000L) return 0L
    return positionMillis.coerceAtLeast(0L)
}
