package com.qbili.ui.screen.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.util.biliAvatar
import com.qbili.ui.util.biliThumbnail

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(roomId: Long, onBack: () -> Unit, onAuthorClick: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: LiveViewModel = viewModel(factory = remember(container) {
        LiveViewModel.factory(container, context)
    })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(roomId) { viewModel.load(roomId) }
    DisposableEffect(viewModel) { onDispose { viewModel.stop() } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("直播间") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = { TextButton(onClick = viewModel::refresh) { Text("刷新") } },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
                if (state.room?.streamUrl != null) {
                    AndroidView(
                        factory = { PlayerView(it).apply { player = viewModel.player } },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    state.room?.cover?.takeIf { it.isNotBlank() }?.let { cover ->
                        AsyncImage(
                            model = cover.biliThumbnail(720, 405), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        if (state.loading) "正在加载直播" else if (state.room?.isLive == false) "当前未开播" else "暂无可播放的直播流",
                        color = Color.White, modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(20.dp))
            }
            state.error?.let { error ->
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::refresh) { Text("重试") }
                }
            }
            state.room?.let { room ->
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(room.title, style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = room.anchorFace.biliAvatar(72), contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { if (room.anchorMid > 0) onAuthorClick(room.anchorMid) }) {
                            Text(room.anchor.ifBlank { "UP 主" })
                        }
                        Spacer(Modifier.weight(1f))
                        Text(room.area, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text("实时弹幕", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 16.dp))
                state.chatError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                ) {
                    items(state.messages) { message ->
                        Text("${message.sender}：${message.text}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
