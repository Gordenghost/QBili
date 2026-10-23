package com.qbili.ui.screen.course

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.qbili.core.formatDuration
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.player.togglePlayback
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.ReaderImageViewer
import com.qbili.ui.screen.video.ApplyFullscreenWindow
import com.qbili.ui.util.biliAvatar

@OptIn(UnstableApi::class)
@Composable
fun CourseScreen(target: CourseTarget, onBack: () -> Unit, onAuthorClick: (Long) -> Unit, onLoginClick: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val playback: CoursePlaybackViewModel = viewModel(factory = remember(container) { viewModelFactory {
        initializer { CoursePlaybackViewModel(context, container) }
    } })
    val model = playback.course
    val player = playback.player
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current
    val snackbars = remember { SnackbarHostState() }
    var imageIndex by remember { mutableIntStateOf(-1) }
    fun leave() { model.close(); onBack() }
    fun toggle() {
        if (state.switching || state.stream == null) return
        player.togglePlayback()
        model.requestPlayback(player.playWhenReady)
    }
    ApplyFullscreenWindow(state.fullscreen)
    BackHandler { if (state.fullscreen) model.setFullscreen(false) else leave() }
    LaunchedEffect(target) { model.setTarget(target) }
    LaunchedEffect(state.stream, state.episode?.id) {
        val stream = state.stream ?: return@LaunchedEffect
        val episode = state.episode ?: return@LaunchedEffect
        if (model.state.value.stream != stream || model.state.value.episode != episode) return@LaunchedEffect
        try {
            player.setMediaSource(buildCourseMediaSource(stream, episode.id))
            player.prepare()
            player.playWhenReady = model.state.value.requested
        } catch (error: Exception) {
            player.pause()
            model.playbackError("播放器初始化失败，请重试或在B站查看")
        }
    }
    LaunchedEffect(state.requested) { player.playWhenReady = model.state.value.requested }
    LaunchedEffect(state.message) {
        state.message?.let { model.consumeMessage(); snackbars.showSnackbar(it) }
    }
    LaunchedEffect(state.detail) { imageIndex = -1 }
    DisposableEffect(player, lifecycle, model) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                model.setBuffering(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) { player.pause(); model.requestPlayback(false) }
            }
            override fun onPlayerError(error: PlaybackException) {
                player.pause()
                model.playbackError("播放失败：${error.errorCodeName}，请重试或在B站查看")
            }
        }
        player.addListener(listener)
        model.onEnter()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { model.onLeave(); player.pause() }
            if (event == Lifecycle.Event.ON_RESUME) model.onEnter()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            model.onLeave()
            player.pause()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbars) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CoursePlayerSurface(player, state, ::toggle,
                onSeekBy = { delta -> player.seekTo((player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0))) },
                onFullscreen = { model.setFullscreen(!state.fullscreen) },
                onBack = { if (state.fullscreen) model.setFullscreen(false) else leave() },
                modifier = if (state.fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (!state.fullscreen) LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.detail?.let { detail ->
                            Text(detail.title, style = MaterialTheme.typography.titleLarge)
                            if (detail.subtitle.isNotBlank()) Text(detail.subtitle)
                            Row(Modifier.fillMaxWidth().clickable(enabled = detail.authorMid > 0) { onAuthorClick(detail.authorMid) },
                                verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(detail.authorAvatar.biliAvatar(), "讲师头像", Modifier.size(40.dp))
                                Text(detail.authorName.ifBlank { "讲师" }, Modifier.padding(start = 12.dp))
                            }
                            if (detail.releaseInfo.isNotBlank()) Text(detail.releaseInfo)
                            Text(detail.access)
                            if (detail.price.isNotBlank()) Text("平台展示价格：${detail.price}，实际结算以B站为准")
                            if (detail.description.isNotBlank()) Text(detail.description)
                        }
                        if (state.loading) Text("正在加载课程…")
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        state.playError?.let { Text(it, color = MaterialTheme.colorScheme.error)
                            state.episode?.let { episode -> TextButton(onClick = { model.selectEpisode(episode.id) }, enabled = !state.switching) { Text("重试播放") } } }
                        if (state.previewHint.isNotBlank()) Text(state.previewHint)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = model::refresh, enabled = !state.loading) { Text("刷新课程") }
                            TextButton(onClick = { model.onLeave(); player.pause()
                                runCatching { uriHandler.openUri(target.url) }.onFailure { model.showMessage("没有可打开链接的应用") }
                            }) { Text("在B站查看／购买") }
                            TextButton(onClick = { model.onLeave(); player.pause(); onLoginClick() }) { Text("登录") }
                        }
                        Text("仅播放平台返回且授权的课程视频，不自动连播；课程直播、资料下载、评论及断点续播尚未接入。",
                            style = MaterialTheme.typography.bodySmall)
                        Text("课程目录", style = MaterialTheme.typography.titleMedium)
                        if (state.detail?.catalogueIncomplete == true) Text("平台仅返回部分目录，请在B站查看完整课程。")
                        if (state.detail?.episodes?.isEmpty() == true) Text("暂无课程目录")
                    }
                }
                items(state.detail?.episodes.orEmpty(), key = { "episode:${it.id}" }) { episode ->
                    ListItem(headlineContent = { Text("${episode.index}. ${episode.title}") },
                        supportingContent = { Text(listOf(episode.accessHint, formatDuration(episode.durationSeconds))
                            .joinToString(" · ")) }, trailingContent = {
                            if (state.episode?.id == episode.id) Text(if (state.switching) "加载中" else "当前课时")
                        }, modifier = Modifier.clickable(enabled = !state.loading && !state.switching) { model.selectEpisode(episode.id) })
                    HorizontalDivider()
                }
                items(state.detail?.images.orEmpty(), key = { "image:$it" }) { image ->
                    AsyncImage(image, "课程介绍图片", contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable {
                            imageIndex = state.detail?.images?.indexOf(image) ?: -1
                        })
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
    val images = state.detail?.images.orEmpty()
    if (imageIndex in images.indices) ReaderImageViewer(images, imageIndex, onDismiss = { imageIndex = -1 })
}
