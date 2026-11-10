package com.qbili.ui.screen.course

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.qbili.data.remote.ApiConstants
import com.qbili.domain.player.SeasonStream

@OptIn(UnstableApi::class)
internal fun buildCourseMediaSource(stream: SeasonStream, episodeId: Long, mediaId: String): MediaSource {
    val factory = DefaultHttpDataSource.Factory().setUserAgent(ApiConstants.USER_AGENT)
        .setDefaultRequestProperties(mapOf("Referer" to "https://www.bilibili.com/cheese/play/ep$episodeId"))
        .setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(15_000).setReadTimeoutMs(20_000)
    val dash = stream.dash
    if (dash != null) {
        val video = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.Builder().setUri(dash.videoUrl).setMediaId(mediaId).build())
        val audio = dash.audioUrl?.let { ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(it)) }
        return if (audio == null) video else MergingMediaSource(video, audio)
    }
    val sources = stream.segments.map { DefaultMediaSourceFactory(factory).createMediaSource(MediaItem.Builder().setUri(it.url).setMediaId(mediaId).build()) }
    if (sources.size == 1) return sources.single()
    return ConcatenatingMediaSource2.Builder().apply {
        setMediaItem(MediaItem.Builder().setMediaId(mediaId).build())
        sources.forEachIndexed { index, source ->
            val duration = stream.segments[index].length
            if (duration > 0) add(source, duration) else add(source)
        }
    }.build()
}
