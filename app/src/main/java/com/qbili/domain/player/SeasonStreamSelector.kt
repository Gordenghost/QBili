package com.qbili.domain.player

import com.qbili.domain.model.DurlResult
import com.qbili.domain.model.PlayableStream
import com.qbili.domain.model.SeasonPlayback
import com.qbili.domain.model.VideoCodec
import com.qbili.domain.model.VideoQuality

data class SeasonStream(
    val dash: PlayableStream?,
    val segments: List<DurlResult>,
    val quality: Int,
    val label: String,
)

fun SeasonPlayback.selectStream(quality: Int, codec: VideoCodec? = null): SeasonStream? {
    val dash = playurl.dash?.let { StreamSelector.select(it, quality, codec) }
    if (dash != null) return SeasonStream(dash, emptyList(), dash.quality, dash.qualityLabel)
    val segments = playurl.durl.filter { it.url.isNotBlank() }.sortedBy { it.order }
    if (segments.isEmpty()) return null
    val label = VideoQuality.entries.firstOrNull { it.qn == this.quality }?.label ?: "默认画质"
    return SeasonStream(null, segments, this.quality, label)
}
