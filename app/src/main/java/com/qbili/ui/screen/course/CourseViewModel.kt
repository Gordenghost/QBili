package com.qbili.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbili.core.friendlyMessage
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.model.CourseProgress
import com.qbili.domain.model.CoursePlaybackSnapshot
import com.qbili.domain.model.courseMediaId
import com.qbili.domain.player.SeasonStream
import com.qbili.domain.player.selectStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

class CourseViewModel(
    private val sessions: StateFlow<LoginCookies>,
    private val loadDetail: suspend (LoginCookies, CourseTarget) -> CourseDetail,
    private val loadPlayback: suspend (LoginCookies, CourseEpisode, Int) -> CoursePlayback,
    private val stopPlayback: () -> Unit,
    private val scope: CoroutineScope? = null,
    private val progressEntries: Flow<List<CourseProgress>> = flowOf(emptyList()),
    private val saveProgress: (CourseProgress) -> Unit = {},
    private val playbackSnapshot: () -> CoursePlaybackSnapshot? = { null },
    private val seekPlayback: (String, Long) -> Boolean = { _, _ -> false },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    data class UiState(
        val target: CourseTarget? = null,
        val detail: CourseDetail? = null,
        val episode: CourseEpisode? = null,
        val stream: SeasonStream? = null,
        val loading: Boolean = false,
        val switching: Boolean = false,
        val buffering: Boolean = false,
        val error: String? = null,
        val playError: String? = null,
        val previewHint: String = "",
        val requested: Boolean = false,
        val fullscreen: Boolean = false,
        val message: String? = null,
        val progress: CourseProgress? = null,
        val preview: Boolean = false,
        val progressAllowed: Boolean = false,
        val mediaGeneration: Long = 0,
        val startPositionMs: Long = 0,
    ) {
        val resumePositionMs: Long get() = detail?.let { progress?.resumePosition(it) } ?: 0
        val mediaId: String get() = courseMediaId(episode?.id ?: 0, mediaGeneration)
        val canResume: Boolean get() = resumePositionMs > 0 && !loading && !switching &&
            !(stream != null && !progressAllowed && episode?.id == progress?.episodeId)
    }
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private val workScope get() = scope ?: viewModelScope
    private var session = sessions.value
    private var generation = 0L
    private var playbackGeneration = 0L
    private var detailJob: Job? = null
    private var playbackJob: Job? = null
    private var foreground = true
    private var closed = false
    private var records = emptyList<CourseProgress>()
    private var playbackSession: LoginCookies? = null
    private var progressLoaded = false
    private var resumeConsumed = false
    private var progressResolved = false

    init {
        workScope.launch {
            sessions.collect { current ->
                if (!closed && session != current) {
                    session = current
                    reset(_state.value.target, false)
                }
            }
        }
        workScope.launch {
            try {
                progressEntries.collect { entries ->
                    records = entries
                    progressLoaded = true
                    if (!resumeConsumed) updateProgress()
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { showMessage("课程记录读取失败，不影响播放；可返回课程入口重试") }
        }
    }

    private fun updateProgress() {
        if (!progressLoaded || resumeConsumed || progressResolved || _state.value.detail == null) return
        progressResolved = true
        val account = session.takeIf { it.isLoggedIn }?.mid ?: 0
        _state.update { current -> current.copy(progress = records.firstOrNull {
            it.accountMid == account && it.seasonId == current.detail?.id
        }) }
    }

    fun setTarget(target: CourseTarget) {
        if (closed || target == _state.value.target) return
        reset(target, true)
    }

    private fun reset(target: CourseTarget?, openEpisode: Boolean) {
        generation++
        detailJob?.cancel()
        clearPlayback()
        resumeConsumed = false
        progressResolved = false
        _state.value = UiState(target = target)
        if (target != null && !closed) load(openEpisode)
    }

    fun refresh() {
        if (closed || _state.value.loading || _state.value.target == null) return
        generation++
        detailJob?.cancel()
        clearPlayback()
        resumeConsumed = false
        progressResolved = false
        load(false)
    }

    private fun load(openEpisode: Boolean) {
        val target = _state.value.target ?: return
        val token = generation
        val captured = session
        _state.update { it.copy(loading = true, error = null) }
        detailJob = workScope.launch {
            try {
                val detail = loadDetail(captured, target)
                if (!valid(token, captured)) return@launch
                _state.update { it.copy(detail = detail, loading = false) }
                updateProgress()
                if (openEpisode && target.episodeId > 0) {
                    detail.episodes.firstOrNull { it.id == target.episodeId }?.let { selectEpisode(it.id) }
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (valid(token, captured)) _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    fun selectEpisode(id: Long, quality: Int = 80, resumePositionMs: Long = 0) {
        val current = _state.value
        if (closed || current.loading || current.switching) return
        val episode = current.detail?.episodes?.firstOrNull { it.id == id } ?: return
        if (!episode.video || !episode.playable || episode.cid <= 0) {
            _state.update { it.copy(message = episode.accessHint) }
            return
        }
        clearPlayback()
        val token = generation
        val playToken = playbackGeneration
        val captured = session
        playbackSession = captured
        _state.update { it.copy(episode = episode, switching = true, playError = null, requested = foreground,
            mediaGeneration = playToken) }
        playbackJob = workScope.launch {
            try {
                val playback = loadPlayback(captured, episode, quality)
                if (!valid(token, captured) || playToken != playbackGeneration) return@launch
                val stream = playback.playback.selectStream(quality)
                    ?: throw IllegalStateException("没有可播放轨道，请在B站查看该课程")
                val resume = current.progress?.takeIf { it.episodeId == episode.id }?.resumePosition(current.detail!!) ?: 0
                val position = resumePositionMs.takeIf { it > 0 && it == resume && playback.progressAllowed } ?: 0
                if (position > 0) resumeConsumed = true
                _state.update { it.copy(stream = stream, switching = false, previewHint = playback.previewHint,
                    preview = playback.playback.preview, startPositionMs = position,
                    progressAllowed = playback.progressAllowed,
                    progress = if (position > 0) null else it.progress,
                    message = if (resumePositionMs > 0 && !playback.progressAllowed)
                        "当前仅提供试看或未确认完整播放状态，从头播放；完整课时记录保留" else it.message) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (valid(token, captured) && playToken == playbackGeneration) {
                    _state.update { it.copy(switching = false, requested = false, playError = error.friendlyMessage()) }
                }
            }
        }
    }

    private fun clearPlayback() {
        persistPlayback()
        playbackGeneration++
        playbackJob?.cancel()
        stopPlayback()
        playbackSession = null
        _state.update { it.copy(episode = null, stream = null, switching = false, requested = false,
            previewHint = "", playError = null, buffering = false, preview = false, progressAllowed = false, startPositionMs = 0) }
    }

    fun resumeLast() {
        val current = _state.value
        if (closed || !foreground || !current.canResume || sessions.value != session) return
        val progress = current.progress ?: return
        if (current.episode?.id == progress.episodeId && current.stream != null && current.playError == null) {
            if (seekPlayback(current.mediaId, current.resumePositionMs)) {
                resumeConsumed = true
                _state.update { it.copy(progress = null) }
                requestPlayback(true)
            } else showMessage("播放器尚未就绪，请稍后重试")
        } else selectEpisode(progress.episodeId, resumePositionMs = current.resumePositionMs)
    }

    fun dismissResume() { resumeConsumed = true; _state.update { it.copy(progress = null) } }

    fun persistPlayback() {
        val current = _state.value
        if (closed || sessions.value != session || playbackSession != session || !current.progressAllowed || current.switching || current.playError != null) return
        val detail = current.detail ?: return
        val episode = current.episode ?: return
        if (current.stream == null) return
        val snapshot = playbackSnapshot() ?: return
        if (!snapshot.played || snapshot.mediaId != current.mediaId || snapshot.durationMs <= 0 ||
            snapshot.positionMs < 1_000 && !snapshot.completed) return
        val account = session.takeIf { it.isLoggedIn }?.mid ?: 0
        saveProgress(CourseProgress(account, detail.id, episode.id, episode.cid, detail.title, detail.cover,
            detail.authorName, episode.title, snapshot.positionMs.coerceIn(0, snapshot.durationMs),
            snapshot.durationMs, clock(), snapshot.completed))
    }

    fun progressSaveFailed(accountMid: Long) {
        if (!closed && (session.takeIf { it.isLoggedIn }?.mid ?: 0) == accountMid) showMessage("课程记录保存失败，不影响播放")
    }

    private fun valid(token: Long, captured: LoginCookies) =
        !closed && token == generation && sessions.value == captured

    fun requestPlayback(requested: Boolean) { _state.update { it.copy(requested = requested && foreground && !closed) } }
    fun playbackError(message: String) { _state.update { it.copy(playError = message, requested = false) } }
    fun setBuffering(value: Boolean) { _state.update { it.copy(buffering = value) } }
    fun showMessage(value: String) { _state.update { it.copy(message = value) } }
    fun setFullscreen(value: Boolean) { _state.update { it.copy(fullscreen = value && !closed) } }
    fun consumeMessage() { _state.update { it.copy(message = null) } }
    fun onEnter() { foreground = true }
    fun onLeave() { persistPlayback(); foreground = false; requestPlayback(false) }

    fun close() {
        if (closed) return
        persistPlayback()
        closed = true
        generation++
        detailJob?.cancel()
        clearPlayback()
        _state.update { it.copy(fullscreen = false, loading = false) }
    }
}
