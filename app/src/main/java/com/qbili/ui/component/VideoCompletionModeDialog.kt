package com.qbili.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.qbili.domain.player.VideoCompletionMode

@Composable
fun VideoCompletionModeDialog(
    selected: VideoCompletionMode,
    onSelect: (VideoCompletionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("普通视频播放模式") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).selectableGroup()) {
                VideoCompletionMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().selectable(selected == mode, role = Role.RadioButton,
                        onClick = { onSelect(mode) }).padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected == mode, onClick = null)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(mode.title)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("仅适用于普通视频，不自动播放相关推荐。定时关闭优先；离开页面或切换账号后不会自动续播。选择会保存在本机。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
