package com.qbili.ui.screen.video

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.BrightnessHigh
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.qbili.core.formatDuration
import kotlin.math.abs
import kotlin.math.roundToInt

/** 一次手势调节的类型，决定拖动过程中改什么 */
private enum class GestureMode { BRIGHTNESS, VOLUME, SEEK }

private data class Indicator(val icon: IndicatorIcon, val text: String)

private enum class IndicatorIcon { BRIGHTNESS, VOLUME, VOLUME_MUTE, SEEK, SPEED }

/**
 * 播放器手势层。
 *
 * 交互约定与主流播放器一致：
 * - 左半屏上下滑 = 亮度（只改本窗口，不动系统设置）
 * - 右半屏上下滑 = 音量
 * - 左右横滑 = 快进快退（松手才真正 seek，拖动中只显示预览）
 * - 双击 = 播放/暂停
 * - 单击 = 显示/隐藏控制按钮
 * - 长按 = 临时倍速，松手还原
 *
 * 轴向在第一次明显位移时判定并锁定，避免斜着滑时亮度和进度同时变化。
 */
@Composable
fun PlayerGestureLayer(
    durationMillis: Long,
    positionMillis: () -> Long,
    onSingleTap: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onGestureStart: () -> Unit,
    onSpeedBoostStart: () -> Unit,
    onSpeedBoostEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val maxVolume = remember {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }

    var indicator by remember { mutableStateOf<Indicator?>(null) }
    var mode by remember { mutableStateOf<GestureMode?>(null) }
    var startX by remember { mutableStateOf(0f) }
    var seekTarget by remember { mutableStateOf(0L) }
    var boosting by remember { mutableStateOf(false) }
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnTogglePlay by rememberUpdatedState(onTogglePlay)
    val currentOnGestureStart by rememberUpdatedState(onGestureStart)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnSingleTap() },
                    onDoubleTap = { currentOnTogglePlay() },
                    onLongPress = {
                        currentOnGestureStart()
                        boosting = true
                        onSpeedBoostStart()
                        indicator = Indicator(IndicatorIcon.SPEED, "2.0× 快进中")
                    },
                    onPress = {
                        // onLongPress 没有「松手」回调，只能在这里等释放
                        tryAwaitRelease()
                        if (boosting) {
                            boosting = false
                            onSpeedBoostEnd()
                            indicator = null
                        }
                    },
                )
            }
            .pointerInput(durationMillis) {
                detectDragGestures(
                    onDragStart = { offset ->
                        currentOnGestureStart()
                        startX = offset.x
                        mode = null
                        seekTarget = positionMillis()
                    },
                    onDragEnd = {
                        // 拖动过程中只更新预览，避免每一帧都 seek 造成卡顿与音画抽搐
                        if (mode == GestureMode.SEEK) onSeekTo(seekTarget)
                        mode = null
                        indicator = null
                    },
                    onDragCancel = {
                        mode = null
                        indicator = null
                    },
                    onDrag = { _, delta ->
                        if (mode == null) {
                            mode = when {
                                abs(delta.x) > abs(delta.y) -> GestureMode.SEEK
                                startX < size.width / 2f -> GestureMode.BRIGHTNESS
                                else -> GestureMode.VOLUME
                            }
                        }
                        when (mode) {
                            GestureMode.BRIGHTNESS -> activity?.let { act ->
                                val attrs = act.window.attributes
                                // -1 表示「跟随系统」，此时没有可用基准值，从中间开始调
                                val current = attrs.screenBrightness.takeIf { it >= 0f } ?: 0.5f
                                val next = (current - delta.y / size.height).coerceIn(0.01f, 1f)
                                act.window.attributes = attrs.apply { screenBrightness = next }
                                indicator = Indicator(
                                    IndicatorIcon.BRIGHTNESS,
                                    "亮度 ${(next * 100).roundToInt()}%",
                                )
                            }

                            GestureMode.VOLUME -> audioManager?.let { manager ->
                                val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val step = -delta.y / size.height * maxVolume * 1.5f
                                val next = (current + step).roundToInt().coerceIn(0, maxVolume)
                                manager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
                                indicator = Indicator(
                                    if (next == 0) IndicatorIcon.VOLUME_MUTE else IndicatorIcon.VOLUME,
                                    "音量 ${next * 100 / maxVolume}%",
                                )
                            }

                            GestureMode.SEEK -> {
                                if (durationMillis > 0) {
                                    // 一屏宽度对应整段视频的 1/3，长视频也不会滑一点就跳很远
                                    val deltaMillis = delta.x / size.width * durationMillis / 3
                                    seekTarget = (seekTarget + deltaMillis.toLong())
                                        .coerceIn(0L, durationMillis)
                                    indicator = Indicator(
                                        IndicatorIcon.SEEK,
                                        "${formatDuration((seekTarget / 1000).toInt())}" +
                                            " / ${formatDuration((durationMillis / 1000).toInt())}",
                                    )
                                }
                            }

                            null -> Unit
                        }
                    },
                )
            },
    ) {
        indicator?.let { GestureIndicator(it) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.GestureIndicator(indicator: Indicator) {
    Row(
        modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.65f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = when (indicator.icon) {
                IndicatorIcon.BRIGHTNESS -> Icons.Outlined.BrightnessHigh
                IndicatorIcon.VOLUME -> Icons.AutoMirrored.Outlined.VolumeUp
                IndicatorIcon.VOLUME_MUTE -> Icons.AutoMirrored.Outlined.VolumeOff
                IndicatorIcon.SEEK -> Icons.Outlined.FastForward
                IndicatorIcon.SPEED -> Icons.Outlined.Speed
            },
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            indicator.text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}
