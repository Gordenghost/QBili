package com.qbili.ui.screen.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qbili.domain.model.VideoItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.VideoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(onVideoClick: (VideoItem) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: RankingViewModel = viewModel(
        factory = remember(container) { RankingViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        if (viewModel.uiState.value.page == 0) viewModel.refresh()
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("热门榜") },
            actions = {
                IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                    Text("刷新", style = MaterialTheme.typography.labelSmall)
                }
            },
        )
    }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "全站热门视频 · 分区榜单持续开发中",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(state.videos, key = { _, item -> item.aid }) { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (index < 3) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    VideoListItem(item, onClick = { onVideoClick(item) }, modifier = Modifier.weight(1f))
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
                        onClick = if (state.page == 0) viewModel::refresh else viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("${state.error} · 点击重试") }
                }
            } else if (!state.loading && state.page > 0 && state.hasMore) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) { Text("加载更多") }
                }
            } else if (!state.loading && state.page > 0 && state.videos.isEmpty()) {
                item { Text("暂无热门视频", modifier = Modifier.padding(24.dp)) }
            }
        }
    }
}
