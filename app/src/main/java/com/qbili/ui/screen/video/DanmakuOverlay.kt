package com.qbili.ui.screen.video

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qbili.domain.model.DanmakuItem
import com.qbili.domain.model.DanmakuMode
import com.qbili.domain.model.DanmakuSettings
import androidx.compose.runtime.LaunchedEffect

/**
 * 弹幕渲染层。
 *
 * 设计取舍：
 * - **轨道分配一次算完**（[assignLanes]），不在每帧里做。逐帧分配会让同一条弹幕
 *   在不同帧落到不同轨道，画面抖动；一次算完还能避免每帧分配对象。
 * - 可见弹幕用**二分查找**定位起点，1400 条弹幕的视频不会随时长退化。
 * - 轨道占满时**复用轨道让弹幕重叠**，而不是丢弃。弹幕高峰期本来就该是刷屏效果，
 *   静默丢弃会让用户以为弹幕没加载。
 */
@Composable
fun DanmakuOverlay(
    danmaku: List<DanmakuItem>,
    settings: DanmakuSettings,
    isPlaying: Boolean,
    positionMillis: () -> Long,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!settings.enabled || danmaku.isEmpty()) return

    val measurer = rememberTextMeasurer()
    var now by remember { mutableLongStateOf(0L) }

    // 暂停时停止刷新，避免白耗电；恢复后立刻对齐到真实进度
    LaunchedEffect(isPlaying, danmaku) {
        now = positionMillis()
        while (isPlaying) {
            withFrameMillis { now = positionMillis() }
        }
    }

    val visible = remember(danmaku, settings) {
        danmaku.filter { it.isRenderable && !settings.isBlocked(it) }
    }
    // 小窗高度不足以容纳全屏的轨道数，否则缩小字号后仍会互相覆盖。
    val laneCount = if (fullscreen) FULLSCREEN_LANE_COUNT else INLINE_LANE_COUNT
    val lanes = remember(visible, settings, laneCount) {
        assignLanes(visible, laneCount, settings.scrollDurationMillis)
    }
    // 文字宽度只跟内容与字号有关，缓存起来避免每帧重新测量
    val layouts = remember(visible, settings.fontScale, fullscreen) {
        HashMap<Int, TextLayoutResult>(visible.size)
    }

    Canvas(modifier = modifier) {
        val laneHeight = size.height * settings.displayAreaRatio / laneCount
        if (laneHeight <= 0f) return@Canvas

        val from = firstVisibleIndex(visible, now - settings.scrollDurationMillis)
        for (index in from until visible.size) {
            val item = visible[index]
            val start = item.progressMillis.toLong()
            if (start > now) break

            val lifetime = if (item.mode == DanmakuMode.SCROLL || item.mode == DanmakuMode.REVERSE) {
                settings.scrollDurationMillis
            } else {
                settings.staticDurationMillis
            }
            val elapsed = now - start
            if (elapsed > lifetime) continue

            val layout = layouts.getOrPut(index) {
                measurer.measure(
                    text = item.content,
                    style = TextStyle(fontSize = danmakuFontSizeSp(
                        item.fontSize, settings.fontScale, fullscreen,
                    ).sp),
                )
            }
            drawDanmaku(item, layout, lanes[index], laneCount, laneHeight, elapsed, lifetime, settings)
        }
    }
}

internal fun danmakuFontSizeSp(fontSize: Int, settingScale: Float, fullscreen: Boolean): Float =
    fontSize * settingScale * if (fullscreen) 1f else 0.6f

private fun DrawScope.drawDanmaku(
    item: DanmakuItem,
    layout: TextLayoutResult,
    lane: Int,
    laneCount: Int,
    laneHeight: Float,
    elapsed: Long,
    lifetimeMillis: Int,
    settings: DanmakuSettings,
) {
    val textWidth = layout.size.width.toFloat()
    val progress = elapsed.toFloat() / lifetimeMillis

    val x = when (item.mode) {
        // 从右侧屏外进入，到左侧完全离开
        DanmakuMode.SCROLL -> size.width - progress * (size.width + textWidth)
        DanmakuMode.REVERSE -> -textWidth + progress * (size.width + textWidth)
        // 顶部/底部弹幕居中静止
        else -> (size.width - textWidth) / 2f
    }

    val y = when (item.mode) {
        DanmakuMode.BOTTOM -> size.height - (lane % laneCount + 1) * laneHeight
        else -> lane * laneHeight
    }

    val color = Color(
        red = (item.color shr 16 and 0xFF) / 255f,
        green = (item.color shr 8 and 0xFF) / 255f,
        blue = (item.color and 0xFF) / 255f,
        alpha = settings.opacity,
    )

    // 先描一层半透明黑做描边：B 站弹幕常是白色，浅色画面上没描边会看不见
    drawText(
        textLayoutResult = layout,
        color = Color.Black.copy(alpha = settings.opacity * 0.55f),
        topLeft = Offset(x + STROKE_OFFSET, y + STROKE_OFFSET),
    )
    drawText(textLayoutResult = layout, color = color, topLeft = Offset(x, y))
}

/**
 * 二分查找第一条可能还在屏幕上的弹幕。
 * 列表按 progressMillis 升序（DanmakuParser 保证），所以可以二分。
 */
private fun firstVisibleIndex(items: List<DanmakuItem>, earliestStart: Long): Int {
    var low = 0
    var high = items.size
    while (low < high) {
        val mid = (low + high) / 2
        if (items[mid].progressMillis < earliestStart) low = mid + 1 else high = mid
    }
    return low
}

/**
 * 轨道分配：每条弹幕进入时选一条「已经空出来」的轨道。
 * 轨道在自身生命周期过半后视为可再进入——完全等它离开屏幕会让弹幕过于稀疏，
 * 而这是 B 站原生弹幕的实际观感。
 */
private fun assignLanes(items: List<DanmakuItem>, laneCount: Int, lifetimeMillis: Int): IntArray {
    val freeAt = LongArray(laneCount)
    val lanes = IntArray(items.size)
    items.forEachIndexed { index, item ->
        val start = item.progressMillis.toLong()
        var lane = -1
        for (candidate in 0 until laneCount) {
            if (freeAt[candidate] <= start) {
                lane = candidate
                break
            }
        }
        // 全部轨道都占着：复用一条造成重叠，而不是把这条弹幕丢掉
        if (lane < 0) lane = index % laneCount
        lanes[index] = lane
        freeAt[lane] = start + lifetimeMillis / 2
    }
    return lanes
}

private const val FULLSCREEN_LANE_COUNT = 12
private const val INLINE_LANE_COUNT = 4
private val STROKE_OFFSET = 1.5.dp.value
