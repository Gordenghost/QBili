package com.qbili.ui.screen.download

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.qbili.R
import com.qbili.core.friendlyMessage
import com.qbili.data.download.OfflineDownloadService
import com.qbili.domain.download.*
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.screen.video.ApplyFullscreenWindow
import com.qbili.ui.screen.video.PlayerGestureLayer
import com.qbili.ui.screen.video.VideoPlayerControls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineDownloadsScreen(onBack: () -> Unit, onPlay: (String) -> Unit) {
    val container = LocalAppContainer.current
    val downloads = container.offlineDownloads
    val session by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val account = if (session.isLoggedIn) session.mid ?: 0 else 0
    val all by downloads.tasks.collectAsStateWithLifecycle()
    val loaded by downloads.loaded.collectAsStateWithLifecycle()
    val notice by downloads.notice.collectAsStateWithLifecycle()
    val tasks = all.filter { it.accountMid == account }.sortedByDescending { it.createdAt }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember(account) { mutableStateOf("") }
    var completedOnly by remember(account) { mutableStateOf(false) }
    var busy by remember(account) { mutableStateOf(false) }
    var deleteTarget by remember(account) { mutableStateOf<OfflineDownload?>(null) }
    var clear by remember(account) { mutableStateOf(false) }
    var resumeTarget by remember(account) { mutableStateOf<String?>(null) }
    var occupied by remember(account) { mutableLongStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(all, account) { occupied = withContext(Dispatchers.IO) { downloads.usage(account) } }
    LaunchedEffect(notice) { notice?.let { snackbar.showSnackbar(it); if (downloads.notice.value == it) downloads.consumeNotice() } }
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val visible = tasks.filter { task -> (!completedOnly || task.status == DownloadStatus.COMPLETE) &&
        words.all { "${task.title} ${task.part} ${task.author} av${task.aid}".contains(it, true) } }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (downloads.account() == account) downloads.showNotice(error.friendlyMessage()) }
            finally { if (downloads.account() == account) busy = false }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("离线缓存") }, navigationIcon = { BackButton(onBack) },
        actions = { TextButton(onClick = { clear = true }, enabled = tasks.isNotEmpty() && !busy) { Text("清空") } }) },
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("当前${if (account == 0L) "游客" else "账号"} · ${tasks.size} 条 · 占用 ${formatDownloadBytes(occupied)}")
                    Text("可用空间 ${formatDownloadBytes(downloads.store.availableBytes)} · 缓存仅保存在本应用私有目录",
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("搜索标题、UP、分 P、AV号") },
                        modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(!completedOnly, { completedOnly = false }, label = { Text("全部", color = MaterialTheme.colorScheme.onSurface) })
                        FilterChip(completedOnly, { completedOnly = true }, label = { Text("已完成", color = MaterialTheme.colorScheme.onSurface) })
                    }
                    if (!loaded) Text("缓存索引未就绪；读取失败时不会覆盖原有记录")
                    if (loaded && visible.isEmpty()) Text(if (tasks.isEmpty()) "暂无缓存，在视频详情页点击“离线缓存”添加" else "没有匹配的缓存")
                }
            }
            items(visible, key = { it.id }) { task ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(task.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${task.part} · ${task.qualityLabel} · ${task.author}", style = MaterialTheme.typography.bodySmall)
                        Text("${statusLabel(task.status)} · ${formatDownloadBytes(task.bytes)} / ${if (task.total > 0) formatDownloadBytes(task.total) else "待获取大小"}")
                        if (task.status == DownloadStatus.DOWNLOADING && task.total > 0)
                            LinearProgressIndicator(progress = { (task.bytes.toFloat() / task.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else if (task.status == DownloadStatus.DOWNLOADING) LinearProgressIndicator(Modifier.fillMaxWidth())
                        task.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row {
                            if (task.status == DownloadStatus.COMPLETE) TextButton(onClick = { onPlay(task.id) }, enabled = !busy) { Text("离线播放") }
                            else if (task.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                                TextButton(onClick = { action { downloads.pause(task.id) } }, enabled = !busy) { Text("暂停") }
                            else TextButton(onClick = { resumeTarget = task.id }, enabled = !busy) { Text("继续") }
                            TextButton(onClick = { deleteTarget = task }, enabled = !busy) { Text("删除") }
                        }
                    }
                }
            }
        }
    }
    if (deleteTarget != null || clear) AlertDialog(onDismissRequest = { deleteTarget = null; clear = false },
        title = { Text(if (clear) "清空当前账号全部缓存？" else "删除缓存？") },
        text = { Text(if (clear) "将停止并删除当前账号全部任务和音视频文件，包括未显示的搜索结果；不会删除其他账号的缓存。"
            else "将停止此任务并删除已下载的音视频文件，无法撤销。") },
        confirmButton = { TextButton(onClick = {
            val target = deleteTarget; val clearAll = clear; deleteTarget = null; clear = false
            action { if (clearAll) downloads.clear(account) else target?.let { downloads.delete(it.id, account) } }
        }) { Text("删除") } }, dismissButton = { TextButton(onClick = { deleteTarget = null; clear = false }) { Text("取消") } })
    resumeTarget?.let { id -> DownloadNetworkDialog(onDismiss = { resumeTarget = null }, onConfirm = {
        resumeTarget = null
        action {
            downloads.resume(id)
            try { withContext(Dispatchers.Main) { OfflineDownloadService.start(context) } }
            catch (error: Exception) { downloads.pause(id); throw error }
        }
    }) }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OfflineDownloadAddScreen(aid: Long, initialCid: Long, onBack: () -> Unit, onQueued: () -> Unit) {
    val container = LocalAppContainer.current
    val session by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var video by remember(session, aid) { mutableStateOf<DownloadVideo?>(null) }
    var cid by remember(session, aid) { mutableLongStateOf(initialCid) }
    var options by remember(session, aid, cid) { mutableStateOf<List<DownloadOption>>(emptyList()) }
    var selected by remember(session, aid, cid) { mutableIntStateOf(0) }
    var error by remember(session, aid, cid) { mutableStateOf<String?>(null) }
    var selectingPage by remember(session) { mutableStateOf(false) }
    var confirming by remember(session) { mutableStateOf(false) }
    var adding by remember(session) { mutableStateOf(false) }
    val page = video?.pages?.firstOrNull { it.cid == cid }
    LaunchedEffect(session, aid) {
        try {
            val loaded = container.offlineVideoRepository.video(session, aid)
            check(initialCid == 0L || loaded.pages.any { it.cid == initialCid }) { "目标分 P 已不在当前目录中" }
            video = loaded
            if (cid == 0L) cid = loaded.pages.first().cid
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.friendlyMessage() }
    }
    LaunchedEffect(session, aid, page) {
        options = emptyList()
        if (page != null) try {
            options = container.offlineVideoRepository.options(session, aid, page)
            selected = options.first().quality
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.friendlyMessage() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("添加离线缓存") }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(video?.title ?: "正在检查视频权限…", style = MaterialTheme.typography.titleMedium)
            Text("仅缓存平台明确允许下载、完整可播放的普通视频；本阶段支持 AVC 视频和标准音轨，不缓存番剧、课程、试看、付费或加密内容。")
            page?.let { TextButton(onClick = { selectingPage = true }, enabled = !adding) { Text("分 P：${it.number} · ${it.title}") } }
            if (options.isEmpty() && error == null) CircularProgressIndicator()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option -> FilterChip(selected == option.quality, { selected = option.quality }, enabled = !adding,
                    label = { Text(option.label, color = if (selected == option.quality) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }) }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { confirming = true }, enabled = page != null && options.any { it.quality == selected } && !adding) {
                Text(if (adding) "正在加入…" else "缓存当前分 P")
            }
            Text("音视频分别下载，全部完整后才能离线播放。暂停保留进度；服务器没有提供可靠续传校验时会重新下载当前轨道。")
        }
    }
    if (selectingPage) AlertDialog(onDismissRequest = { selectingPage = false }, title = { Text("选择分 P") }, text = {
        LazyColumn(Modifier.heightIn(max = 320.dp)) { items(video?.pages.orEmpty(), key = { it.cid }) { item ->
            TextButton(onClick = { cid = item.cid; selectingPage = false }, modifier = Modifier.fillMaxWidth()) {
                Text("P${item.number} ${item.title}", color = MaterialTheme.colorScheme.onSurface)
            }
        } }
    }, confirmButton = { TextButton(onClick = { selectingPage = false }) { Text("关闭") } })
    if (confirming) DownloadNetworkDialog(onDismiss = { confirming = false }, onConfirm = {
        confirming = false
        val detail = video; val chosen = options.firstOrNull { it.quality == selected }; val target = page
        if (detail != null && chosen != null && target != null) {
            val captured = session
            adding = true
            scope.launch {
                try {
                    val id = withContext(Dispatchers.IO) { container.offlineDownloads.enqueue(captured, detail, target, chosen) }
                    check(container.sessionManager.loginCookies.value == captured) { "账号已变化" }
                    try { OfflineDownloadService.start(context) }
                    catch (failure: Exception) { withContext(Dispatchers.IO) { container.offlineDownloads.pause(id) }; throw failure }
                    onQueued()
                } catch (cancel: CancellationException) { throw cancel }
                catch (failure: Exception) { if (container.sessionManager.loginCookies.value == captured) error = failure.friendlyMessage() }
                finally { if (container.sessionManager.loginCookies.value == captured) adding = false }
            }
        }
    })
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePlayerScreen(id: String, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val session by container.sessionManager.loginCookies.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val tasks by container.offlineDownloads.tasks.collectAsStateWithLifecycle()
    val task = tasks.firstOrNull { it.id == id && it.accountMid == container.offlineDownloads.account() }
    val player = remember(id) { ExoPlayer.Builder(context)
        .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true).setHandleAudioBecomingNoisy(true).build() }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var fullscreen by remember(id) { mutableStateOf(false) }
    var controls by remember(id) { mutableStateOf(true) }
    var requested by remember(id) { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var scrubbing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    var released by remember(id) { mutableStateOf(false) }
    val originalSession = remember(id) { session }
    ApplyFullscreenWindow(fullscreen)
    fun leave() { player.pause(); player.clearMediaItems(); playerView?.player = null; onBack() }
    fun togglePlay() {
        if (player.playWhenReady) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }
    androidx.activity.compose.BackHandler { if (fullscreen) fullscreen = false else leave() }
    LaunchedEffect(id, session, task?.status) {
        if (session != originalSession || task?.status != DownloadStatus.COMPLETE) {
            player.pause(); player.clearMediaItems(); error = "账号已变化或缓存已删除，请返回缓存列表"; return@LaunchedEffect
        }
        try {
            val files = withContext(Dispatchers.IO) { container.offlineDownloads.playable(id) }
            check(container.sessionManager.loginCookies.value == originalSession) { "账号已变化" }
            val factory = ProgressiveMediaSource.Factory(FileDataSource.Factory())
            val video = factory.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(files.first)))
            val audio = factory.createMediaSource(MediaItem.fromUri(android.net.Uri.fromFile(files.second)))
            player.setMediaSource(MergingMediaSource(video, audio)); player.prepare()
        } catch (cancel: CancellationException) { throw cancel }
        catch (failure: Exception) { error = failure.friendlyMessage() }
    }
    DisposableEffect(player, lifecycle) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { requested = playWhenReady }
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) player.pause() }
            override fun onPlayerError(failure: androidx.media3.common.PlaybackException) { error = "本机缓存播放失败：${failure.friendlyMessage()}" }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) { player.pause(); playerView?.player = null }
            if (event == Lifecycle.Event.ON_RESUME) playerView?.player = player
        }
        player.addListener(listener); lifecycle.addObserver(observer)
        onDispose { released = true; lifecycle.removeObserver(observer); player.removeListener(listener); playerView?.player = null; player.release() }
    }
    LaunchedEffect(controls, interaction, scrubbing, pressed) { if (controls && !scrubbing && !pressed) { delay(2500); controls = false } }
    Column(Modifier.fillMaxSize()) {
        Box((if (fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9)).fillMaxWidth().background(Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = {
                (LayoutInflater.from(it).inflate(R.layout.video_player_view, null, false) as PlayerView).apply {
                    this.player = player; useController = false; playerView = this
                }
            }, update = { it.player = if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && session == originalSession) player else null },
                onRelease = { it.player = null; if (playerView === it) playerView = null })
            if (error == null) PlayerGestureLayer(player.duration, { player.currentPosition },
                onSingleTap = { controls = !controls; interaction++ }, onTogglePlay = ::togglePlay,
                onSeekTo = { player.seekTo(it) }, onGestureStart = { interaction++ },
                onSpeedBoostStart = { if (!released) player.setPlaybackSpeed(2f) },
                onSpeedBoostEnd = { if (!released) player.setPlaybackSpeed(1f) })
            if (controls) {
                VideoPlayerControls(player, requested, onTogglePlay = ::togglePlay,
                    onSeekBy = { player.seekTo((player.currentPosition + it).coerceIn(0, player.duration.coerceAtLeast(0))) },
                    onSeekTo = { player.seekTo(it) }, onInteraction = { interaction++ },
                    onScrubbingChange = { scrubbing = it }, onPressedChange = { pressed = it })
                Row(Modifier.align(Alignment.TopStart).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (fullscreen) fullscreen = false else leave() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = Color.White) }
                    Text("离线播放", color = Color.White, modifier = Modifier.weight(1f))
                    IconButton(onClick = { fullscreen = !fullscreen }) { Icon(Icons.Outlined.Fullscreen, "切换全屏", tint = Color.White) }
                }
            }
        }
        if (!fullscreen) Column(Modifier.padding(16.dp)) {
            Text(task?.title ?: "离线视频", style = MaterialTheme.typography.titleMedium)
            Text("${task?.part.orEmpty()} · ${task?.qualityLabel.orEmpty()}")
            Text("只读取本机音视频文件，不请求播放地址、评论或弹幕；返回不会自动开播。")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = ::leave) { Text("返回缓存列表") }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }

@Composable
private fun DownloadNetworkDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val context = LocalContext.current
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) android.widget.Toast.makeText(context, "未允许通知，下载进度仍可在离线缓存页查看", android.widget.Toast.LENGTH_LONG).show()
        onConfirm()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("开始缓存？") },
        text = { Text("下载使用当前网络，移动网络可能产生较多流量。应用在后台时通过系统前台服务继续下载；划掉应用、账号变化或系统限制后暂停，需要手动继续。") },
        confirmButton = { TextButton(onClick = {
            if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context,
                    android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                permission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else onConfirm()
        }) { Text("开始下载") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun statusLabel(status: DownloadStatus): String = when (status) {
    DownloadStatus.QUEUED -> "等待下载"
    DownloadStatus.DOWNLOADING -> "正在下载"
    DownloadStatus.PAUSED -> "已暂停"
    DownloadStatus.FAILED -> "下载失败"
    DownloadStatus.COMPLETE -> "已完成"
}
