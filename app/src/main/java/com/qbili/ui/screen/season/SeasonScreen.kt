package com.qbili.ui.screen.season

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.qbili.core.formatDuration
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.screen.video.ApplyFullscreenWindow

@OptIn(UnstableApi::class)
@Composable
fun SeasonScreen(
    seasonId: Long,
    onBack: () -> Unit,
    onLoginClick: () -> Unit,
    onCommentsClick: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: SeasonViewModel = viewModel(factory = remember(container) { SeasonViewModel.factory(container, context) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(seasonId) { viewModel.load(seasonId) }
    ApplyFullscreenWindow(state.fullscreen)
    BackHandler(state.fullscreen) { viewModel.setFullscreen(false) }
    DisposableEffect(viewModel, lifecycleOwner) {
        viewModel.onEnter()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.onLeave()
            if (event == Lifecycle.Event.ON_START) viewModel.onEnter()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onLeave()
        }
    }
    LaunchedEffect(state.feedback) {
        state.feedback?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.consumeFeedback() }
    }
    Column(Modifier.fillMaxSize()) {
        SeasonPlayerSurface(
            viewModel, state,
            onBack = { if (state.fullscreen) viewModel.setFullscreen(false) else onBack() },
            modifier = if (state.fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        if (!state.fullscreen) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                state.error?.let { error ->
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(error, color = MaterialTheme.colorScheme.error)
                            Row {
                                TextButton(onClick = { if (state.detail == null) viewModel.reload() else viewModel.retryPlayback() }) { Text("重试") }
                                if (!state.loggedIn) TextButton(onClick = onLoginClick) { Text("去登录") }
                            }
                        }
                    }
                }
                val detail = state.detail
                if (detail != null) {
                    item {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            AsyncImage(detail.cover, contentDescription = "封面", contentScale = ContentScale.Crop,
                                modifier = Modifier.width(92.dp).height(128.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(detail.title, style = MaterialTheme.typography.titleLarge)
                                Text(listOf(detail.typeName, detail.progress).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall)
                                if (detail.score > 0) Text("评分 ${detail.score}", color = MaterialTheme.colorScheme.primary)
                                Button(onClick = { if (state.loggedIn) viewModel.toggleFollow() else onLoginClick() }, enabled = !state.loading && !state.followBusy) {
                                    Text(if (detail.followed) "已追番／追剧" else "追番／追剧")
                                }
                            }
                        }
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text((detail.areas + detail.styles).joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                            Text(detail.description.ifBlank { "暂无简介" }, style = MaterialTheme.typography.bodyMedium)
                            if (detail.areaLimited) Text("当前地区可能受播放限制", color = MaterialTheme.colorScheme.error)
                            if (state.preview) {
                                Text("当前为试看，完整观看可能需要登录、大会员或购买权限。", color = MaterialTheme.colorScheme.primary)
                                if (!state.loggedIn) TextButton(onClick = onLoginClick) { Text("登录后重新获取播放权限") }
                            }
                        }
                    }
                    item {
                        SeasonPlaybackOptions(state, viewModel)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val episodes = detail.episodes
                            val index = episodes.indexOfFirst { it.id == state.episode?.id }
                            TextButton(onClick = { episodes.getOrNull(index - 1)?.let { viewModel.selectEpisode(it.id) } }, enabled = !state.loading && index > 0) { Text("上一集") }
                            TextButton(onClick = { episodes.getOrNull(index + 1)?.let { viewModel.selectEpisode(it.id) } }, enabled = !state.loading && index >= 0 && index < episodes.lastIndex) { Text("下一集") }
                            if ((state.episode?.aid ?: 0) > 0) {
                                TextButton(onClick = { onCommentsClick(requireNotNull(state.episode).aid) }) { Text("本集评论") }
                            }
                        }
                    }
                    if (detail.episodes.isEmpty()) item { Text("暂无已发布剧集", modifier = Modifier.padding(16.dp)) }
                    detail.sections.forEachIndexed { sectionIndex, section ->
                        item(key = "section-$sectionIndex") { Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp)) }
                        items(section.episodes, key = { "episode-${it.id}" }) { episode ->
                            val selected = state.episode?.id == episode.id
                            ListItem(
                                headlineContent = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                supportingContent = {
                                    Text(listOf(if (selected) "当前剧集" else "", episode.accessHint, formatDuration(episode.durationSeconds))
                                        .filter { it.isNotBlank() }.joinToString(" · "))
                                },
                                leadingContent = {
                                    AsyncImage(episode.cover, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.width(88.dp).height(50.dp))
                                },
                                modifier = Modifier.clickable(enabled = !state.loading) { viewModel.selectEpisode(episode.id) },
                                colors = androidx.compose.material3.ListItemDefaults.colors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeasonPlaybackOptions(state: SeasonViewModel.UiState, viewModel: SeasonViewModel) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionMenu(state.stream?.label ?: "画质", state.qualities.map { it.label to it.qn }, !state.loading && !state.switching, viewModel::selectQuality)
            val codecs = state.qualities.firstOrNull { it.qn == state.stream?.quality }?.availableCodecs.orEmpty()
            OptionMenu(state.stream?.dash?.codec?.label ?: "编码", codecs.map { it.label to it }, !state.loading && !state.switching, viewModel::selectCodec)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OptionMenu("${state.speed}×", listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).map { "${it}×" to it }, true, viewModel::setSpeed)
            AssistChip(onClick = viewModel::toggleDanmaku, label = { Text(if (state.danmakuSettings.enabled) "关闭弹幕" else "开启弹幕") })
            Text("${state.danmaku.size} 条", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun <Value> OptionMenu(label: String, options: List<Pair<String, Value>>, enabled: Boolean, onSelect: (Value) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { expanded = true }, label = { Text(label, style = MaterialTheme.typography.labelSmall) }, enabled = enabled && options.isNotEmpty())
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelect(value) })
            }
        }
    }
}
