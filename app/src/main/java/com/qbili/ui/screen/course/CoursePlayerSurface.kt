package com.qbili.ui.screen.course

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.qbili.ui.screen.video.PlayerGestureLayer
import com.qbili.ui.screen.video.VideoPlayerControls
import com.qbili.R
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun CoursePlayerSurface(player: ExoPlayer, state: CourseViewModel.UiState,
    onTogglePlay: () -> Unit, onSeekBy: (Long) -> Unit, onFullscreen: () -> Unit,
    onBack: () -> Unit, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var speedBeforeBoost by remember { mutableStateOf(1f) }
    LaunchedEffect(visible, interaction, scrubbing, pressed) {
        if (visible && !scrubbing && !pressed) { delay(2_500); visible = false }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
            (LayoutInflater.from(context).inflate(R.layout.video_player_view, null, false) as PlayerView)
                .apply { this.player = player; useController = false }
        }, update = { it.player = player }, onRelease = { it.player = null })
        if (state.stream != null) {
            PlayerGestureLayer(durationMillis = player.duration, positionMillis = { player.currentPosition },
                onSingleTap = { visible = !visible; interaction++ },
                onTogglePlay = { onTogglePlay(); visible = true; interaction++ },
                onSeekTo = { player.seekTo(it) }, onGestureStart = { visible = true; interaction++ },
                onSpeedBoostStart = { speedBeforeBoost = player.playbackParameters.speed; player.setPlaybackSpeed(2f) },
                onSpeedBoostEnd = { player.setPlaybackSpeed(speedBeforeBoost) }, modifier = Modifier.fillMaxSize())
            if (visible) VideoPlayerControls(player,
                isPlaying = state.requested && player.playbackState != Player.STATE_ENDED,
                onTogglePlay = onTogglePlay, onSeekBy = onSeekBy, onSeekTo = { player.seekTo(it) },
                onInteraction = { interaction++ }, onScrubbingChange = { scrubbing = it }, onPressedChange = { pressed = it })
        }
        if (visible || state.stream == null) Row(Modifier.align(Alignment.TopStart).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
            Text(state.episode?.title ?: state.detail?.title ?: "课程", color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = onFullscreen) { Icon(if (state.fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                if (state.fullscreen) "退出全屏" else "全屏", tint = Color.White) }
        }
        if (state.switching || state.buffering) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        else if (state.stream == null) Text(if (state.playError != null) "播放失败，请查看下方提示" else "在下方目录选择可播放课时",
            color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}
