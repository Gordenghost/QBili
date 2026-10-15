package com.qbili.ui.screen.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatRelativeTime
import com.qbili.domain.model.PrivateConversation
import com.qbili.domain.model.PrivateMessage
import com.qbili.domain.model.MessageTimelineItem
import com.qbili.domain.model.messageTimeline
import com.qbili.domain.model.messageTimeLabel
import com.qbili.data.repository.MessageRepository
import com.qbili.ui.component.AuthorAvatar
import com.qbili.ui.component.ReaderImageViewer
import com.qbili.ui.LocalAppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(onBack: () -> Unit, onLoginClick: () -> Unit, onAuthorClick: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: MessagesViewModel = viewModel(
        factory = remember(container) { MessagesViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val profile by container.sessionManager.profile.collectAsStateWithLifecycle()
    val myMid = cookies.mid ?: profile?.mid ?: 0L
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.feedback) {
        state.feedback?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeFeedback()
        }
    }
    BackHandler(enabled = state.selected != null) { viewModel.close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.selected?.name ?: "我的私信", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (state.selected?.profileUnavailable == true) Text("资料暂未获取",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.selected == null) onBack() else viewModel.close()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    state.selected?.let { contact ->
                        IconButton(onClick = { onAuthorClick(contact.talkerId) }) {
                            AuthorAvatar(contact.face, contact.name, size = 32.dp)
                        }
                    }
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
                Surface(color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.imePadding().navigationBarsPadding()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = state.draft,
                                onValueChange = viewModel::setDraft,
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("发送文字私信", maxLines = 1) },
                                maxLines = 3,
                                enabled = !state.sending,
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = viewModel::send,
                                enabled = !state.sending && state.draft.isNotBlank() &&
                                    state.draft.trim().length <= MessageRepository.MAX_TEXT_LENGTH,
                            ) { Text(if (state.sending) "发送中" else "发送") }
                        }
                        if (state.draft.length > MessageRepository.MAX_TEXT_LENGTH * 4 / 5) Text(
                            "${state.draft.trim().length}/${MessageRepository.MAX_TEXT_LENGTH}",
                            color = if (state.draft.trim().length > MessageRepository.MAX_TEXT_LENGTH) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                        )
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
            else -> MessageThread(state, myMid, profile?.avatar.orEmpty(), profile?.name.orEmpty(),
                viewModel, onAuthorClick, Modifier.padding(padding))
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
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AuthorAvatar(item.face, item.name, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (item.profileUnavailable) Text("昵称暂未获取", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        if (item.unreadCount > 99) "99+" else "${item.unreadCount}",
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
    myFace: String,
    myName: String,
    viewModel: MessagesViewModel,
    onAuthorClick: (Long) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val timeline = remember(state.messages) { messageTimeline(state.messages).asReversed() }
    var selectedImage by remember(state.selected?.talkerId) { mutableStateOf<String?>(null) }
    selectedImage?.let { url ->
        val images = state.messages.mapNotNull { it.imageUrl }.distinct()
        ReaderImageViewer(images, images.indexOf(url)) { selectedImage = null }
    }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(timeline, key = { it.key }) { entry ->
            when (entry) {
                is MessageTimelineItem.Time -> Text(messageTimeLabel(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                is MessageTimelineItem.Message -> {
                    val message = entry.value
                    val isMine = myMid > 0 && message.senderMid == myMid
                    val contact = state.selected
                    MessageBubble(message, isMine,
                        face = if (isMine) myFace else contact?.face.orEmpty(),
                        name = if (isMine) myName.ifBlank { "我" } else contact?.name.orEmpty(),
                        onAuthorClick = { if (message.senderMid > 0) onAuthorClick(message.senderMid) },
                        onImageClick = { selectedImage = message.imageUrl })
                }
            }
        }
        if (state.olderMessage != null && !state.messagesLoading && state.messagesError == null) {
            item {
                TextButton(onClick = viewModel::loadOlder, modifier = Modifier.fillMaxWidth()) {
                    Text("加载更早消息")
                }
            }
        }
        if (state.messagesError != null) {
            item {
                OutlinedButton(onClick = viewModel::retryMessages, modifier = Modifier.fillMaxWidth()) {
                    Text("${state.messagesError} · 点击重试")
                }
            }
        }
        if (state.messagesLoading) {
            item { LoadingRow() }
        }
        if (!state.messagesLoading && state.messagesError == null && state.messages.isEmpty()) {
            item { Text("暂无消息", modifier = Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun MessageBubble(message: PrivateMessage, isMine: Boolean, face: String, name: String,
    onAuthorClick: () -> Unit, onImageClick: () -> Unit) {
    var imageFailed by remember(message.key, message.imageUrl) { mutableStateOf(false) }
    if (message.type == 5) {
        Text(message.text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isMine) AuthorAvatar(face, name, size = 36.dp,
            modifier = Modifier.clickable(onClick = onAuthorClick))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = if (isMine) Alignment.TopEnd else Alignment.TopStart) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isMine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                val image = message.imageUrl
                if (image != null) {
                    Box(
                        modifier = Modifier.widthIn(min = 96.dp, max = 220.dp)
                            .heightIn(min = 72.dp, max = 240.dp).clickable(onClick = onImageClick).padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = image, contentDescription = "查看私信图片",
                            contentScale = ContentScale.Fit,
                            onError = { imageFailed = true }, onSuccess = { imageFailed = false },
                            modifier = Modifier.widthIn(min = 96.dp, max = 220.dp)
                                .heightIn(min = 72.dp, max = 240.dp),
                        )
                        if (imageFailed) Text(
                            "图片加载失败\n点击预览重试", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                } else SelectionContainer {
                    Text(message.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (isMine) {
            Spacer(Modifier.width(8.dp))
            AuthorAvatar(face, name, size = 36.dp, modifier = Modifier.clickable(onClick = onAuthorClick))
        }
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
