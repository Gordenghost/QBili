package com.qbili.ui.screen.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qbili.core.formatDuration
import com.qbili.domain.player.coursePlaybackSpeeds
import com.qbili.domain.player.courseSleepMinutes
import com.qbili.domain.player.formatCourseSpeed

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CoursePlaybackSettingsDialog(state: CourseViewModel.UiState, onSpeed: (Float) -> Unit,
    onTimer: (Int) -> Unit, onCancelTimer: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("课程播放设置") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("倍速（本机记忆）", style = MaterialTheme.typography.titleSmall)
            Text(if (!state.speedLoaded) "正在读取倍速设置…" else if (state.savingSpeed) "正在保存倍速…"
                else "当前 ${formatCourseSpeed(state.playbackSpeed)}")
            state.speedError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                coursePlaybackSpeeds.forEach { speed ->
                    FilterChip(selected = state.playbackSpeed == speed, enabled = state.speedLoaded && !state.savingSpeed,
                        onClick = { onSpeed(speed) }, label = { Text(formatCourseSpeed(speed),
                            color = if (state.playbackSpeed == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) })
                }
            }
            Text("仅影响课程播放，切画质或课时沿用；长按临时2倍，松开恢复所选倍速。调整倍速不会自动恢复暂停。",
                style = MaterialTheme.typography.bodySmall)
            Text("定时暂停", style = MaterialTheme.typography.titleSmall)
            Text(if (state.sleepRemainingSeconds > 0) "${formatDuration(state.sleepRemainingSeconds)} 后暂停"
                else if (state.sleepTimerExpired) "定时暂停已生效，课程已暂停"
                else if (state.episode == null) "先选择课时，再设置定时暂停" else "未设置")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                courseSleepMinutes.forEach { minutes ->
                    FilterChip(selected = state.sleepTimerMinutes == minutes, enabled = state.episode != null,
                        onClick = { onTimer(minutes) }, label = { Text("${minutes}分钟",
                            color = if (state.sleepTimerMinutes == minutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) })
                }
            }
            if (state.sleepRemainingSeconds > 0) TextButton(onClick = onCancelTimer) { Text("取消定时暂停") }
            Text("按实际时间倒计时，暂停／缓冲也计时，切画质或课时不重置。到点仅暂停本课程，不退出应用；离开课程（含切后台）、刷新或切换账号取消计时。",
                style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}
