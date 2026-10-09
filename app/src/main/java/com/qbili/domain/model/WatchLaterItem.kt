package com.qbili.domain.model

import com.qbili.core.formatDuration

data class WatchLaterItem(
    val video: VideoItem,
    val progressSeconds: Int,
    val addedAt: Long,
) {
    val progressLabel: String
        get() = when {
            progressSeconds < 0 || video.durationSeconds > 0 && progressSeconds >= video.durationSeconds -> "已看完"
            progressSeconds == 0 -> "未观看"
            else -> "看到 ${formatDuration(progressSeconds)}"
        }
}
