package com.qbili.ui.screen.course

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.qbili.core.formatDuration
import com.qbili.ui.LocalAppContainer
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterNotNull
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.model.parseCourseTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEntryScreen(onBack: () -> Unit, onCourseClick: (CourseTarget) -> Unit) {
    val container = LocalAppContainer.current
    val model: CourseLibraryViewModel = viewModel(factory = remember(container) { viewModelFactory {
        initializer { CourseLibraryViewModel(container.sessionManager.loginCookies, container.courseProgressStore) }
    } })
    val state by model.state.collectAsStateWithLifecycle()
    val session by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val accountMid = session.takeIf { it.isLoggedIn }?.mid ?: 0
    val currentAccount = state.accountMid == accountMid
    val snackbars = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(session) { confirmClear = null }
    LaunchedEffect(model) { model.state.map { it.message }.filterNotNull().collect {
        model.consumeMessage(); snackbars.showSnackbar(it)
    } }
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    fun open() {
        val target = parseCourseTarget(input)
        if (target == null) invalid = true else { invalid = false; onCourseClick(target) }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbars) }, topBar = { TopAppBar(title = { Text("课程") }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding()) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("粘贴B站课程的完整链接，或输入 ss课程ID／ep课时ID。课程与番剧的ID分别处理。")
                    OutlinedTextField(input, onValueChange = { input = it; invalid = false }, singleLine = true,
                        label = { Text("课程链接或ID") }, placeholder = { Text("https://www.bilibili.com/cheese/play/ss…") },
                        isError = invalid, supportingText = { if (invalid) Text("请输入完整课程链接或ss／ep加数字；不解析短链或外部链接") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { open() }),
                        modifier = Modifier.fillMaxWidth())
                    Button(onClick = ::open, enabled = input.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("打开课程") }
                    Text("支持课程视频与本机学习记录；平台试看不写入完整课时记录。不提供购买或绕过权限；平台课程搜索、已购列表和直播课时暂不支持。")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("最近学习", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmClear = state.accountMid }, enabled = currentAccount &&
                            !state.busy && !state.loading && state.entries.isNotEmpty()) { Text("清空") }
                    }
                    Text(if (accountMid == 0L) "当前为未登录本机记录" else "当前账号的本机记录",
                        style = MaterialTheme.typography.bodySmall)
                    Text("最多保留最近100门课程，仅保存在本机，不同步B站。删除不影响普通视频／番剧历史或课程购买权限。",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(state.query, model::search, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("搜索本机记录：课程、讲师、课时、ss／ep") })
                    if (!currentAccount || state.loading) Text("正在读取课程记录…")
                    if (currentAccount) state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = model::retry) { Text("重试") }
                    }
                    if (currentAccount && !state.loading && state.error == null && state.filtered.isEmpty()) {
                        Text(if (state.entries.isEmpty()) "暂无本机学习记录，实际播放课程视频后会自动保存。" else "没有匹配的学习记录")
                    }
                }
            }
            if (currentAccount) items(state.filtered, key = { it.key }) { entry ->
                ListItem(headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { AsyncImage(entry.cover, "课程封面", Modifier.size(56.dp)) },
                    supportingContent = { Text("${entry.author}\n${entry.episodeTitle}\n" +
                        if (entry.completed) "该课时已看完" else "${formatDuration((entry.positionMs / 1_000).toInt())} / ${formatDuration((entry.durationMs / 1_000).toInt())}") },
                    trailingContent = { TextButton(onClick = { model.delete(entry.accountMid, entry.seasonId) }, enabled = !state.busy) { Text("删除") } },
                    modifier = Modifier.clickable(enabled = !state.busy) { onCourseClick(CourseTarget(seasonId = entry.seasonId)) })
                HorizontalDivider()
            }
        }
    }
    confirmClear?.let { mid ->
        AlertDialog(onDismissRequest = { confirmClear = null }, title = { Text("清空本机课程记录？") },
            text = { Text("删除当前账号全部本机课程记录，包括搜索结果之外的记录。不删除B站账号历史、课程权限或普通视频／番剧记录。") },
            confirmButton = { TextButton(onClick = { confirmClear = null; model.clear(mid) }, enabled = !state.busy && mid == accountMid) { Text("清空") } },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("取消") } })
    }
}
