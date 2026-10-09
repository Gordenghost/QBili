package com.qbili.ui.screen.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.qbili.core.QBiliLog
import com.qbili.core.friendlyMessage
import com.qbili.data.remote.ApiConstants
import com.qbili.data.repository.DanmakuRepository
import com.qbili.data.repository.InteractionRepository
import com.qbili.data.repository.VideoRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.AudioQuality
import com.qbili.domain.model.DanmakuItem
import com.qbili.domain.model.DanmakuSettings
import com.qbili.domain.model.DashResult
import com.qbili.domain.model.FavFolder
import com.qbili.domain.model.favoriteChanges
import com.qbili.domain.model.PlayableStream
import com.qbili.domain.model.QualityOption
import com.qbili.domain.model.VideoCodec
import com.qbili.domain.model.VideoDetail
import com.qbili.domain.model.VideoInteraction
import com.qbili.domain.model.VideoQuality
import com.qbili.domain.player.StreamSelector
import com.qbili.domain.player.togglePlayback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class VideoPlayerViewModel(
    private val videoRepository: VideoRepository,
    private val danmakuRepository: DanmakuRepository,
    private val interactionRepository: InteractionRepository,
    private val sessionManager: SessionManager,
    context: Context,
) : ViewModel() {

    private val appContext = context.applicationContext

    data class UiState(
        val loading: Boolean = true,
        val error: String? = null,
        val detail: VideoDetail? = null,
        /** 当前分 P 在 [VideoDetail.pages] 里的下标 */
        val pageIndex: Int = 0,
        val qualities: List<QualityOption> = emptyList(),
        val audioQualities: List<AudioQuality> = emptyList(),
        val stream: PlayableStream? = null,
        val fullscreen: Boolean = false,
        val speed: Float = 1.0f,
        /** 定时关闭剩余秒数，0 表示未设置 */
        val sleepRemainingSeconds: Int = 0,
        /** 播完当前视频就停 */
        val sleepAtVideoEnd: Boolean = false,
        val danmaku: List<DanmakuItem> = emptyList(),
        val danmakuSettings: DanmakuSettings = DanmakuSettings(),
        val isPlaying: Boolean = false,
        val playbackRequested: Boolean = false,
        val interaction: VideoInteraction = VideoInteraction(),
        val favFolders: List<FavFolder> = emptyList(),
        val favFoldersLoading: Boolean = false,
        val favFoldersError: String? = null,
        val favoritesSaved: Boolean = false,
        /** 一次性提示（点赞成功/需要登录等），显示后由 UI 清空 */
        val toast: String? = null,
        val interactionBusy: Boolean = false,
        val loggedIn: Boolean = false,
        /** 点踩只有 App 端接口，需要移动端登录拿到的 access_key */
        val canDislike: Boolean = false,
        /** 切画质/切分P 时的短暂加载，与首次加载区分开，避免整页闪白 */
        val switching: Boolean = false,
    ) {
        val currentPage get() = detail?.pages?.getOrNull(pageIndex)
        val isLoggedIn: Boolean get() = loggedIn
        val availableCodecs: List<VideoCodec>
            get() = qualities.firstOrNull { it.qn == stream?.quality }?.availableCodecs.orEmpty()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** 缓存当前分 P 的 DASH 轨道，切编码时无需重新请求 playurl */
    private var dash: DashResult? = null
    private var videoId: String = ""
    private var desiredQn: Int = VideoQuality.Q1080P.qn
    private var desiredAudioId: Int = 0
    private var sleepJob: Job? = null
    private var relationJob: Job? = null
    private var watchLaterLookup: Job? = null
    /** 长按倍速前的原速度，松手要还原 */
    private var speedBeforeBoost: Float = 1.0f

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(appContext)
            .setLoadControl(DefaultLoadControl())
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        _uiState.update { it.copy(playbackRequested =
                            player.playWhenReady && player.playbackState != Player.STATE_ENDED) }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        _uiState.update { it.copy(isPlaying = playing) }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED && _uiState.value.sleepAtVideoEnd) {
                            pause()
                            _uiState.update { it.copy(sleepAtVideoEnd = false) }
                            QBiliLog.i(TAG, "播完当前视频，定时关闭生效")
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        QBiliLog.w(TAG, "播放失败: ${error.errorCodeName} ${error.message}")
                        _uiState.update {
                            it.copy(error = "播放失败：${error.errorCodeName}", switching = false)
                        }
                    }
                })
            }
    }

    fun load(videoId: String) {
        if (this.videoId == videoId && _uiState.value.detail != null) return
        this.videoId = videoId

        viewModelScope.launch {
            _uiState.update { UiState(loading = true) }
            try {
                val detail = videoRepository.detail(videoId)
                _uiState.update { it.copy(detail = detail, loading = false) }
                QBiliLog.i(TAG, "详情加载完成: ${detail.title.take(20)} 分P=${detail.pages.size}")
                refreshInteraction(detail.aid, detail.bvid)
                if (sessionManager.loginCookies.value.isLoggedIn) {
                    watchLaterLookup?.cancel()
                    watchLaterLookup = viewModelScope.launch {
                        runCatching { interactionRepository.isInWatchLater(detail.aid) }
                            .onSuccess { inList ->
                                _uiState.update { state ->
                                    state.copy(interaction = state.interaction.copy(inWatchLater = inList))
                                }
                            }
                            .onFailure { QBiliLog.w(TAG, "稍后再看状态加载失败: ${it.message}") }
                    }
                }
                playPage(0)
            } catch (e: Exception) {
                QBiliLog.w(TAG, "详情加载失败: ${e.message}")
                _uiState.update { it.copy(loading = false, error = e.friendlyMessage()) }
            }
        }
    }

    fun selectPage(index: Int) {
        val pages = _uiState.value.detail?.pages ?: return
        if (index !in pages.indices || index == _uiState.value.pageIndex) return
        viewModelScope.launch { playPage(index) }
    }

    /** 切画质要重新请求 playurl：登录后服务端才会下发更高档轨道 */
    fun selectQuality(qn: Int) {
        if (qn == _uiState.value.stream?.quality) return
        desiredQn = qn
        viewModelScope.launch { refreshStream(keepPosition = true) }
    }

    /** 切编码只需在已缓存的轨道里重选，不用再打接口 */
    fun selectCodec(codec: VideoCodec) {
        val current = dash ?: return
        if (codec == _uiState.value.stream?.codec) return
        val stream = StreamSelector.select(current, desiredQn, codec, desiredAudioId) ?: return
        applyStream(stream, keepPosition = true)
    }

    /** 切音质同样只需在已缓存的轨道里重选 */
    fun selectAudioQuality(audioId: Int) {
        val current = dash ?: return
        if (audioId == _uiState.value.stream?.audioId) return
        desiredAudioId = audioId
        val stream = StreamSelector.select(
            current,
            desiredQn,
            _uiState.value.stream?.codec,
            audioId,
        ) ?: return
        applyStream(stream, keepPosition = true)
    }

    // ---------- 全屏 / 倍速 ----------

    fun setFullscreen(value: Boolean) = _uiState.update { it.copy(fullscreen = value) }

    fun toggleFullscreen() = _uiState.update { it.copy(fullscreen = !it.fullscreen) }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(speed = speed) }
    }

    /**
     * 长按临时倍速。松手要还原到长按前的速度，
     * 所以不能直接假设原速是 1.0——用户可能本来就设了 1.5 倍。
     */
    fun startSpeedBoost(factor: Float = 2.0f) {
        speedBeforeBoost = _uiState.value.speed
        setSpeed(factor)
    }

    fun stopSpeedBoost() {
        if (_uiState.value.speed != speedBeforeBoost) setSpeed(speedBeforeBoost)
    }

    fun togglePlayPause() {
        player.togglePlayback()
    }

    fun seekBy(deltaMillis: Long) {
        val target = (player.currentPosition + deltaMillis)
            .coerceIn(0L, player.duration.coerceAtLeast(0L))
        player.seekTo(target)
    }

    // ---------- 互动 ----------

    private fun refreshInteraction(aid: Long, bvid: String) {
        val loggedIn = sessionManager.loginCookies.value.isLoggedIn
        _uiState.update {
            it.copy(loggedIn = loggedIn, canDislike = interactionRepository.canDislike)
        }
        if (!loggedIn) return

        relationJob?.cancel()
        relationJob = viewModelScope.launch {
            runCatching { interactionRepository.loadState(aid, bvid) }
                .onSuccess { state ->
                    _uiState.update { current ->
                        current.copy(interaction = state.copy(inWatchLater = current.interaction.inWatchLater))
                    }
                }
                .onFailure { QBiliLog.w(TAG, "互动状态加载失败: ${it.message}") }
        }
    }

    fun consumeToast() = _uiState.update { it.copy(toast = null) }

    /**
     * 所有互动都要登录。统一在这里拦一次，避免每个按钮各写一遍，
     * 也避免未登录时打出一串必然失败的请求。
     */
    private fun runInteraction(
        successMessage: String? = null,
        refreshAfterSuccess: Boolean = true,
        block: suspend (detail: VideoDetail) -> Unit,
    ) {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.interactionBusy) return
        if (!sessionManager.loginCookies.value.isLoggedIn) {
            _uiState.update { it.copy(toast = "请先登录", loggedIn = false) }
            return
        }
        relationJob?.cancel()
        _uiState.update { it.copy(interactionBusy = true) }
        viewModelScope.launch {
            var succeeded = false
            try {
                block(detail)
                succeeded = true
                successMessage?.let { msg -> _uiState.update { it.copy(toast = msg) } }
            } catch (e: Exception) {
                QBiliLog.w(TAG, "互动操作失败: ${e.message}")
                _uiState.update { it.copy(toast = e.friendlyMessage()) }
            } finally {
                _uiState.update { it.copy(interactionBusy = false) }
                // 其余互动回填服务端状态；点赞成功时保留刚确认的本地结果，避免旧响应覆盖。
                if (!succeeded || refreshAfterSuccess) {
                    _uiState.value.detail?.let { refreshInteraction(it.aid, it.bvid) }
                }
            }
        }
    }

    fun toggleLike() = runInteraction(refreshAfterSuccess = false) {
        val liked = _uiState.value.interaction.liked
        interactionRepository.setLike(it.bvid, !liked)
        _uiState.update { state -> state.copy(interaction = state.interaction.withLike(!liked)) }
    }

    fun toggleDislike() {
        if (_uiState.value.interactionBusy) return
        if (!interactionRepository.canDislike) {
            _uiState.update {
                it.copy(toast = "点踩只有 B 站移动端接口支持，请用短信或密码登录后重试")
            }
            return
        }
        runInteraction(refreshAfterSuccess = false) {
            val disliked = _uiState.value.interaction.disliked
            interactionRepository.setDislike(it.aid, !disliked)
            _uiState.update { state -> state.copy(interaction = state.interaction.withDislike(!disliked)) }
        }
    }

    fun addCoin(count: Int, alsoLike: Boolean) = runInteraction("已投 $count 枚硬币") {
        interactionRepository.addCoin(it.bvid, count, alsoLike)
    }

    fun loadFavFolders(): Boolean {
        if (_uiState.value.interactionBusy) return false
        val detail = _uiState.value.detail ?: return false
        val mid = sessionManager.loginCookies.value.mid
        if (mid == null) {
            _uiState.update { it.copy(toast = "请先登录") }
            return false
        }
        _uiState.update {
            it.copy(favFolders = emptyList(), favFoldersLoading = true,
                favFoldersError = null, favoritesSaved = false)
        }
        viewModelScope.launch {
            runCatching { interactionRepository.favFolders(mid, detail.aid) }
                .onSuccess { folders ->
                    _uiState.update { it.copy(favFolders = folders, favFoldersLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(favFoldersError = e.friendlyMessage(), favFoldersLoading = false) }
                }
        }
        return true
    }

    fun consumeFavoritesSaved() = _uiState.update { it.copy(favoritesSaved = false) }

    /** @param selectedIds 用户在弹窗里勾选后的最终收藏夹集合 */
    fun applyFavorites(selectedIds: Set<Long>) = runInteraction("收藏已更新", refreshAfterSuccess = false) { detail ->
        val changes = favoriteChanges(_uiState.value.favFolders, selectedIds)
        interactionRepository.updateFavorites(
            aid = detail.aid,
            addIds = changes.addIds,
            removeIds = changes.removeIds,
        )
        _uiState.update { state ->
            state.copy(
                favFolders = state.favFolders.map { it.copy(containsVideo = it.id in selectedIds) },
                interaction = state.interaction.copy(favorited = selectedIds.isNotEmpty()),
                favoritesSaved = true,
            )
        }
    }

    fun toggleWatchLater() {
        watchLaterLookup?.cancel()
        runInteraction {
            if (_uiState.value.interaction.inWatchLater) {
                interactionRepository.removeFromWatchLater(it.aid)
                _uiState.update { s -> s.copy(interaction = s.interaction.copy(inWatchLater = false)) }
            } else {
                interactionRepository.addToWatchLater(it.bvid)
                // relation 不返回稍后再看状态；首次读列表，操作成功后本地同步。
                _uiState.update { s -> s.copy(interaction = s.interaction.copy(inWatchLater = true)) }
            }
        }
    }

    fun toggleFollow() = runInteraction {
        interactionRepository.setFollow(it.authorMid, !_uiState.value.interaction.following)
    }

    /** 分享计数上报，真正的分享由 UI 层调系统分享面板完成 */
    fun reportShare() {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch { interactionRepository.reportShare(detail.bvid) }
    }

    // ---------- 定时关闭 ----------

    /**
     * @param minutes 分钟数；0 表示取消
     */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepAtVideoEnd = false, sleepRemainingSeconds = minutes * 60) }
        if (minutes <= 0) return

        sleepJob = viewModelScope.launch {
            while (isActive && _uiState.value.sleepRemainingSeconds > 0) {
                delay(1_000)
                _uiState.update { it.copy(sleepRemainingSeconds = it.sleepRemainingSeconds - 1) }
            }
            if (isActive) {
                player.pause()
                QBiliLog.i(TAG, "定时关闭触发，已暂停播放")
            }
        }
    }

    /** 播完当前视频即停。用播放器的结束回调判断，不靠倒计时估算时长 */
    fun setSleepAtVideoEnd(enabled: Boolean) {
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepAtVideoEnd = enabled, sleepRemainingSeconds = 0) }
    }

    fun cancelSleepTimer() {
        sleepJob?.cancel()
        _uiState.update { it.copy(sleepRemainingSeconds = 0, sleepAtVideoEnd = false) }
    }

    fun toggleDanmaku() = updateDanmakuSettings {
        it.copy(enabled = !it.enabled)
    }

    fun updateDanmakuSettings(transform: (DanmakuSettings) -> DanmakuSettings) {
        _uiState.update { it.copy(danmakuSettings = transform(it.danmakuSettings)) }
    }

    fun retry() {
        val id = videoId
        videoId = ""
        load(id)
    }

    private suspend fun playPage(index: Int) {
        val detail = _uiState.value.detail ?: return
        val page = detail.pages.getOrNull(index) ?: return
        _uiState.update { it.copy(pageIndex = index, switching = true, danmaku = emptyList()) }

        // 弹幕与播放地址互不依赖，并发拉；弹幕失败不该拖累播放
        viewModelScope.launch {
            runCatching { danmakuRepository.load(page.cid, page.durationSeconds) }
                .onSuccess { list -> _uiState.update { it.copy(danmaku = list) } }
                .onFailure { QBiliLog.w(TAG, "弹幕加载失败: ${it.message}") }
        }
        refreshStream(keepPosition = false)
    }

    private suspend fun refreshStream(keepPosition: Boolean) {
        val page = _uiState.value.currentPage ?: return
        _uiState.update { it.copy(switching = true, error = null) }
        try {
            val playurl = videoRepository.playurl(page.cid, videoId, desiredQn)
            val dashResult = playurl.dash
                ?: throw IllegalStateException("接口未返回 DASH 流，可能是充电专属或地区限制视频")
            dash = dashResult

            val qualities = StreamSelector.availableQualities(dashResult)
            val stream = StreamSelector.select(
                dash = dashResult,
                desiredQn = desiredQn,
                desiredCodec = _uiState.value.stream?.codec,
                desiredAudioId = desiredAudioId,
            ) ?: throw IllegalStateException("没有可播放的视频轨道")

            _uiState.update {
                it.copy(
                    qualities = qualities,
                    audioQualities = StreamSelector.availableAudioQualities(dashResult),
                )
            }
            applyStream(stream, keepPosition)
            QBiliLog.i(
                TAG,
                "选轨: ${stream.qualityLabel} ${stream.codec?.label} " +
                    "${stream.width}x${stream.height} 可选画质=${qualities.map { it.qn }}",
            )
        } catch (e: Exception) {
            QBiliLog.w(TAG, "取播放地址失败: ${e.message}")
            _uiState.update { it.copy(switching = false, error = e.friendlyMessage()) }
        }
    }

    private fun applyStream(stream: PlayableStream, keepPosition: Boolean) {
        val resumeAt = if (keepPosition) player.currentPosition else 0L
        val resumePlayback = if (keepPosition) player.playWhenReady else true
        player.setMediaSource(buildMediaSource(stream))
        player.prepare()
        if (resumeAt > 0) player.seekTo(resumeAt)
        player.playWhenReady = resumePlayback
        _uiState.update { it.copy(stream = stream, switching = false) }
    }

    /**
     * B 站 DASH 的音视频是**两条独立的完整 fMP4**，各有自己的 baseUrl，
     * 必须用 [MergingMediaSource] 合轨。喂 DashMediaSource 是错的——
     * 那需要一份 MPD 清单，而接口不提供。
     *
     * 另外 CDN 强校验 Referer 与 UA，缺了直接 403。
     */
    private fun buildMediaSource(stream: PlayableStream): MediaSource {
        val factory = DefaultHttpDataSource.Factory()
            .setUserAgent(ApiConstants.USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to ApiConstants.WEB_REFERER))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)

        val video = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(MediaItem.fromUri(stream.videoUrl))

        val audio = stream.audioUrl?.let { url ->
            ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        }

        return if (audio == null) video else MergingMediaSource(video, audio)
    }

    override fun onCleared() {
        sleepJob?.cancel()
        player.release()
        super.onCleared()
    }

    companion object {
        private const val TAG = "VideoPlayerVM"

        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    VideoPlayerViewModel(
                        videoRepository = container.videoRepository,
                        danmakuRepository = container.danmakuRepository,
                        interactionRepository = container.interactionRepository,
                        sessionManager = container.sessionManager,
                        context = context,
                    )
                }
            }
    }
}
