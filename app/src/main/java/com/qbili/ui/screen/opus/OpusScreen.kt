package com.qbili.ui.screen.opus

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.qbili.core.friendlyMessage
import com.qbili.core.normalizeUrl
import com.qbili.domain.model.OpusBlock
import com.qbili.domain.model.OpusDetail
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.ReaderActionBar
import com.qbili.ui.component.AuthorAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpusScreen(
    id: String,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onCommentsClick: (Int, Long) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onArticleClick: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val repository = container.dynamicRepository
    val actionRepository = container.opusInteractionRepository
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var detail by remember(id) { mutableStateOf<OpusDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var retry by remember(id) { mutableIntStateOf(0) }
    var busy by remember(id) { mutableStateOf(false) }
    var feedback by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id, retry) {
        error = null
        try {
            detail = repository.opusDetail(id)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            error = cause.friendlyMessage()
        }
    }
    LaunchedEffect(cookies.isLoggedIn) {
        if (detail != null) retry++
    }
    LaunchedEffect(feedback) {
        feedback?.let { message ->
            feedback = null
            snackbar.showSnackbar(message)
        }
    }

    fun toggleLike() {
        val opus = detail ?: return
        if (!cookies.isLoggedIn) { onLoginClick(); return }
        if (busy) return
        busy = true
        scope.launch {
            try {
                actionRepository.setLike(id, !opus.liked)
                detail = opus.withLike(!opus.liked)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                feedback = "点赞失败：${cause.friendlyMessage()}"
            } finally {
                busy = false
            }
        }
    }

    fun toggleFavorite() {
        val opus = detail ?: return
        if (!cookies.isLoggedIn) { onLoginClick(); return }
        if (busy) return
        busy = true
        scope.launch {
            try {
                actionRepository.setFavorite(id, !opus.favorited)
                detail = opus.withFavorite(!opus.favorited)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                feedback = "收藏失败：${cause.friendlyMessage()}"
            } finally {
                busy = false
            }
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("图文详情") }, navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        })
    }, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        detail?.let { opus ->
            ReaderActionBar(
                commentCount = opus.commentCount,
                likeCount = opus.likeCount,
                favoriteCount = opus.favoriteCount,
                liked = opus.liked,
                favorited = opus.favorited,
                busy = busy,
                onCommentsClick = {
                    if (opus.commentType > 0 && opus.commentOid > 0)
                        onCommentsClick(opus.commentType, opus.commentOid)
                    else feedback = "暂未获取到这篇图文的评论入口"
                },
                onLikeClick = ::toggleLike,
                onFavoriteClick = ::toggleFavorite,
                onShareClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://www.bilibili.com/opus/$id")
                    }, "分享图文"))
                },
            )
        }
    }) { padding ->
        if (detail == null && error == null) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                detail?.let { opus ->
                    if (opus.title.isNotBlank()) item {
                        Text(opus.title, style = MaterialTheme.typography.headlineSmall)
                    }
                    if (opus.author.isNotBlank()) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AuthorAvatar(opus.authorFace, opus.author)
                            Spacer(Modifier.width(8.dp))
                            if (opus.authorMid > 0) TextButton(onClick = { onAuthorClick(opus.authorMid) }) {
                                Text(opus.author)
                            } else Text(opus.author, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    if (opus.commentType == 12 && opus.commentOid > 0) item {
                        TextButton(onClick = { onArticleClick(opus.commentOid) }) {
                            Text("阅读完整专栏")
                        }
                    }
                    items(opus.blocks) { block ->
                        when (block) {
                            is OpusBlock.Paragraph -> Text(block.text,
                                style = MaterialTheme.typography.bodyLarge)
                            is OpusBlock.Picture -> AsyncImage(
                                model = normalizeUrl(block.url), contentDescription = "图文图片",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (error != null || detail?.blocks.isNullOrEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(error ?: "暂未获取到图文正文，可在 B 站打开",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { retry++ }) { Text("重试加载") }
                    }
                }
                item {
                    Button(onClick = {
                        if (id.all(Char::isDigit)) {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.bilibili.com/opus/$id")))
                        }
                    }, enabled = id.all(Char::isDigit)) { Text("在 B 站打开完整图文") }
                }
            }
        }
    }
}
