package com.qbili.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbili.core.friendlyMessage
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.player.SeasonStream
import com.qbili.domain.player.selectStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CourseViewModel(
    private val sessions: StateFlow<LoginCookies>,
    private val loadDetail: suspend (LoginCookies, CourseTarget) -> CourseDetail,
    private val loadPlayback: suspend (LoginCookies, CourseEpisode, Int) -> CoursePlayback,
    private val stopPlayback: () -> Unit,
    private val scope: CoroutineScope? = null,
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
    )
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

    init {
        workScope.launch {
            sessions.collect { current ->
                if (!closed && session != current) {
                    session = current
                    reset(_state.value.target, false)
                }
            }
        }
    }

    fun setTarget(target: CourseTarget) {
        if (closed || target == _state.value.target) return
        reset(target, true)
    }

    private fun reset(target: CourseTarget?, openEpisode: Boolean) {
        generation++
        detailJob?.cancel()
        clearPlayback()
        _state.value = UiState(target = target)
        if (target != null && !closed) load(openEpisode)
    }

    fun refresh() {
        if (closed || _state.value.loading || _state.value.target == null) return
        generation++
        detailJob?.cancel()
        clearPlayback()
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
                if (openEpisode && target.episodeId > 0) {
                    detail.episodes.firstOrNull { it.id == target.episodeId }?.let { selectEpisode(it.id) }
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (valid(token, captured)) _state.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    fun selectEpisode(id: Long, quality: Int = 80) {
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
        _state.update { it.copy(episode = episode, switching = true, playError = null, requested = foreground) }
        playbackJob = workScope.launch {
            try {
                val playback = loadPlayback(captured, episode, quality)
                if (!valid(token, captured) || playToken != playbackGeneration) return@launch
                val stream = playback.playback.selectStream(quality)
                    ?: throw IllegalStateException("没有可播放轨道，请在B站查看该课程")
                _state.update { it.copy(stream = stream, switching = false, previewHint = playback.previewHint) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (valid(token, captured) && playToken == playbackGeneration) {
                    _state.update { it.copy(switching = false, requested = false, playError = error.friendlyMessage()) }
                }
            }
        }
    }

    private fun clearPlayback() {
        playbackGeneration++
        playbackJob?.cancel()
        stopPlayback()
        _state.update { it.copy(episode = null, stream = null, switching = false, requested = false,
            previewHint = "", playError = null, buffering = false) }
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
    fun onLeave() { foreground = false; requestPlayback(false) }

    fun close() {
        if (closed) return
        closed = true
        generation++
        detailJob?.cancel()
        clearPlayback()
        _state.update { it.copy(fullscreen = false, loading = false) }
    }
}
