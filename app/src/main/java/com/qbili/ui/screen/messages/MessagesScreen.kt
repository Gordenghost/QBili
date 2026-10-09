package com.qbili.ui.screen.messages

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.qbili.domain.model.PrivateConversation
import com.qbili.domain.model.PrivateMessage
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.util.biliAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(onBack: () -> Unit, onLoginClick: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: MessagesViewModel = viewModel(
        factory = remember(container) { MessagesViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val profile by container.sessionManager.profile.collectAsStateWithLifecycle()
    val myMid = cookies.mid ?: profile?.mid ?: 0L
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(cookies.isLoggedIn) {
        if (cookies.isLoggedIn) viewModel.refreshSessions() else viewModel.clear()
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            viewModel.consumeFeedback()
            snackbar.showSnackbar(it)
        }
    }
    BackHandler(enabled = state.selected != null) { viewModel.close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selected?.name ?: "我的私信") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.selected == null) onBack() else viewModel.close()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (cookies.isLoggedIn) {
                        val loading = if (state.selected == null) state.sessionsLoading else state.messagesLoading
                        TextButton(
                            onClick = if (state.selected == null) viewModel::refreshSessions
                                else viewModel::refreshMessages,
                            enabled = !loading,
                        ) { Text("刷新") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.selected != null && cookies.isLoggedIn) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = state.draft,
                            onValueChange = viewModel::setDraft,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("发送文字私信") },
                            maxLines = 3,
                            enabled = !state.sending,
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = viewModel::send,
                            enabled = !state.sending && state.draft.isNotBlank() &&
                                state.draft.trim().length <= 500,
                        ) { Text(if (state.sending) "发送中" else "发送") }
                    }
                }
            }
        },
    ) { padding ->
        when {
            !cookies.isLoggedIn -> MessagePrompt(
                "登录后可查看和回复私信", "去登录", onLoginClick, Modifier.padding(padding),
            )
            state.selected == null -> SessionList(state, viewModel, Modifier.padding(padding))
            else -> MessageThread(state, myMid, viewModel, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SessionList(state: MessagesViewModel.UiState, viewModel: MessagesViewModel, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.sessions, key = { it.talkerId }) { item ->
            SessionRow(item, onClick = { viewModel.open(item) })
        }
        if (state.sessionsLoading) {
            item { LoadingRow() }
        }
        if (state.sessionsError != null) {
            item {
                OutlinedButton(
                    onClick = viewModel::retrySessions,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("${state.sessionsError} · 点击重试") }
            }
        } else if (!state.sessionsLoading && state.nextSession != null) {
            item {
                OutlinedButton(
                    onClick = viewModel::loadMoreSessions,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) { Text("加载更多会话") }
            }
        } else if (!state.sessionsLoading && state.sessions.isEmpty()) {
            item { Text("暂无私信会话", modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun SessionRow(item: PrivateConversation, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.face.biliAvatar(96),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.timestamp > 0) Text(
                        formatRelativeTime(item.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.preview.ifBlank { "暂无消息" },
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (item.unreadCount > 0) Text(
                        "${item.unreadCount}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageThread(
    state: MessagesViewModel.UiState,
    myMid: Long,
    viewModel: MessagesViewModel,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.selected?.talkerId, state.messages.lastOrNull()?.key) {
        if (state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex + if (state.olderMessage != null) 1 else 0)
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.olderMessage != null && !state.messagesLoading) {
            item {
                TextButton(onClick = viewModel::loadOlder, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更早消息")
                }
            }
        }
        if (state.messagesError != null) {
            item {
                OutlinedButton(onClick = viewModel::refreshMessages, modifier = Modifier.fillMaxWidth()) {
                    Text("${state.messagesError} · 点击重试")
                }
            }
        }
        if (state.messagesLoading) {
            item { LoadingRow() }
        }
        items(state.messages, key = { it.key }) { message ->
            MessageBubble(message, isMine = myMid > 0 && message.senderMid == myMid)
        }
        if (!state.messagesLoading && state.messagesError == null && state.messages.isEmpty()) {
            item { Text("暂无消息", modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun MessageBubble(message: PrivateMessage, isMine: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
        if (message.timestamp > 0) Text(
            formatRelativeTime(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MessagePrompt(text: String, action: String, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text)
        Button(onClick = onClick, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}
