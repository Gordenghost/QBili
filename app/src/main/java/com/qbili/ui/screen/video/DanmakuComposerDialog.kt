package com.qbili.ui.screen.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.qbili.core.formatPosition
import com.qbili.domain.model.DanmakuMode
import com.qbili.domain.model.DanmakuPost

@Composable
fun DanmakuComposerDialog(state: DanmakuComposerViewModel.UiState, viewModel: DanmakuComposerViewModel,
    onSend: () -> Unit) {
    if (!state.visible) return
    AlertDialog(
        onDismissRequest = viewModel::close,
        title = { Text("发送弹幕") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("发送位置：${formatPosition(state.progressMillis)} · 打开输入框时的位置",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = state.draft, onValueChange = viewModel::edit,
                    enabled = !state.busy, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("弹幕内容") },
                    supportingText = { Text("${state.draft.trim().codePointCount(0, state.draft.trim().length)}/${DanmakuPost.MAX_LENGTH}") })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DanmakuMode.SCROLL to "滚动", DanmakuMode.TOP to "顶部", DanmakuMode.BOTTOM to "底部")
                        .forEach { (mode, label) ->
                            FilterChip(selected = state.mode == mode, onClick = { viewModel.setMode(mode) },
                                enabled = !state.busy, label = { Text(label) })
                        }
                }
                Text("颜色", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0xFFFFFF to "白", 0xFF6699 to "粉", 0xFFD700 to "黄", 0x00D9FF to "蓝")
                        .forEach { (color, label) ->
                            FilterChip(selected = state.color == color, onClick = { viewModel.setColor(color) },
                                enabled = !state.busy, label = { Text(label) })
                        }
                }
                Text(state.draft.ifBlank { "弹幕预览" }, color = Color(0xFF000000L or state.color.toLong()),
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF242424)).padding(10.dp))
                Text("等级、颜色权限、发送频率和审核由 B站决定。发送失败不会自动重试。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !state.busy && state.draft.isNotBlank()) {
                Text(if (state.busy) "发送中…" else "发送")
            }
        },
        dismissButton = { TextButton(onClick = viewModel::close, enabled = !state.busy) { Text("关闭") } },
    )
}
