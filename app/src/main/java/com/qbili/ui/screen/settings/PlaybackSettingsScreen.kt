package com.qbili.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbili.core.friendlyMessage
import com.qbili.data.local.PlaybackSettings
import com.qbili.ui.LocalAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val store = LocalAppContainer.current.playbackSettingsStore
    val settings by store.settings.collectAsStateWithLifecycle(initialValue = PlaybackSettings())
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var confirmUpload by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    fun save(resume: Boolean? = null, report: Boolean? = null) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                resume?.let { store.setResume(it) }
                report?.let { store.setReport(it) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { snackbars.showSnackbar(error.friendlyMessage())
            } finally { saving = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("播放与历史") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }, snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(headlineContent = { Text("普通视频断点续播") },
                supportingContent = { Text("再次打开视频时提示继续播放或从头播放，支持分 P。关闭后仍保存本机观看记录。") },
                trailingContent = { Switch(checked = settings.resumeEnabled, onCheckedChange = { save(resume = it) }, enabled = !saving) },
                modifier = Modifier.clickable(enabled = !saving) { save(resume = !settings.resumeEnabled) })
            HorizontalDivider()
            ListItem(headlineContent = { Text("同步普通视频观看进度到 B站") },
                supportingContent = { Text("默认关闭。开启后，仅登录并实际播放时上传当前视频、分 P 和播放进度；不会补传本机旧记录。") },
                trailingContent = { Switch(checked = settings.reportHistory, onCheckedChange = {
                    if (it) confirmUpload = true else save(report = false)
                }, enabled = !saving) },
                modifier = Modifier.clickable(enabled = !saving) {
                    if (settings.reportHistory) save(report = false) else confirmUpload = true
                })
            HorizontalDivider()
            Text("本机记录按账号独立保存，最多保留最近 500 条普通视频。番剧仍使用原有本机续播记录，暂不上传。此开关只控制轻哔，不会修改 B站官方的历史暂停设置。",
                modifier = Modifier.padding(16.dp))
        }
    }
    if (confirmUpload) AlertDialog(onDismissRequest = { confirmUpload = false }, title = { Text("开启观看历史同步？") },
        text = { Text("将通过当前账号向 B站上传普通视频播放进度，并可能影响推荐。无需开启此功能也能查看账号历史、使用本机记录和断点续播。关闭后停止后续上传，已发送的记录需在历史页面删除。") },
        confirmButton = { TextButton(onClick = { confirmUpload = false; save(report = true) }) { Text("开启") } },
        dismissButton = { TextButton(onClick = { confirmUpload = false }) { Text("取消") } })
}
