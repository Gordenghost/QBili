package com.qbili.ui.screen.comment

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatCount
import com.qbili.core.formatRelativeTime
import com.qbili.data.repository.CommentRepository
import com.qbili.domain.model.VideoComment
import com.qbili.domain.model.segmentCommentText
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.AuthorAvatar
import com.qbili.ui.util.biliAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentScreen(oid: Long, type: Int, onBack: () -> Unit, onLoginClick: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: CommentViewModel = viewModel(
        factory = remember(container) { CommentViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(oid, type) { viewModel.show(oid, type) }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            viewModel.consumeFeedback()
            snackbar.showSnackbar(it)
        }
    }
    LaunchedEffect(state.replyTo?.id, state.replyRoot?.id) {
        if (state.replyTo != null && state.replyRoot == null && cookies.isLoggedIn) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("评论") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    state.replyTo?.let { target ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "回复 @${target.authorName}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.replyTo(null) }) { Text("取消") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = viewModel::setDraft,
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            placeholder = { Text("写下你的评论") },
                            maxLines = 3,
                            enabled = cookies.isLoggedIn && !state.sending,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (cookies.isLoggedIn) {
                            Button(
                                onClick = viewModel::send,
                                enabled = state.draft.isNotBlank() && !state.sending,
                            ) { Text(if (state.sending) "发送中" else "发送") }
                        } else {
                            Button(onClick = onLoginClick) { Text("登录") }
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "评论 ${formatCount(state.total.toLong())}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = state.mode == CommentRepository.MODE_POPULAR,
                        onClick = { viewModel.changeMode(CommentRepository.MODE_POPULAR) },
                        label = { Text("最热") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = state.mode == CommentRepository.MODE_LATEST,
                        onClick = { viewModel.changeMode(CommentRepository.MODE_LATEST) },
                        label = { Text("最新") },
                    )
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Text("刷新", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            if (state.loading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
            items(state.comments, key = { it.id }) { comment ->
                CommentCard(
                    comment = comment,
                    onReply = viewModel::replyTo,
                    onOpenReplies = viewModel::openReplies,
                )
            }
            if (state.loadingMore) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
            if (state.error != null) {
                item {
                    OutlinedButton(
                        onClick = if (state.comments.isEmpty()) viewModel::refresh else viewModel::loadMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("${state.error} · 点击重试") }
                }
            } else if (!state.loading && !state.loadingMore && state.next != null) {
                item {
                    OutlinedButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                        Text("加载更多")
                    }
                }
            } else if (!state.loading && state.comments.isEmpty()) {
                item {
                    Text(
                        "暂无评论",
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }

    state.replyRoot?.let { root ->
        ModalBottomSheet(
            onDismissRequest = viewModel::closeReplies,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.8f)
                    .padding(horizontal = 16.dp),
            ) {
                Text("${root.replyCount} 条回复", style = MaterialTheme.typography.titleLarge)
                Text(root.authorName, style = MaterialTheme.typography.labelMedium)
                CommentText(root.message, root.emotes, Modifier.padding(vertical = 8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.threadReplies, key = { it.id }) { reply ->
                        CommentCard(
                            comment = reply,
                            onReply = {
                                viewModel.replyTo(it)
                                viewModel.closeReplies()
                            },
                            onOpenReplies = {},
                            showRepliesLink = false,
                        )
                    }
                    if (state.threadLoading) {
                        item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
                    }
                    if (state.threadError != null) {
                        item {
                            OutlinedButton(onClick = viewModel::loadMoreReplies) {
                                Text("${state.threadError} · 点击重试")
                            }
                        }
                    } else if (state.threadHasMore && !state.threadLoading) {
                        item {
                            OutlinedButton(onClick = viewModel::loadMoreReplies) { Text("加载更多回复") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentCard(
    comment: VideoComment,
    onReply: (VideoComment) -> Unit,
    onOpenReplies: (VideoComment) -> Unit,
    showRepliesLink: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AuthorAvatar(comment.authorFace, comment.authorName, 36.dp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(comment.authorName, style = MaterialTheme.typography.labelMedium)
                    Text(
                        formatRelativeTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { onReply(comment) }) { Text("回复") }
            }
            CommentText(comment.message, comment.emotes)
            Text(
                "${formatCount(comment.likeCount)} 赞 · ${comment.replyCount} 回复",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            comment.previews.take(2).forEach { reply ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AuthorAvatar(reply.authorFace, reply.authorName, 28.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(reply.authorName, style = MaterialTheme.typography.labelSmall)
                        CommentText(reply.message, reply.emotes, maxLines = 2)
                    }
                    TextButton(onClick = { onReply(reply) }) { Text("回复") }
                }
            }
            if (showRepliesLink && (comment.replyCount > 0 || comment.previews.isNotEmpty())) {
                TextButton(onClick = { onOpenReplies(comment) }) {
                    Text("查看全部 ${maxOf(comment.replyCount, comment.previews.size)} 条回复")
                }
            }
        }
    }
}

@Composable
private fun CommentText(
    message: String,
    emotes: Map<String, String>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val segments = remember(message, emotes) { segmentCommentText(message, emotes) }
    val inlineImages = segments.filter { it.imageUrl != null }.associate { part ->
        val url = part.imageUrl.orEmpty()
        part.text to InlineTextContent(
            placeholder = Placeholder(22.sp, 22.sp, PlaceholderVerticalAlign.Center),
        ) {
            AsyncImage(
                model = url.biliAvatar(56),
                contentDescription = part.text,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    Text(
        text = buildAnnotatedString {
            segments.forEach { part ->
                if (part.imageUrl == null) append(part.text) else appendInlineContent(part.text, part.text)
            }
        },
        inlineContent = inlineImages,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}
