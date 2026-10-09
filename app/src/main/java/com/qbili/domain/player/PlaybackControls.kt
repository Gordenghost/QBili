package com.qbili.domain.player

import androidx.media3.common.Player

fun Player.togglePlayback() {
    when {
        playbackState == Player.STATE_ENDED -> {
            seekTo(0L)
            play()
        }
        playWhenReady -> pause()
        else -> play()
    }
}
