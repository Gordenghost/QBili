package com.qbili.domain.player

import androidx.media3.common.Player

enum class VideoCompletionMode(val title: String, val description: String) {
    STOP("播完暂停", "默认模式，播放结束后停留在当前 P。"),
    REPEAT_PAGE("单 P 循环", "当前 P 播完后从头播放，不切换其他 P。"),
    NEXT_PAGE("自动下一 P", "当前 P 播完后播放下一 P，最后一 P 播完暂停。"),
    ;

    companion object {
        fun fromStored(value: String?): VideoCompletionMode = entries.firstOrNull { it.name == value } ?: STOP
    }
}

sealed interface VideoCompletionAction {
    data object Stop : VideoCompletionAction
    data object Replay : VideoCompletionAction
    data class Next(val index: Int) : VideoCompletionAction
}

fun videoCompletionAction(
    mode: VideoCompletionMode,
    pageIndex: Int,
    pageCount: Int,
    allowAutomaticPlayback: Boolean,
    sleepAtEnd: Boolean,
): VideoCompletionAction {
    if (!allowAutomaticPlayback || sleepAtEnd || pageIndex !in 0 until pageCount) return VideoCompletionAction.Stop
    return when (mode) {
        VideoCompletionMode.STOP -> VideoCompletionAction.Stop
        VideoCompletionMode.REPEAT_PAGE -> VideoCompletionAction.Replay
        VideoCompletionMode.NEXT_PAGE -> adjacentVideoPage(pageIndex, pageCount, 1)
            ?.let(VideoCompletionAction::Next) ?: VideoCompletionAction.Stop
    }
}

class VideoCompletionController {
    private var mediaId: String? = null
    private var played = false
    private var handled = false

    fun reset(id: String?) {
        mediaId = id
        played = false
        handled = false
    }

    fun onPlaying(id: String?, playing: Boolean) {
        if (id != null && id == mediaId && playing) {
            played = true
            handled = false
        }
    }

    fun onEnded(
        player: Player,
        mode: VideoCompletionMode,
        pageIndex: Int,
        pageCount: Int,
        allowAutomaticPlayback: Boolean,
        sleepAtEnd: Boolean,
        onNext: (Int) -> Unit,
        onStop: () -> Unit,
    ): Boolean {
        if (mediaId == null || player.currentMediaItem?.mediaId != mediaId ||
            player.playbackState != Player.STATE_ENDED || handled) return false
        handled = true
        when (val action = videoCompletionAction(mode, pageIndex, pageCount,
            allowAutomaticPlayback && played && player.playWhenReady, sleepAtEnd)) {
            VideoCompletionAction.Stop -> { player.pause(); onStop() }
            VideoCompletionAction.Replay -> { player.seekTo(0L); player.play() }
            is VideoCompletionAction.Next -> onNext(action.index)
        }
        return true
    }
}
