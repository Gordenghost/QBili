package com.qbili.ui.screen.dynamic

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
import com.qbili.core.formatRelativeTime
import com.qbili.core.normalizeUrl
import com.qbili.domain.model.DynamicPost
import com.qbili.domain.model.VideoItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.util.biliAvatar
import com.qbili.domain.model.formatDynamicText
import com.qbili.ui.util.biliThumbnail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicScreen(onLoginClick: () -> Unit, onAuthorClick: (Long) -> Unit,
    onVideoClick: (VideoItem) -> Unit, onOpusClick: (String) -> Unit,
    onArticleClick: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: DynamicViewModel = viewModel(
        factory = remember(container) { DynamicViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    LaunchedEffect(cookies.isLoggedIn) {
        if (cookies.isLoggedIn) viewModel.refresh() else viewModel.clear()
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("关注动态") }, actions = {
            if (cookies.isLoggedIn) TextButton(onClick = viewModel::refresh, enabled = !state.loading) {
                Text("刷新")
            }
        })
    }) { padding ->
        if (!cookies.isLoggedIn) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("登录后查看关注 UP 主的动态")
                Button(onClick = onLoginClick, modifier = Modifier.padding(top = 16.dp)) { Text("去登录") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.posts, key = { it.id }) { post ->
                    DynamicCard(post, onAuthorClick = { onAuthorClick(post.authorMid) },
                        onVideoClick = { post.video?.let(onVideoClick) },
                        onOpusClick = { post.opusId?.let(onOpusClick) },
                        onArticleClick = { post.articleId?.let(onArticleClick) })
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
                        OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("${state.error} · 点击重试")
                        }
                    }
                } else if (!state.loading && state.offset != null) {
                    item {
                        OutlinedButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多动态")
                        }
                    }
                } else if (!state.loading && state.posts.isEmpty()) {
                    item { Text("暂无动态", modifier = Modifier.padding(24.dp)) }
                }
            }
        }
    }
}

@Composable
internal fun DynamicCard(post: DynamicPost, onAuthorClick: () -> Unit, onVideoClick: () -> Unit,
    onOpusClick: () -> Unit, onArticleClick: () -> Unit,
    modifier: Modifier = Modifier) {
    var selectedImage by remember(post.id) { mutableStateOf<String?>(null) }
    selectedImage?.let { url ->
        Dialog(onDismissRequest = { selectedImage = null }) {
            AsyncImage(
                model = normalizeUrl(url), contentDescription = "图片预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(500.dp)
                    .clickable { selectedImage = null },
            )
        }
    }
    Card(
        onClick = {
            when {
                post.video != null -> onVideoClick()
                post.articleId != null -> onArticleClick()
                post.opusId != null -> onOpusClick()
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = post.authorFace.biliAvatar(80), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    TextButton(onClick = onAuthorClick) {
                        Text(post.authorName.ifBlank { "UP 主" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (post.publishedAt > 0) Text(formatRelativeTime(post.publishedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (post.text.isNotBlank()) Text(formatDynamicText(post.text), maxLines = 8,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp))
            if (post.images.isNotEmpty()) {
                val columns = post.images.size.coerceAtMost(3)
                post.images.chunked(columns).forEach { rowImages ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        rowImages.forEach { image ->
                            AsyncImage(
                                model = if (columns == 1) image.biliThumbnail(720, 405)
                                    else image.biliThumbnail(240, 240),
                                contentDescription = "查看动态图片",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.weight(1f).aspectRatio(if (columns == 1) 16f / 9f else 1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { selectedImage = image },
                            )
                        }
                        repeat(columns - rowImages.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else if (post.cover.isNotBlank()) AsyncImage(
                model = post.cover.biliThumbnail(480, 270), contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            if (post.articleId != null || post.opusId != null) {
                TextButton(onClick = if (post.articleId != null) onArticleClick else onOpusClick,
                    modifier = Modifier.align(Alignment.End)) {
                    Text(if (post.articleId != null) "阅读专栏" else "查看图文详情")
                }
            }
        }
    }
}
