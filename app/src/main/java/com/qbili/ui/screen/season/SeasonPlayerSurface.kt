package com.qbili.ui.screen.season

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.qbili.ui.screen.video.DanmakuOverlay
import com.qbili.ui.screen.video.PlayerGestureLayer
import com.qbili.ui.screen.video.VideoPlayerControls
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
internal fun SeasonPlayerSurface(
    viewModel: SeasonViewModel,
    state: SeasonViewModel.UiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(visible, interaction, scrubbing, pressed) {
        if (visible && !scrubbing && !pressed) {
            delay(2_500)
            visible = false
        }
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> PlayerView(context).apply { player = viewModel.player; useController = false } },
        )
        DanmakuOverlay(
            danmaku = state.danmaku, settings = state.danmakuSettings,
            isPlaying = state.playing, positionMillis = { viewModel.player.currentPosition },
            fullscreen = state.fullscreen, modifier = Modifier.fillMaxSize().padding(bottom = 40.dp),
        )
        PlayerGestureLayer(
            durationMillis = viewModel.player.duration,
            positionMillis = { viewModel.player.currentPosition },
            onSingleTap = { visible = !visible; interaction += 1 },
            onTogglePlay = { viewModel.togglePlay(); visible = true; interaction += 1 },
            onSeekTo = { viewModel.player.seekTo(it) },
            onGestureStart = { visible = true; interaction += 1 },
            onSpeedBoostStart = viewModel::startBoost,
            onSpeedBoostEnd = viewModel::stopBoost,
            modifier = Modifier.fillMaxSize(),
        )
        if (visible) {
            VideoPlayerControls(
                player = viewModel.player, isPlaying = state.playbackRequested,
                onTogglePlay = viewModel::togglePlay, onSeekBy = viewModel::seekBy,
                onSeekTo = { viewModel.player.seekTo(it) }, onInteraction = { interaction += 1 },
                onScrubbingChange = { scrubbing = it }, onPressedChange = { pressed = it },
            )
            Row(Modifier.align(Alignment.TopStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White)
                }
                Text(
                    state.episode?.title ?: state.detail?.title ?: "番剧 / 影视",
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.setFullscreen(!state.fullscreen) }) {
                    Icon(if (state.fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        if (state.fullscreen) "退出全屏" else "全屏", tint = Color.White)
                }
            }
        }
        if (state.loading || state.switching) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else if (state.error != null && state.fullscreen) {
            Text(state.error, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(32.dp))
        } else if (state.stream == null) {
            Text("暂无可播放画面", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        if (state.preview) {
            Text("试看", color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 44.dp))
        }
    }
}
