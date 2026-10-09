package com.qbili.ui.screen.watchlater

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatDuration
import com.qbili.core.formatRelativeTime
import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.WatchLaterItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.FullScreenLoading
import com.qbili.ui.util.biliThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLaterScreen(
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: WatchLaterViewModel = viewModel(
        factory = remember(container) { WatchLaterViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val loggedIn = cookies.isLoggedIn
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(loggedIn) {
        if (loggedIn) viewModel.refresh() else viewModel.clear()
    }
    // 从播放页返回时列表可能已被修改；页面重新可见时以服务端为准。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (loggedIn) viewModel.refresh()
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            viewModel.consumeMessage()
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("稍后再看") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = loggedIn && state.loading && state.videos.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                !loggedIn -> WatchLaterPrompt("登录后可查看稍后再看列表", "去登录", onLoginClick)
                state.loading && state.videos.isEmpty() -> FullScreenLoading()
                state.error != null && state.videos.isEmpty() ->
                    WatchLaterPrompt(state.error.orEmpty(), "重新加载", viewModel::refresh)
                state.videos.isEmpty() -> WatchLaterPrompt("还没有稍后再看的视频", "刷新", viewModel::refresh)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            "共 ${state.videos.size} 个视频",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }
                    if (state.error != null) {
                        item {
                            OutlinedButton(
                                onClick = viewModel::refresh,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) { Text("刷新失败：${state.error}，点击重试") }
                        }
                    }
                    items(state.videos, key = { it.video.aid }) { item ->
                        WatchLaterRow(
                            item = item,
                            removing = item.video.aid in state.removing,
                            enabled = !state.loading,
                            onClick = { onVideoClick(item.video) },
                            onRemove = { viewModel.remove(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchLaterPrompt(text: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun WatchLaterRow(
    item: WatchLaterItem,
    removing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(132.dp).height(82.dp)) {
                AsyncImage(
                    model = item.video.cover.biliThumbnail(300, 190),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                if (item.video.durationSeconds > 0) {
                    Text(
                        formatDuration(item.video.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.video.title.ifBlank { "视频已失效" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    item.video.authorName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val addedLabel = formatRelativeTime(item.addedAt)
                    .takeIf { it.isNotEmpty() }?.let { "$it 加入" }
                Text(
                    listOfNotNull(item.progressLabel, addedLabel).joinToString(" · "),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemove, enabled = enabled && !removing) {
                if (removing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除${item.video.title}")
                }
            }
        }
    }
}
