package com.qbili.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.qbili.domain.model.VideoItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.FullScreenError
import com.qbili.ui.component.FullScreenLoading
import com.qbili.ui.component.LoadStateFooter
import com.qbili.ui.component.VideoCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (VideoItem) -> Unit,
    onSearchClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    homeReselections: Flow<Unit> = emptyFlow(),
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = remember { HomeViewModel.factory(container) })
    val items = viewModel.videos.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val refreshLoading by rememberUpdatedState(items.loadState.refresh is LoadState.Loading)
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var selected by remember { mutableStateOf<VideoItem?>(null) }
    LaunchedEffect(viewModel) {
        viewModel.feedback.collect { message -> snackbar.showSnackbar(message) }
    }
    LaunchedEffect(homeReselections, gridState, viewModel) {
        homeReselections.collect {
            when (homeReselectAction(gridState.firstVisibleItemIndex,
                gridState.firstVisibleItemScrollOffset, refreshLoading)) {
                HomeReselectAction.SCROLL_TO_TOP -> gridState.animateScrollToItem(0)
                HomeReselectAction.REFRESH -> viewModel.refresh()
                HomeReselectAction.NONE -> Unit
            }
        }
    }

    selected?.let { video ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Text("减少此类推荐", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text("推荐反馈在本机生效；拉黑 UP 会同步到 B 站账号。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp))
            fun apply(action: () -> Unit) { selected = null; action() }
            FeedbackMenuItem("不感兴趣 · 不再推荐此视频") { apply { viewModel.hide(video) } }
            FeedbackMenuItem("推荐过 · 不再显示此视频") { apply { viewModel.hide(video) } }
            FeedbackMenuItem("此类内容过多 · 屏蔽视频首个标签") { apply { viewModel.blockSimilar(video) } }
            if (video.channel.isNotBlank()) FeedbackMenuItem("屏蔽频道：${video.channel}") {
                apply { viewModel.blockChannel(video) }
            }
            if (video.authorMid > 0) FeedbackMenuItem("拉黑 UP：${video.authorName}") {
                if (cookies.isLoggedIn) apply { viewModel.blockAuthor(video) }
                else apply(onLoginClick)
            }
            FeedbackMenuItem("进入推送屏蔽设置", Modifier.padding(bottom = 24.dp)) { apply(onSettingsClick) }
        }
    }

    val refreshState = items.loadState.refresh
    val isRefreshing = refreshState is LoadState.Loading && items.itemCount > 0

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("推荐") },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, contentDescription = "搜索")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                refreshState is LoadState.Loading && items.itemCount == 0 -> FullScreenLoading()

                refreshState is LoadState.Error && items.itemCount == 0 ->
                    FullScreenError(
                        error = refreshState.error,
                        onRetry = { items.retry() },
                    )

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // 不设置 key：推荐流跨页可能返回重复 bvid，重复 key 会让 Compose 崩溃
                    items(count = items.itemCount) { index ->
                        items[index]?.let { video ->
                            VideoCard(item = video, onClick = { onVideoClick(video) },
                                onLongClick = { if (!busy) selected = video })
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val append = items.loadState.append
                        LoadStateFooter(
                            isLoading = append is LoadState.Loading,
                            error = (append as? LoadState.Error)?.error,
                            onRetry = { items.retry() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedbackMenuItem(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    ) { Text(text, modifier = Modifier.fillMaxWidth()) }
}
