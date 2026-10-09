package com.qbili.ui.screen.video

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.SubtitlesOff
import androidx.compose.material.icons.outlined.ThumbUpOffAlt
import androidx.compose.material.icons.outlined.ThumbDownOffAlt
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.qbili.core.formatCount
import com.qbili.core.formatDuration
import com.qbili.core.formatRelativeTime
import com.qbili.domain.model.FavFolder
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.FullScreenError
import com.qbili.ui.component.FullScreenLoading
import com.qbili.ui.util.biliAvatar
import kotlinx.coroutines.delay

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f)
private val SLEEP_OPTIONS = listOf(15, 30, 45, 60, 90)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoId: String,
    onBack: () -> Unit,
    onAuthorClick: (Long) -> Unit,
    onCommentsClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: VideoPlayerViewModel = viewModel(
        factory = remember { VideoPlayerViewModel.factory(container, context) },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(videoId) { viewModel.load(videoId) }

    ApplyFullscreenWindow(state.fullscreen)

    // 全屏时的返回键先退出全屏，而不是直接离开播放页
    BackHandler(enabled = state.fullscreen) { viewModel.setFullscreen(false) }

    DisposableEffect(Unit) {
        onDispose { viewModel.player.pause() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // 全屏与竖屏共用同一个 AndroidView 调用点：换调用点会重建 PlayerView，
        // 造成切换瞬间黑屏
        PlayerSurface(
            viewModel = viewModel,
            state = state,
            onBack = { if (state.fullscreen) viewModel.setFullscreen(false) else onBack() },
            modifier = if (state.fullscreen) {
                Modifier.fillMaxSize()
            } else {
                Modifier.fillMaxWidth().aspectRatio(16f / 9f)
            },
        )

        if (!state.fullscreen) {
            when {
                state.loading -> FullScreenLoading()

                state.error != null && state.detail == null -> FullScreenError(
                    error = IllegalStateException(state.error),
                    onRetry = viewModel::retry,
                )

                else -> VideoInfoPane(
                    state = state,
                    viewModel = viewModel,
                    onAuthorClick = onAuthorClick,
                    onCommentsClick = onCommentsClick,
                )
            }
        }
    }
}

/**
 * 全屏时切横屏并隐藏系统栏，退出时还原。
 *
 * MainActivity 声明了 `configChanges="orientation|screenSize"`，
 * 所以旋转不会重建 Activity，播放不会中断。
 */
@Composable
private fun ApplyFullscreenWindow(fullscreen: Boolean) {
    val activity = LocalContext.current as? Activity ?: return

    LaunchedEffect(fullscreen) {
        activity.requestedOrientation = if (fullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            if (fullscreen) {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            } else {
                show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // 离开播放页必须还原，否则会把整个应用留在横屏且没有系统栏
    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(
    viewModel: VideoPlayerViewModel,
    state: VideoPlayerViewModel.UiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var controlsPressed by remember { mutableStateOf(false) }

    LaunchedEffect(controlsVisible, controlsInteraction, scrubbing, controlsPressed) {
        if (controlsVisible && !scrubbing && !controlsPressed) {
            delay(2_500)
            controlsVisible = false
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = false
                }
            },
        )

        // 弹幕盖在画面之上，底部留出控制条的高度
        DanmakuOverlay(
            danmaku = state.danmaku,
            settings = state.danmakuSettings,
            isPlaying = state.isPlaying,
            positionMillis = { viewModel.player.currentPosition },
            fullscreen = state.fullscreen,
            modifier = Modifier.fillMaxSize().padding(bottom = 64.dp),
        )

        // 手势层在弹幕之上：弹幕不接收触摸，手势要能穿过它
        PlayerGestureLayer(
            durationMillis = viewModel.player.duration.coerceAtLeast(0L),
            positionMillis = { viewModel.player.currentPosition },
            onSingleTap = {
                controlsVisible = !controlsVisible
                if (controlsVisible) controlsInteraction++
            },
            onTogglePlay = viewModel::togglePlayPause,
            onSeekTo = { viewModel.player.seekTo(it) },
            onGestureStart = { controlsVisible = false },
            onSpeedBoostStart = { viewModel.startSpeedBoost() },
            onSpeedBoostEnd = viewModel::stopSpeedBoost,
            modifier = Modifier.fillMaxSize(),
        )

        if (controlsVisible) {
            VideoPlayerControls(
                player = viewModel.player,
                isPlaying = state.playbackRequested,
                onTogglePlay = viewModel::togglePlayPause,
                onSeekBy = viewModel::seekBy,
                onSeekTo = { viewModel.player.seekTo(it) },
                onInteraction = { controlsInteraction++ },
                onScrubbingChange = { scrubbing = it },
                onPressedChange = { controlsPressed = it },
            )
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
                tint = Color.White,
            )
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.sleepRemainingSeconds > 0) {
                Text(
                    "⏱ ${formatDuration(state.sleepRemainingSeconds)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (state.speed != 1.0f) {
                Text(
                    "${state.speed}×",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = viewModel::toggleFullscreen) {
                Icon(
                    if (state.fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                    contentDescription = if (state.fullscreen) "退出全屏" else "全屏",
                    tint = Color.White,
                )
            }
        }

        if (state.switching) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }
    }
}

@Composable
private fun VideoInfoPane(
    state: VideoPlayerViewModel.UiState,
    viewModel: VideoPlayerViewModel,
    onAuthorClick: (Long) -> Unit,
    onCommentsClick: (Long) -> Unit,
) {
    val detail = state.detail ?: return
    val context = LocalContext.current
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showCoinDialog by remember { mutableStateOf(false) }
    var showFavSheet by remember { mutableStateOf(false) }

    // 互动结果用系统 Toast 反馈：这个页面没有 Scaffold，
    // 为一句提示引入 SnackbarHost 会把布局搅复杂
    LaunchedEffect(state.toast) {
        state.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text(detail.title, style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MetaText(Icons.Outlined.PlayArrow, formatCount(detail.viewCount))
            Spacer(Modifier.width(10.dp))
            MetaText(Icons.AutoMirrored.Outlined.Comment, formatCount(detail.danmakuCount))
            Spacer(Modifier.width(10.dp))
            MetaText(Icons.Outlined.ThumbUpOffAlt, formatCount(detail.likeCount))
            Spacer(Modifier.width(10.dp))
            Text(
                formatRelativeTime(detail.pubDate),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = detail.authorFace.biliAvatar(),
                contentDescription = detail.authorName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = detail.authorMid > 0) { onAuthorClick(detail.authorMid) },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                detail.authorName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
                    .clickable(enabled = detail.authorMid > 0) { onAuthorClick(detail.authorMid) }
                    .padding(vertical = 12.dp),
            )
            AssistChip(
                onClick = { onAuthorClick(detail.authorMid) },
                label = { Text("主页", style = MaterialTheme.typography.labelSmall) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = state.interaction.following,
                onClick = viewModel::toggleFollow,
                enabled = !state.interactionBusy,
                label = {
                    Text(
                        if (state.interaction.following) "已关注" else "关注",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(10.dp))

        PlaybackControls(
            state = state,
            viewModel = viewModel,
            onOpenDanmakuSettings = { showDanmakuSettings = true },
        )

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(6.dp))

        InteractionRow(
            state = state,
            viewModel = viewModel,
            onOpenCoin = { showCoinDialog = true },
            onOpenFavorites = { if (viewModel.loadFavFolders()) showFavSheet = true },
            onShare = {
                viewModel.reportShare()
                shareVideo(context, detail.title, detail.bvid)
            },
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onCommentsClick(detail.aid) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.Comment, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("查看评论 ${formatCount(detail.replyCount)}")
        }

        if (detail.isMultiPage) {
            Spacer(Modifier.height(14.dp))
            Text("选集（${detail.pages.size}P）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            detail.pages.forEachIndexed { index, page ->
                FilterChip(
                    selected = index == state.pageIndex,
                    onClick = { viewModel.selectPage(index) },
                    label = {
                        Text(
                            "P${page.index} ${page.title}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                )
            }
        }

        if (detail.description.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text("简介", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                detail.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(32.dp))
    }

    if (showDanmakuSettings) {
        DanmakuSettingsSheet(
            state = state,
            viewModel = viewModel,
            onDismiss = { showDanmakuSettings = false },
        )
    }

    if (showCoinDialog) {
        CoinDialog(
            onDismiss = { showCoinDialog = false },
            onConfirm = { count, alsoLike ->
                showCoinDialog = false
                viewModel.addCoin(count, alsoLike)
            },
        )
    }

    if (showFavSheet) {
        LaunchedEffect(state.favoritesSaved) {
            if (state.favoritesSaved) {
                showFavSheet = false
                viewModel.consumeFavoritesSaved()
            }
        }
        FavoriteSheet(
            folders = state.favFolders,
            loading = state.favFoldersLoading,
            error = state.favFoldersError,
            saving = state.interactionBusy,
            onRetry = { viewModel.loadFavFolders() },
            onDismiss = { showFavSheet = false },
            onConfirm = viewModel::applyFavorites,
        )
    }
}

/**
 * 互动按钮行。
 *
 * 已投币数直接显示出来（已投1币/已投2币）——B 站每个视频最多投 2 枚，
 * 只显示「已投币」会让用户不知道还能不能再投；投满 2 枚后按钮置灰。
 */
@Composable
private fun InteractionRow(
    state: VideoPlayerViewModel.UiState,
    viewModel: VideoPlayerViewModel,
    onOpenCoin: () -> Unit,
    onOpenFavorites: () -> Unit,
    onShare: () -> Unit,
) {
    val interaction = state.interaction
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ActionButton(
            icon = if (interaction.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUpOffAlt,
            label = if (interaction.liked) "已点赞" else "点赞",
            active = interaction.liked,
            enabled = true,
            onClick = { if (!state.interactionBusy) viewModel.toggleLike() },
        )
        ActionButton(
            icon = if (interaction.disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDownOffAlt,
            label = if (interaction.disliked) "已点踩" else "点踩",
            active = interaction.disliked,
            // 扫码/Cookie 登录缺少 App 凭据，保留点击入口用于明确解释原因。
            enabled = true,
            onClick = { if (!state.interactionBusy) viewModel.toggleDislike() },
        )
        ActionButton(
            icon = Icons.Outlined.MonetizationOn,
            label = if (interaction.coinCount > 0) "已投${interaction.coinCount}币" else "投币",
            active = interaction.coinCount > 0,
            enabled = interaction.coinCount < 2,
            onClick = { if (!state.interactionBusy) onOpenCoin() },
        )
        ActionButton(
            icon = if (interaction.favorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
            label = "收藏",
            active = interaction.favorited,
            enabled = true,
            onClick = { if (!state.interactionBusy) onOpenFavorites() },
        )
        ActionButton(
            icon = Icons.Outlined.WatchLater,
            label = "稍后看",
            active = interaction.inWatchLater,
            enabled = true,
            onClick = { if (!state.interactionBusy) viewModel.toggleWatchLater() },
        )
        ActionButton(
            icon = Icons.Outlined.Share,
            label = "转发",
            active = false,
            enabled = true,
            onClick = onShare,
        )
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // 写请求期间只阻止重复点击，不改变颜色，避免整排图标明暗闪烁。
    val tint = if (active) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = label, tint = tint)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
private fun CoinDialog(
    onDismiss: () -> Unit,
    onConfirm: (count: Int, alsoLike: Boolean) -> Unit,
) {
    var count by remember { mutableStateOf(1) }
    var alsoLike by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("投币") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1, 2).forEach { value ->
                        FilterChip(
                            selected = count == value,
                            onClick = { count = value },
                            label = { Text("$value 枚") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(checked = alsoLike, onCheckedChange = { alsoLike = it })
                    Text("同时点赞", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(count, alsoLike) }) { Text("投币") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 收藏夹多选。
 *
 * 勾选完一次提交：新增与移除合并成一个请求，
 * 避免「先删后加、加失败」把原有收藏弄丢。
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteSheet(
    folders: List<FavFolder>,
    loading: Boolean,
    error: String?,
    saving: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Set<Long>) -> Unit,
) {
    // 以服务端返回的 fav_state 作为初始勾选状态
    val selected = remember(folders) {
        mutableStateOf(folders.filter { it.containsVideo }.map { it.id }.toSet())
    }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.75f
    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() },
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("收藏到", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
            } else if (folders.isEmpty()) {
                Text("暂无收藏夹，请先在 B 站创建收藏夹")
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(folders, key = { it.id }) { folder ->
                        val checked = folder.id in selected.value
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = !saving) {
                                    selected.value = if (checked) selected.value - folder.id
                                        else selected.value + folder.id
                                }.padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !saving)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${folder.mediaCount} 个内容" + if (folder.isPrivate) " · 私密" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
                TextButton(
                    onClick = { onConfirm(selected.value) },
                    enabled = !loading && error == null && folders.isNotEmpty() && !saving,
                ) { Text(if (saving) "保存中" else "确定") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** 走系统分享面板把视频链接发给其它应用 */
private fun shareVideo(context: Context, title: String, bvid: String) {
    val url = "https://www.bilibili.com/video/$bvid"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, title + "\n" + url)
    }
    context.startActivity(Intent.createChooser(intent, "分享到"))
}

@Composable
private fun PlaybackControls(
    state: VideoPlayerViewModel.UiState,
    viewModel: VideoPlayerViewModel,
    onOpenDanmakuSettings: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 画质菜单只列真实存在的轨道，不用 accept_quality（它会虚报）
            MenuChip(
                label = state.stream?.qualityLabel ?: "画质",
                options = state.qualities.map { it.label to it.qn },
                onSelect = viewModel::selectQuality,
            )
            MenuChip(
                label = state.stream?.codec?.label ?: "编码",
                options = state.availableCodecs.map { it.label to it },
                onSelect = viewModel::selectCodec,
            )
            MenuChip(
                label = state.stream?.audioLabel?.ifBlank { "音质" } ?: "音质",
                options = state.audioQualities.map { it.label to it.id },
                onSelect = viewModel::selectAudioQuality,
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MenuChip(
                label = "${state.speed}× 倍速",
                options = SPEED_OPTIONS.map { "${it}×" to it },
                onSelect = viewModel::setSpeed,
            )
            SleepTimerChip(state = state, viewModel = viewModel)

            IconButton(onClick = viewModel::toggleDanmaku) {
                Icon(
                    if (state.danmakuSettings.enabled) {
                        Icons.Outlined.Subtitles
                    } else {
                        Icons.Outlined.SubtitlesOff
                    },
                    contentDescription = if (state.danmakuSettings.enabled) "关闭弹幕" else "开启弹幕",
                )
            }
            IconButton(onClick = onOpenDanmakuSettings) {
                Icon(Icons.Outlined.Tune, contentDescription = "弹幕设置")
            }
            Text(
                "${state.danmaku.size} 条弹幕",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SleepTimerChip(
    state: VideoPlayerViewModel.UiState,
    viewModel: VideoPlayerViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when {
        state.sleepRemainingSeconds > 0 -> formatDuration(state.sleepRemainingSeconds)
        state.sleepAtVideoEnd -> "播完即停"
        else -> "定时关闭"
    }

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            leadingIcon = {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, modifier = Modifier.size(15.dp))
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SLEEP_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("$minutes 分钟后暂停") },
                    onClick = {
                        expanded = false
                        viewModel.setSleepTimer(minutes)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text("播完当前视频后暂停") },
                onClick = {
                    expanded = false
                    viewModel.setSleepAtVideoEnd(true)
                },
            )
            if (state.sleepRemainingSeconds > 0 || state.sleepAtVideoEnd) {
                DropdownMenuItem(
                    text = { Text("取消定时") },
                    onClick = {
                        expanded = false
                        viewModel.cancelSleepTimer()
                    },
                )
            }
        }
    }
}

@Composable
private fun <T> MenuChip(
    label: String,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { if (options.isNotEmpty()) expanded = true },
            enabled = options.isNotEmpty(),
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (text, value) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun MetaText(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DanmakuSettingsSheet(
    state: VideoPlayerViewModel.UiState,
    viewModel: VideoPlayerViewModel,
    onDismiss: () -> Unit,
) {
    val settings = state.danmakuSettings
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("弹幕设置", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            SettingSlider("字号", settings.fontScale, 0.5f..2.0f, "%.1f×".format(settings.fontScale)) {
                viewModel.updateDanmakuSettings { s -> s.copy(fontScale = it) }
            }
            SettingSlider(
                "不透明度",
                settings.opacity,
                0.1f..1.0f,
                "${(settings.opacity * 100).toInt()}%",
            ) { viewModel.updateDanmakuSettings { s -> s.copy(opacity = it) } }
            SettingSlider(
                "滚动速度",
                settings.speedFactor,
                0.5f..2.5f,
                "%.1f×".format(settings.speedFactor),
            ) { viewModel.updateDanmakuSettings { s -> s.copy(speedFactor = it) } }
            SettingSlider(
                "显示区域",
                settings.displayAreaRatio,
                0.2f..1.0f,
                "${(settings.displayAreaRatio * 100).toInt()}% 屏高",
            ) { viewModel.updateDanmakuSettings { s -> s.copy(displayAreaRatio = it) } }
            SettingSlider(
                "屏蔽等级",
                settings.minWeight.toFloat(),
                0f..10f,
                if (settings.minWeight == 0) "不屏蔽" else "屏蔽权重 < ${settings.minWeight}",
                steps = 9,
            ) { viewModel.updateDanmakuSettings { s -> s.copy(minWeight = it.toInt()) } }

            Text(
                "屏蔽等级用的是服务端给每条弹幕打的权重（1-10），等级越高留下的弹幕越少",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                display,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}
