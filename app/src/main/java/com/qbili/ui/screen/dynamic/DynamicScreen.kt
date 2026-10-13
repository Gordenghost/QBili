package com.qbili.ui.screen.dynamic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatRelativeTime
import com.qbili.core.normalizeUrl
import com.qbili.domain.model.DynamicPost
import com.qbili.domain.model.VideoItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.AuthorAvatar
import com.qbili.domain.model.DynamicAuthor
import com.qbili.domain.model.searchDynamicAuthors
import com.qbili.domain.model.formatDynamicText
import com.qbili.ui.util.biliThumbnail
import com.qbili.ui.component.ReaderImageViewer

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
    val listState = rememberLazyListState()
    var authorPicker by remember { mutableStateOf(false) }
    LaunchedEffect(cookies.mid, cookies.isLoggedIn) {
        authorPicker = false
        viewModel.setAccount(if (cookies.isLoggedIn) cookies.mid ?: 0 else 0)
    }
    LaunchedEffect(state.selectedAuthor?.mid, cookies.mid) { listState.scrollToItem(0) }
    if (authorPicker && cookies.isLoggedIn) {
        LaunchedEffect(Unit) {
            if (state.authorsPage == 0) viewModel.loadAuthors()
        }
        DynamicAuthorPicker(
            authors = state.authors,
            selected = state.selectedAuthor,
            loading = state.authorsLoading,
            error = state.authorsError,
            hasMore = state.authorsHasMore,
            onLoadMore = viewModel::loadAuthors,
            onSelect = { viewModel.selectAuthor(it); authorPicker = false },
            onDismiss = { authorPicker = false },
        )
    }
    Scaffold(topBar = {
        Column {
            TopAppBar(title = { Text("关注动态") }, actions = {
                if (cookies.isLoggedIn) TextButton(onClick = viewModel::refresh, enabled = !state.loading) {
                    Text("刷新")
                }
            })
            if (cookies.isLoggedIn) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(selected = state.selectedAuthor == null,
                    onClick = { viewModel.selectAuthor(null) }, label = { Text("全部动态") })
                FilterChip(selected = state.selectedAuthor != null, onClick = { authorPicker = true },
                    label = { Text(state.selectedAuthor?.name?.ifBlank { "UP ${state.selectedAuthor?.mid}" }
                        ?: "按 UP 筛选", maxLines = 1, overflow = TextOverflow.Ellipsis) })
            }
        }
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
                state = listState,
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
                    item { Text(if (state.selectedAuthor == null) "暂无动态" else "该 UP 暂无动态",
                        modifier = Modifier.padding(24.dp)) }
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
        ReaderImageViewer(post.images, post.images.indexOf(url)) { selectedImage = null }
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
                AuthorAvatar(post.authorFace, post.authorName,
                    modifier = Modifier.clickable(enabled = post.authorMid > 0, onClick = onAuthorClick))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DynamicAuthorPicker(
    authors: List<DynamicAuthor>, selected: DynamicAuthor?, loading: Boolean, error: String?, hasMore: Boolean,
    onLoadMore: () -> Unit, onSelect: (DynamicAuthor?) -> Unit, onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(authors, query) { authors.searchDynamicAuthors(query) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("按 UP 筛选动态", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
        OutlinedTextField(query, onValueChange = { query = it }, singleLine = true,
            label = { Text("搜索关注 UP 的昵称或 UID") }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup()) {
            item {
                Row(Modifier.fillMaxWidth().selectable(selected == null, role = Role.RadioButton,
                    onClick = { onSelect(null) }).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("全部动态", modifier = Modifier.weight(1f))
                    RadioButton(selected == null, onClick = null)
                }
                HorizontalDivider()
            }
            items(results, key = { it.mid }) { author ->
                Row(Modifier.fillMaxWidth().selectable(selected?.mid == author.mid, role = Role.RadioButton,
                    onClick = { onSelect(author) }).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    AuthorAvatar(author.face, author.name)
                    Spacer(Modifier.width(12.dp))
                    Text(author.name.ifBlank { "UP ${author.mid}" }, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RadioButton(selected?.mid == author.mid, onClick = null)
                }
            }
            if (loading) item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            if (error != null) item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("$error · 重试读取关注列表") }
            } else if (!loading && hasMore) item {
                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多关注 UP") }
            }
            if (!loading && results.isEmpty()) item {
                Text(if (query.isBlank()) "暂无可选择的 UP" else "已加载列表中没有匹配的 UP，可继续加载更多",
                    modifier = Modifier.padding(16.dp))
            }
        }
    }
}
