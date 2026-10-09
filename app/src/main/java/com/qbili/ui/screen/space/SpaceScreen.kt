package com.qbili.ui.screen.space

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatCount
import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.SpaceOpusItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.VideoListItem
import com.qbili.ui.screen.dynamic.DynamicCard
import com.qbili.ui.util.biliAvatar
import com.qbili.ui.util.biliThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceScreen(mid: Long, onBack: () -> Unit, onLoginClick: () -> Unit,
    onVideoClick: (VideoItem) -> Unit, onAuthorClick: (Long) -> Unit,
    onOpusClick: (String) -> Unit, onArticleClick: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SpaceViewModel = viewModel(
        factory = remember(container) { SpaceViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val myProfile by container.sessionManager.profile.collectAsStateWithLifecycle()
    val selfMid = cookies.mid ?: myProfile?.mid
    val snackbar = remember { SnackbarHostState() }
    var selectedTab by remember(mid) { mutableIntStateOf(0) }
    LaunchedEffect(mid) { viewModel.show(mid) }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { viewModel.consumeFeedback(); snackbar.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.profile?.name ?: "个人主页") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.refreshProfile()
                        when (selectedTab) {
                            0 -> viewModel.refreshDynamics()
                            1 -> viewModel.refreshVideos()
                            2 -> viewModel.refreshOpus()
                        }
                    }) {
                        Text("刷新", style = MaterialTheme.typography.labelSmall)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.profile?.let { user ->
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(
                                model = user.face.biliAvatar(160),
                                contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.size(72.dp).clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, style = MaterialTheme.typography.titleLarge)
                                Text("Lv${user.level} · ${formatCount(user.followers)} 粉丝 · ${user.videoCount} 投稿",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (user.sign.isNotBlank()) Text(user.sign, modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyMedium)
                        if (user.mid != selfMid) {
                            OutlinedButton(
                                onClick = if (cookies.isLoggedIn) viewModel::toggleFollow else onLoginClick,
                                enabled = !state.followBusy,
                                modifier = Modifier.padding(top = 8.dp),
                            ) { Text(if (!cookies.isLoggedIn) "登录后关注" else if (user.following) "已关注 · 点击取消"
                                else "关注") }
                        }
                    }
                }
            }
            if (state.profileLoading && state.profile == null) {
                item { LoadingSpaceRow() }
            }
            if (state.profileError != null) {
                item {
                    OutlinedButton(onClick = viewModel::refreshProfile,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Text("资料加载失败：${state.profileError} · 点击重试")
                    }
                }
            }
            if (state.mid > 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilterChip(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            label = { Text("动态") })
                        FilterChip(selected = selectedTab == 1, onClick = {
                            selectedTab = 1
                            viewModel.ensureVideos()
                        }, label = { Text("视频投稿") })
                        FilterChip(selected = selectedTab == 2, onClick = {
                            selectedTab = 2
                            viewModel.ensureOpus()
                        }, label = { Text("图文") })
                    }
                }
                if (selectedTab == 0) {
                    items(state.dynamics, key = { "dynamic-${it.id}" }) { post ->
                        DynamicCard(post, onAuthorClick = { onAuthorClick(post.authorMid) },
                            onVideoClick = { post.video?.let(onVideoClick) },
                            onOpusClick = { post.opusId?.let(onOpusClick) },
                            onArticleClick = { post.articleId?.let(onArticleClick) },
                            modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    if (state.dynamicLoading) item { LoadingSpaceRow() }
                    if (state.dynamicError != null) {
                        item {
                            OutlinedButton(onClick = viewModel::retryDynamics,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("${state.dynamicError} · 点击重试")
                            }
                        }
                    } else if (!state.dynamicLoading && state.dynamicOffset != null) {
                        item {
                            OutlinedButton(onClick = viewModel::loadMoreDynamics,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("加载更多动态")
                            }
                        }
                    } else if (!state.dynamicLoading && state.dynamics.isEmpty()) {
                        item { Text("暂无动态", modifier = Modifier.padding(24.dp)) }
                    }
                } else if (selectedTab == 1) {
                    items(state.videos, key = { it.aid }) { video ->
                        VideoListItem(video, onClick = { onVideoClick(video) })
                    }
                    if (state.videosLoading) item { LoadingSpaceRow() }
                    if (state.videosError != null) {
                        item {
                            OutlinedButton(
                                onClick = if (state.page == 0) viewModel::refreshVideos else viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            ) { Text("${state.videosError} · 点击重试") }
                        }
                    } else if (!state.videosLoading && state.hasMore) {
                        item {
                            OutlinedButton(onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("加载更多视频")
                            }
                        }
                    } else if (!state.videosLoading && state.videos.isEmpty()) {
                        item { Text("暂无视频投稿", modifier = Modifier.padding(24.dp)) }
                    }
                } else {
                    items(state.opus, key = { "opus-${it.id}" }) { post ->
                        SpaceOpusRow(post, onClick = {
                            post.articleId?.let(onArticleClick) ?: onOpusClick(post.id)
                        })
                    }
                    if (state.opusLoading) item { LoadingSpaceRow() }
                    if (state.opusError != null) {
                        item {
                            OutlinedButton(onClick = viewModel::loadMoreOpus,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("${state.opusError} · 点击重试")
                            }
                        }
                    } else if (!state.opusLoading && state.opusHasMore) {
                        item {
                            OutlinedButton(onClick = viewModel::loadMoreOpus,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                Text("加载更多图文")
                            }
                        }
                    } else if (!state.opusLoading && state.opus.isEmpty()) {
                        item { Text("暂无图文投稿", modifier = Modifier.padding(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceOpusRow(post: SpaceOpusItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (post.cover.isNotBlank()) AsyncImage(
            model = post.cover.biliThumbnail(240, 240),
            contentDescription = "图文封面",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(88.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(post.content.ifBlank { "图文" }, maxLines = 3, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge)
            Text("${formatCount(post.likeCount)} 赞", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingSpaceRow() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
