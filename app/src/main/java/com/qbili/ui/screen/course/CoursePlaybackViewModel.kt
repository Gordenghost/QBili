package com.qbili.ui.screen.course

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.Player
import com.qbili.domain.model.CoursePlaybackSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.qbili.di.AppContainer

class CoursePlaybackViewModel(context: Context, container: AppContainer) : ViewModel() {
    val player = ExoPlayer.Builder(context.applicationContext).build()
    private var playedMediaId: String? = null
    val course = CourseViewModel(container.sessionManager.loginCookies, container.courseRepository::detail,
        container.courseRepository::playurl, stopPlayback = {
            playedMediaId = null
            player.pause(); player.stop(); player.clearMediaItems()
        }, scope = viewModelScope, progressEntries = container.courseProgressStore.entries,
        saveProgress = { progress -> container.saveCourseProgress(progress) { courseSaveFailed(progress.accountMid) } },
        playbackSnapshot = {
            player.currentMediaItem?.mediaId?.let { id -> CoursePlaybackSnapshot(id, player.currentPosition,
                player.duration, playedMediaId == id, player.playbackState == Player.STATE_ENDED) }
        }, seekPlayback = { id, position ->
            if (player.currentMediaItem?.mediaId == id && player.playbackState == Player.STATE_READY &&
                player.duration > position) {
                player.seekTo(position)
                player.play()
                true
            } else false
        })

    private fun courseSaveFailed(accountMid: Long) { viewModelScope.launch { course.progressSaveFailed(accountMid) } }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player.isPlaying) playedMediaId = player.currentMediaItem?.mediaId
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) || events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                if (!player.isPlaying) course.persistPlayback()
            }
        }
    }

    init {
        player.addListener(listener)
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                if (player.isPlaying) course.persistPlayback()
            }
        }
    }

    override fun onCleared() {
        course.close()
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }
}
