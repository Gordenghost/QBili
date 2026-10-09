package com.qbili.ui.screen.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.qbili.core.formatDuration
import kotlinx.coroutines.delay

@Composable
internal fun BoxScope.VideoPlayerControls(
    player: Player,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onInteraction: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onPressedChange: (Boolean) -> Unit,
) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    LaunchedEffect(pressed) {
        onPressedChange(pressed)
        if (pressed) onInteraction()
    }
    DisposableEffect(Unit) {
        onDispose { onPressedChange(false) }
    }
    val durationMillis = player.duration.coerceAtLeast(0L)
    var positionMillis by remember(player) { mutableLongStateOf(0L) }
    var scrubMillis by remember(player) { mutableLongStateOf(0L) }
    var scrubbing by remember(player) { mutableStateOf(false) }

    // 浮层可见时才更新进度，隐藏后不再轮询播放器。
    LaunchedEffect(player) {
        while (true) {
            positionMillis = player.currentPosition.coerceAtLeast(0L)
            delay(250)
        }
    }

    Row(
        modifier = Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onSeekBy(-10_000L); onInteraction() },
            enabled = durationMillis > 0L,
            modifier = Modifier.size(48.dp),
            interactionSource = interactions,
        ) {
            Box(Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Replay10, contentDescription = "后退 10 秒", tint = Color.White,
                    modifier = Modifier.size(22.dp))
            }
        }
        IconButton(
            onClick = { onTogglePlay(); onInteraction() },
            modifier = Modifier.size(48.dp),
            interactionSource = interactions,
        ) {
            Box(Modifier.size(42.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        IconButton(
            onClick = { onSeekBy(10_000L); onInteraction() },
            enabled = durationMillis > 0L,
            modifier = Modifier.size(48.dp),
            interactionSource = interactions,
        ) {
            Box(Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Forward10, contentDescription = "前进 10 秒", tint = Color.White,
                    modifier = Modifier.size(22.dp))
            }
        }
    }

    val displayedMillis = (if (scrubbing) scrubMillis else positionMillis).coerceIn(0L, durationMillis)
    val timeStyle = MaterialTheme.typography.labelSmall.copy(
        shadow = Shadow(Color.Black, Offset(0f, 1f), 3f),
    )
    Row(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            formatDuration((displayedMillis / 1000).toInt()),
            style = timeStyle, color = Color.White,
        )
        // 自绘细轨道保留点击和拖动跳转，但不会被 Slider 的默认粗轨道撑高。
        Canvas(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp).height(32.dp)
                .semantics {
                    contentDescription = "播放进度"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        if (durationMillis > 0L) displayedMillis.toFloat() / durationMillis else 0f,
                        0f..1f,
                    )
                    if (durationMillis > 0L) setProgress { fraction ->
                        val target = seekPositionMillis(fraction, 1f, durationMillis)
                        onSeekTo(target)
                        positionMillis = target
                        onInteraction()
                        true
                    }
                }
                .pointerInput(durationMillis) {
                    if (durationMillis <= 0L) return@pointerInput
                    detectTapGestures(onTap = { offset ->
                        val target = seekPositionMillis(offset.x, size.width.toFloat(), durationMillis)
                        onSeekTo(target)
                        positionMillis = target
                        onInteraction()
                    })
                }
                .pointerInput(durationMillis) {
                    if (durationMillis <= 0L) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            scrubMillis = seekPositionMillis(offset.x, size.width.toFloat(), durationMillis)
                            scrubbing = true
                            onScrubbingChange(true)
                        },
                        onDrag = { change, _ ->
                            scrubMillis = seekPositionMillis(change.position.x, size.width.toFloat(), durationMillis)
                            change.consume()
                        },
                        onDragEnd = {
                            onSeekTo(scrubMillis)
                            positionMillis = scrubMillis
                            scrubbing = false
                            onScrubbingChange(false)
                            onInteraction()
                        },
                        onDragCancel = {
                            scrubbing = false
                            onScrubbingChange(false)
                        },
                    )
                },
        ) {
            val progress = if (durationMillis > 0L) displayedMillis.toFloat() / durationMillis else 0f
            val centerY = size.height / 2f
            drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, centerY),
                Offset(size.width, centerY), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White, Offset(0f, centerY), Offset(size.width * progress, centerY),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            if (durationMillis > 0L) drawCircle(Color.White, radius = 4.dp.toPx(),
                center = Offset(size.width * progress, centerY))
        }
        Text(formatDuration((durationMillis / 1000).toInt()),
            style = timeStyle, color = Color.White)
    }
}

internal fun seekPositionMillis(position: Float, width: Float, durationMillis: Long): Long {
    if (width <= 0f || durationMillis <= 0L) return 0L
    return ((position / width).coerceIn(0f, 1f) * durationMillis).toLong()
}
