package com.qbili.ui.screen.season

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.qbili.core.QBiliLog
import com.qbili.core.friendlyMessage
import com.qbili.data.local.SeasonProgressStorage
import com.qbili.data.repository.DanmakuRepository
import com.qbili.data.repository.SeasonRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.DanmakuItem
import com.qbili.domain.model.DanmakuSettings
import com.qbili.domain.model.QualityOption
import com.qbili.domain.model.SeasonDetail
import com.qbili.domain.model.SeasonEpisode
import com.qbili.domain.model.SeasonPlayback
import com.qbili.domain.model.SeasonProgress
import com.qbili.domain.model.VideoCodec
import com.qbili.domain.model.resumePosition
import com.qbili.domain.player.SeasonStream
import com.qbili.domain.player.SeasonPlaybackIntent
import com.qbili.domain.player.StreamSelector
import com.qbili.domain.player.selectStream
import com.qbili.domain.player.togglePlayback
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class SeasonViewModel(
    private val repository: SeasonRepository,
    private val danmakuRepository: DanmakuRepository,
    private val sessionManager: SessionManager,
    private val progressStore: SeasonProgressStorage,
    private val persistProgress: (Long, SeasonProgress) -> Unit,
    context: Context,
) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val detail: SeasonDetail? = null,
        val episode: SeasonEpisode? = null,
        val error: String? = null,
        val switching: Boolean = false,
        val stream: SeasonStream? = null,
        val qualities: List<QualityOption> = emptyList(),
        val preview: Boolean = false,
        val fullscreen: Boolean = false,
        val speed: Float = 1f,
        val playing: Boolean = false,
        val playbackRequested: Boolean = false,
        val danmaku: List<DanmakuItem> = emptyList(),
        val danmakuSettings: DanmakuSettings = DanmakuSettings(),
        val loggedIn: Boolean = false,
        val followBusy: Boolean = false,
        val feedback: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var seasonId = 0L
    private var detailJob: Job? = null
    private var playJob: Job? = null
    private var danmakuJob: Job? = null
    private var playback: SeasonPlayback? = null
    private var savedProgress: SeasonProgress? = null
    private var desiredQuality = 80
    private var boostOriginalSpeed = 1f
    private val playbackIntent = SeasonPlaybackIntent()
    private var followJob: Job? = null

    val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(playing = isPlaying) }
            }
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _uiState.update { it.copy(playbackRequested = playWhenReady) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    saveProgress()
                    if (_uiState.value.preview) {
                        _uiState.update { it.copy(feedback = "试看已结束，完整观看可能需要登录、大会员或购买权限") }
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _uiState.update { it.copy(error = "播放失败：${error.errorCodeName}，可重试或切换剧集", switching = false) }
            }
        })
    }

    init {
        viewModelScope.launch {
            var previousLogin: Pair<Boolean, Long?>? = null
            sessionManager.loginCookies.map { it.isLoggedIn to it.mid }.distinctUntilChanged().collect { login ->
                val changed = previousLogin != null && previousLogin != login
                previousLogin = login
                _uiState.update { it.copy(loggedIn = login.first) }
                if (changed && seasonId > 0 && _uiState.value.detail != null) reload()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) saveProgress()
            }
        }
    }

    fun load(id: Long) {
        if (seasonId == id && _uiState.value.detail != null) return
        if (seasonId != id) {
            saveProgress()
            player.stop()
            player.clearMediaItems()
            savedProgress = null
            playback = null
            _uiState.update { UiState(loggedIn = it.loggedIn) }
        }
        seasonId = id
        reload()
    }

    fun reload() {
        saveProgress()
        detailJob?.cancel()
        playJob?.cancel()
        danmakuJob?.cancel()
        followJob?.cancel()
        val autoPlay = if (_uiState.value.episode == null) true else playbackIntent.requested
        playbackIntent.setRequested(autoPlay)
        player.pause()
        val previousEpisodeId = _uiState.value.episode?.id
        _uiState.update { it.copy(loading = true, switching = false, error = null, followBusy = false,
            playbackRequested = playbackIntent.shouldPlay) }
        val requestedSeasonId = seasonId
        detailJob = viewModelScope.launch {
            try {
                val detail = repository.detail(requestedSeasonId)
                val stored = try { progressStore.read(requestedSeasonId) } catch (error: IOException) { null }
                ensureActive()
                if (requestedSeasonId != seasonId) return@launch
                if (savedProgress == null || (stored?.updatedAt ?: 0L) > requireNotNull(savedProgress).updatedAt) {
                    savedProgress = stored
                }
                ensureActive()
                _uiState.update { it.copy(detail = detail, loading = false, followBusy = false) }
                val selected = detail.episodes.firstOrNull { it.id == previousEpisodeId }
                    ?: detail.episodes.firstOrNull { it.id == savedProgress?.episodeId }
                    ?: detail.episodes.firstOrNull { it.cid > 0 }
                    ?: detail.episodes.firstOrNull()
                if (selected != null) {
                    selectEpisode(selected.id, force = true, autoPlay = playbackIntent.requested)
                } else {
                    player.stop()
                    player.clearMediaItems()
                    playback = null
                    _uiState.update { it.copy(episode = null, stream = null, qualities = emptyList(),
                        danmaku = emptyList(), preview = false) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, switching = false, error = error.friendlyMessage()) }
            }
        }
    }

    fun selectEpisode(id: Long, force: Boolean = false, autoPlay: Boolean = true) {
        if (_uiState.value.loading) return
        val episode = _uiState.value.detail?.episodes?.firstOrNull { it.id == id } ?: return
        if (!force && _uiState.value.episode?.id == id && _uiState.value.stream != null) return
        saveProgress()
        playJob?.cancel()
        danmakuJob?.cancel()
        player.pause()
        player.stop()
        player.clearMediaItems()
        playback = null
        playbackIntent.setRequested(autoPlay)
        player.playWhenReady = playbackIntent.shouldPlay
        _uiState.update { it.copy(episode = episode, stream = null, qualities = emptyList(),
            switching = true, preview = false, error = null, danmaku = emptyList(),
            playbackRequested = playbackIntent.shouldPlay) }
        val resume = savedProgress?.resumePosition(episode) ?: 0L
        playJob = viewModelScope.launch { fetchPlayback(episode, resume) }
        if (episode.cid > 0 && !episode.areaLimited && _uiState.value.detail?.areaLimited != true) {
            danmakuJob = viewModelScope.launch {
                try {
                    val items = danmakuRepository.load(episode.cid, episode.durationSeconds)
                    ensureActive()
                    if (_uiState.value.episode?.id == episode.id) _uiState.update { it.copy(danmaku = items) }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    QBiliLog.w("SeasonPlayer", "剧集弹幕加载失败：${error.message}")
                }
            }
        }
    }

    private suspend fun fetchPlayback(episode: SeasonEpisode, resume: Long) {
        try {
            if (_uiState.value.detail?.areaLimited == true) {
                throw IllegalStateException("该番剧／影视在当前地区暂不可播放")
            }
            val result = repository.playurl(episode, desiredQuality)
            currentCoroutineContext().ensureActive()
            if (_uiState.value.episode?.id != episode.id) return
            val stream = result.selectStream(desiredQuality, _uiState.value.stream?.dash?.codec)
                ?: throw IllegalStateException("剧集未提供可播放的轨道")
            playback = result
            val qualities = result.playurl.dash?.let { StreamSelector.availableQualities(it) }.orEmpty()
            _uiState.update { it.copy(preview = result.preview, qualities = qualities) }
            applyStream(stream, if (result.preview) 0L else resume)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update { it.copy(switching = false, error = error.friendlyMessage()) }
        }
    }

    private fun applyStream(stream: SeasonStream, resume: Long) {
        val episode = _uiState.value.episode ?: return
        player.setMediaSource(buildSeasonMediaSource(stream, episode.id))
        player.prepare()
        if (resume > 0) player.seekTo(resume)
        player.setPlaybackSpeed(_uiState.value.speed)
        player.playWhenReady = playbackIntent.shouldPlay
        _uiState.update { it.copy(stream = stream, switching = false, error = null) }
    }

    fun selectQuality(quality: Int) {
        val episode = _uiState.value.episode ?: return
        if (_uiState.value.loading || _uiState.value.switching || quality == _uiState.value.stream?.quality) return
        desiredQuality = quality
        val resume = player.currentPosition.coerceAtLeast(0L)
        playbackIntent.setRequested(player.playWhenReady)
        playJob?.cancel()
        _uiState.update { it.copy(switching = true, error = null) }
        playJob = viewModelScope.launch { fetchPlayback(episode, resume) }
    }

    fun selectCodec(codec: VideoCodec) {
        val stream = playback?.selectStream(desiredQuality, codec) ?: return
        if (_uiState.value.loading || _uiState.value.switching) return
        playbackIntent.setRequested(player.playWhenReady)
        applyStream(stream, player.currentPosition.coerceAtLeast(0L))
    }

    fun retryPlayback() {
        _uiState.value.episode?.let { selectEpisode(it.id, force = true) } ?: reload()
    }

    fun toggleFollow() {
        val detail = _uiState.value.detail ?: return
        if (_uiState.value.loading || _uiState.value.followBusy) return
        if (!sessionManager.loginCookies.value.isLoggedIn) {
            _uiState.update { it.copy(feedback = "请先登录后再追番／追剧") }
            return
        }
        _uiState.update { it.copy(followBusy = true) }
        followJob = viewModelScope.launch {
            try {
                repository.setFollowed(detail.id, !detail.followed)
                _uiState.update { it.copy(detail = it.detail?.copy(followed = !detail.followed),
                    followBusy = false, feedback = if (detail.followed) "已取消追番／追剧" else "追番／追剧成功") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(followBusy = false, feedback = error.friendlyMessage()) }
            }
        }
    }

    fun setFullscreen(value: Boolean) = _uiState.update { it.copy(fullscreen = value) }
    fun togglePlay() {
        if (_uiState.value.loading || _uiState.value.switching || _uiState.value.stream == null) {
            playbackIntent.toggle()
            player.playWhenReady = playbackIntent.shouldPlay
            _uiState.update { it.copy(playbackRequested = playbackIntent.shouldPlay) }
        } else {
            player.togglePlayback()
            playbackIntent.setRequested(player.playWhenReady)
        }
    }
    fun seekBy(delta: Long) {
        player.seekTo((player.currentPosition + delta).coerceIn(0L, player.duration.coerceAtLeast(0L)))
    }
    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(speed = speed) }
    }
    fun startBoost() {
        boostOriginalSpeed = _uiState.value.speed
        setSpeed(2f)
    }
    fun stopBoost() = setSpeed(boostOriginalSpeed)
    fun toggleDanmaku() = _uiState.update { it.copy(danmakuSettings = it.danmakuSettings.copy(enabled = !it.danmakuSettings.enabled)) }
    fun consumeFeedback() = _uiState.update { it.copy(feedback = null) }
    fun onEnter() {
        playbackIntent.enter()
    }
    fun onLeave() {
        playbackIntent.leave()
        saveProgress()
        player.pause()
    }
    private fun saveProgress() {
        val state = _uiState.value
        val episode = state.episode ?: return
        if (state.stream == null || state.preview || player.currentPosition <= 0L) return
        val progress = SeasonProgress(episode.id, player.currentPosition.coerceAtLeast(0L),
            player.duration.coerceAtLeast(0L), System.currentTimeMillis())
        savedProgress = progress
        persistProgress(seasonId, progress)
    }
    override fun onCleared() {
        saveProgress()
        player.release()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer, context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { SeasonViewModel(container.seasonRepository, container.danmakuRepository,
                container.sessionManager, container.seasonProgressStore, container::saveSeasonProgress, context) }
        }
    }
}
