package com.qbili.ui.screen.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatDuration
import com.qbili.core.formatPosition
import com.qbili.domain.model.WatchHistoryItem
import com.qbili.domain.model.historyDateLabel
import com.qbili.ui.LocalAppContainer
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onLoginClick: () -> Unit,
    onVideoClick: (WatchHistoryItem) -> Unit, onSeasonClick: (Long, WatchHistoryItem) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(factory = remember(container) { HistoryViewModel.factory(container) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var deleteTarget by remember { mutableStateOf<WatchHistoryItem?>(null) }
    var clearConfirmation by remember { mutableStateOf(false) }
    var resumedOnce by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (resumedOnce) viewModel.refresh()
        resumedOnce = true
    }
    LaunchedEffect(state.message) {
        state.message?.let { viewModel.consumeMessage(); snackbars.showSnackbar(it) }
    }
    LaunchedEffect(state.seasonTarget) {
        state.seasonTarget?.let { (id, item) -> viewModel.consumeTarget(); onSeasonClick(id, item) }
    }
    LaunchedEffect(state.local, state.accountMid) { listState.scrollToItem(0); deleteTarget = null; clearConfirmation = false }
    LaunchedEffect(listState, state.items.size, state.hasMore) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged().collect { index ->
                if (index >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
            }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("观看历史") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        }, actions = {
            TextButton(onClick = { clearConfirmation = true }, enabled = state.items.isNotEmpty() && !state.busy && !state.loading) { Text("清空") }
        }) },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !state.local, onClick = { viewModel.selectSource(false) }, label = { Text("B站账号") }, enabled = state.loggedIn && !state.busy)
                FilterChip(selected = state.local, onClick = { viewModel.selectSource(true) }, label = { Text("本机记录") }, enabled = !state.busy)
            }
            Text(if (state.local) "仅显示当前账号在本机观看的普通视频；未登录记录独立保存。" else "读取当前账号的观看历史；上传开关位于设置 → 播放与历史。",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            if (!state.loggedIn) TextButton(onClick = onLoginClick) { Text("登录查看 B站账号历史") }
            PullToRefreshBox(isRefreshing = state.loading && state.items.isNotEmpty(), onRefresh = viewModel::refresh, modifier = Modifier.weight(1f)) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (state.items.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            if (state.loading) CircularProgressIndicator() else Text(if (state.error == null) "暂无观看记录" else "加载失败")
                        }
                    }
                    state.items.forEachIndexed { index, item ->
                        val label = historyDateLabel(item.viewedAt)
                        if (index == 0 || label != historyDateLabel(state.items[index - 1].viewedAt)) item(key = "date:${item.key}") {
                            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                        }
                        item(key = item.key) {
                            HistoryRow(item, enabled = !state.busy, onClick = {
                                when {
                                    item.business == "archive" && item.aid > 0 -> onVideoClick(item)
                                    item.business == "pgc" && item.episodeId > 0 -> viewModel.openSeason(item)
                                    else -> viewModel.unsupported()
                                }
                            }, onDelete = { deleteTarget = item })
                            HorizontalDivider()
                        }
                    }
                    state.error?.let { error -> item {
                        Column(Modifier.padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { if (state.items.isEmpty()) viewModel.refresh() else viewModel.loadMore() }) { Text("重试") }
                        }
                    } }
                    if (state.items.isNotEmpty() && !state.local) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (state.loading || state.busy) CircularProgressIndicator(Modifier.size(24.dp))
                            else if (state.hasMore) TextButton(onClick = viewModel::loadMore) { Text("加载更多") }
                            else Text("没有更多记录了", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("删除观看记录？") },
            text = { Text(if (state.local) "删除本机记录及续播进度，不影响 B站账号历史。" else "将删除 B站账号中的这条记录和对应本机续播进度。") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; viewModel.delete(target) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } })
    }
    if (clearConfirmation) AlertDialog(onDismissRequest = { clearConfirmation = false }, title = { Text("清空观看历史？") },
        text = { Text(if (state.local) "清空当前账号的本机普通视频记录与续播进度，不影响 B站账号。" else "清空当前 B站账号的全部观看历史及本机普通视频续播进度，此操作无法撤销。") },
        confirmButton = { TextButton(onClick = { clearConfirmation = false; viewModel.clear() }) { Text("确认清空") } },
        dismissButton = { TextButton(onClick = { clearConfirmation = false }) { Text("取消") } })
}

@Composable
private fun HistoryRow(item: WatchHistoryItem, enabled: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(start = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = item.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(112.dp).height(70.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            Text(item.author.ifBlank { when (item.business) { "pgc" -> "番剧／影视"; "archive" -> "视频"; else -> "其他内容" } }, maxLines = 1,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text((if (item.page > 1) "P${item.page} · " else "") + if (item.completed) "已看完" else "${formatPosition(item.positionMs)} / ${formatDuration(item.durationSeconds)}",
                style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onDelete, enabled = enabled) { Icon(Icons.Outlined.DeleteOutline, "删除记录") }
    }
}
