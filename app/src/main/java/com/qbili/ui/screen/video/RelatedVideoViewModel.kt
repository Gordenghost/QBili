package com.qbili.ui.screen.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbili.core.friendlyMessage
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RelatedVideoTarget(val aid: Long, val bvid: String)

class RelatedVideoViewModel(
    private val sessions: StateFlow<LoginCookies>,
    private val loadVideos: suspend (LoginCookies, Long, String) -> List<VideoItem>,
    private val scope: CoroutineScope? = null,
) : ViewModel() {
    data class UiState(
        val target: RelatedVideoTarget? = null,
        val videos: List<VideoItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val loaded: Boolean = false,
    )
    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val workScope get() = scope ?: viewModelScope
    private var session = sessions.value
    private var generation = 0L
    private var job: Job? = null

    init {
        workScope.launch {
            sessions.collect { current ->
                if (current != session) {
                    session = current
                    reset(_uiState.value.target)
                }
            }
        }
    }

    fun setTarget(target: RelatedVideoTarget?) {
        if (target == _uiState.value.target) return
        reset(target)
    }

    private fun reset(target: RelatedVideoTarget?) {
        generation++
        job?.cancel()
        _uiState.value = UiState(target = target)
        if (target != null) load()
    }

    fun refresh() {
        if (_uiState.value.loading || _uiState.value.target == null) return
        generation++
        job?.cancel()
        load()
    }

    private fun load() {
        val target = _uiState.value.target ?: return
        val expectedSession = session
        val token = generation
        _uiState.update { it.copy(loading = true, error = null) }
        job = workScope.launch {
            try {
                val videos = loadVideos(expectedSession, target.aid, target.bvid)
                if (generation != token || sessions.value != expectedSession) return@launch
                _uiState.update { it.copy(videos = videos, loading = false, loaded = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == token && sessions.value == expectedSession) {
                    _uiState.update { it.copy(loading = false, error = error.friendlyMessage()) }
                }
            }
        }
    }
}
