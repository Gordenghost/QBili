package com.qbili.ui.screen.favorite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatDuration
import com.qbili.domain.model.VideoItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.util.biliThumbnail
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteScreen(
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: FavoriteViewModel = viewModel(
        factory = remember(container) { FavoriteViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val profile by container.sessionManager.profile.collectAsStateWithLifecycle()
    val mid = cookies.mid ?: profile?.mid
    val scope = rememberCoroutineScope()

    LaunchedEffect(cookies.isLoggedIn, mid) {
        if (cookies.isLoggedIn && mid != null) viewModel.start(mid) else viewModel.clear()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (cookies.isLoggedIn) viewModel.refresh()
    }
    BackHandler(enabled = state.selected != null) { viewModel.closeFolder() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selected?.title ?: "我的收藏") },
                navigationIcon = {
                    IconButton(onClick = { if (state.selected == null) onBack() else viewModel.closeFolder() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (cookies.isLoggedIn) {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                            Text("刷新", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            !cookies.isLoggedIn -> FavoriteMessage(
                "登录后可查看收藏夹", "去登录", onLoginClick, Modifier.padding(padding),
            )
            mid == null -> FavoriteMessage("正在读取账号信息", "刷新", { scope.launch { container.sessionManager.refresh() } }, Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.selected == null) {
                    items(state.folders, key = { it.id }) { folder ->
                        Card(onClick = { viewModel.openFolder(folder) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Folder, contentDescription = null)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(folder.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${folder.mediaCount} 个内容" + if (folder.isPrivate) " · 私密" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(state.videos, key = { it.aid }) { item ->
                        Card(onClick = { onVideoClick(item) }, modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = item.cover.biliThumbnail(300, 180),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.width(120.dp).height(74.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${item.authorName} · ${formatDuration(item.durationSeconds)}",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
                if (state.error != null) {
                    item {
                        OutlinedButton(
                            onClick = if (state.selected == null) viewModel::refresh else viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("${state.error} · 点击重试") }
                    }
                } else if (!state.loading && state.selected != null && state.hasMore) {
                    item {
                        OutlinedButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多")
                        }
                    }
                } else if (!state.loading &&
                    (state.selected == null && state.folders.isEmpty() ||
                        state.selected != null && state.videos.isEmpty())
                ) {
                    item { Text("暂无内容", modifier = Modifier.padding(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FavoriteMessage(text: String, button: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text)
        Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(button) }
    }
}
