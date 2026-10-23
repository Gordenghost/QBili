package com.qbili.ui.screen.course

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.ExoPlayer
import com.qbili.di.AppContainer

class CoursePlaybackViewModel(context: Context, container: AppContainer) : ViewModel() {
    val player = ExoPlayer.Builder(context.applicationContext).build()
    val course = CourseViewModel(container.sessionManager.loginCookies, container.courseRepository::detail,
        container.courseRepository::playurl, stopPlayback = { player.pause(); player.stop(); player.clearMediaItems() },
        scope = viewModelScope)

    override fun onCleared() {
        course.close()
        player.release()
        super.onCleared()
    }
}
