package com.qbili.ui.screen.article

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.ReaderActionBar
import com.qbili.ui.component.AuthorAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    id: Long,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onCommentsClick: (Long) -> Unit,
    onAuthorClick: (Long) -> Unit,
    onArticleClick: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val viewModel: ArticleViewModel = viewModel(
        factory = remember(container) { ArticleViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cookies by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val url = "https://www.bilibili.com/read/cv$id/"

    LaunchedEffect(id) { viewModel.show(id) }
    LaunchedEffect(cookies.isLoggedIn) {
        if (state.detail != null) viewModel.refreshReaction()
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            viewModel.consumeMessage()
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("专栏") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    state.detail?.authorMid?.takeIf { it > 0 }?.let { mid ->
                        TextButton(onClick = { onAuthorClick(mid) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AuthorAvatar(state.detail?.authorFace.orEmpty(),
                                    state.detail?.authorName.orEmpty(), 28.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("作者")
                            }
                        }
                    }
                    IconButton(onClick = {
                        if (state.detail == null) viewModel.retry() else viewModel.refreshReaction()
                    }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (state.detail != null) ReaderActionBar(
                commentCount = state.reaction?.commentCount,
                likeCount = state.reaction?.likeCount,
                favoriteCount = state.reaction?.favoriteCount,
                liked = state.reaction?.liked == true,
                favorited = state.reaction?.favorited == true,
                busy = state.actionBusy || (cookies.isLoggedIn && state.reactionLoading),
                onCommentsClick = { onCommentsClick(id) },
                onLikeClick = { if (cookies.isLoggedIn) viewModel.toggleLike() else onLoginClick() },
                onFavoriteClick = { if (cookies.isLoggedIn) viewModel.toggleFavorite() else onLoginClick() },
                onShareClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }, "分享专栏"))
                },
            )
        },
    ) { padding ->
        val detail = state.detail
        when {
            detail != null -> ArticleWebView(detail, onAuthorClick, onArticleClick,
                modifier = Modifier.fillMaxSize().padding(padding))
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(state.error.orEmpty())
                OutlinedButton(onClick = viewModel::retry, modifier = Modifier.padding(top = 12.dp)) {
                    Text("重试加载")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("在 B 站打开") }
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { CircularProgressIndicator() }
        }
    }
}
