package com.qbili.domain.player

import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CoursePlaybackSnapshot
import com.qbili.domain.model.QualityOption

val courseQualityIds = setOf(16, 32, 64, 74, 80, 112, 116, 120, 125, 126, 127)

fun CourseDetail.adjacentCourseEpisode(episodeId: Long, direction: Int): CourseEpisode? {
    if (direction != -1 && direction != 1) return null
    val index = episodes.indexOfFirst { it.id == episodeId }
    return if (index >= 0) episodes.getOrNull(index + direction) else null
}

fun CoursePlayback.availableCourseQualities(): List<QualityOption> {
    val dash = playback.playurl.dash
    val options = if (dash != null && dash.video.any { it.url.isNotBlank() }) {
        StreamSelector.availableQualities(dash)
    } else {
        val stream = playback.selectStream(playback.quality) ?: return emptyList()
        listOf(QualityOption(stream.quality, stream.label, emptyList()))
    }
    return options.filter { it.qn in courseQualityIds }.map { option ->
        option.copy(label = qualityLabels[option.qn] ?: if (option.qn in setOf(16, 32, 64, 74, 80, 120))
            option.label else "画质 ${option.qn}")
    }
}

fun CoursePlayback.selectCourseStream(quality: Int): SeasonStream? {
    val stream = playback.selectStream(quality) ?: return null
    val label = availableCourseQualities().firstOrNull { it.qn == stream.quality }?.label ?: "画质 ${stream.quality}"
    return stream.copy(label = label)
}

fun CoursePlaybackSnapshot.courseQualityPosition(mediaId: String, oldProgressAllowed: Boolean,
    newProgressAllowed: Boolean, newDurationMs: Long): Long {
    if (!oldProgressAllowed || !newProgressAllowed || this.mediaId != mediaId || !played || completed ||
        durationMs <= 0 || positionMs !in 0 until durationMs) return 0
    val limit = newDurationMs.takeIf { it > 0 }?.let { minOf(it, durationMs) } ?: durationMs
    return positionMs.takeIf { it < limit } ?: 0
}
