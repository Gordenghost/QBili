package com.qbili.ui.screen.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbili.core.friendlyMessage
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.DanmakuMode
import com.qbili.domain.model.DanmakuPost
import com.qbili.domain.model.DanmakuTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DanmakuComposerViewModel(
    private val sessions: StateFlow<LoginCookies>,
    private val sendPost: suspend (LoginCookies, DanmakuPost) -> Long,
    private val scope: CoroutineScope? = null,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) : ViewModel() {
    data class UiState(
        val target: DanmakuTarget? = null,
        val visible: Boolean = false,
        val draft: String = "",
        val progressMillis: Long = 0,
        val mode: DanmakuMode = DanmakuMode.SCROLL,
        val color: Int = 0xFFFFFF,
        val busy: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private val workScope get() = scope ?: viewModelScope
    private var session = sessions.value
    private var generation = 0L
    private var job: Job? = null
    private var lastSentAt: Long? = null

    init {
        workScope.launch {
            sessions.collect { current ->
                if (current != session) {
                    invalidate()
                    session = current
                    lastSentAt = null
                    _uiState.value = UiState(target = _uiState.value.target)
                }
            }
        }
    }

    fun setTarget(target: DanmakuTarget?) {
        if (_uiState.value.target == target) return
        invalidate()
        _uiState.value = UiState(target = target)
    }

    fun open(progressMillis: Long) {
        if (sessions.value != session) return
        val target = _uiState.value.target ?: return
        if (!session.isLoggedIn || session.biliJct.isNullOrBlank()) {
            _uiState.update { it.copy(notice = "请先登录后发送弹幕（Cookie 需包含 bili_jct）") }
            return
        }
        if (_uiState.value.busy) return
        _uiState.update { it.copy(visible = true, error = null,
            progressMillis = progressMillis.coerceIn(0, target.durationMillis.coerceAtLeast(0))) }
    }

    fun close() {
        if (_uiState.value.busy) return
        invalidate()
        _uiState.update { it.copy(visible = false) }
    }

    fun edit(draft: String) {
        if (!_uiState.value.busy) _uiState.update { it.copy(draft = draft, error = null) }
    }

    fun setMode(mode: DanmakuMode) {
        if (!_uiState.value.busy) _uiState.update { it.copy(mode = mode, error = null) }
    }

    fun setColor(color: Int) {
        if (!_uiState.value.busy) _uiState.update { it.copy(color = color, error = null) }
    }

    fun consumeNotice() { _uiState.update { it.copy(notice = null) } }

    fun send() {
        val state = _uiState.value
        if (!state.visible || state.busy || state.target == null || sessions.value != session) return
        val last = lastSentAt
        if (last != null && clock() - last < 5_000) {
            _uiState.update { it.copy(error = "发送过于频繁，请稍等 5 秒再试") }
            return
        }
        val request = try {
            DanmakuPost(state.target, state.draft, state.progressMillis, state.mode, state.color).validated()
        } catch (error: IllegalArgumentException) {
            _uiState.update { it.copy(error = error.message) }
            return
        }
        val expectedSession = session
        val expectedGeneration = generation
        _uiState.update { it.copy(busy = true, error = null) }
        job = workScope.launch {
            try {
                sendPost(expectedSession, request)
                if (generation != expectedGeneration || sessions.value != expectedSession) return@launch
                lastSentAt = clock()
                _uiState.update { it.copy(visible = false, draft = "", notice = "弹幕已发送，显示以服务端审核为准") }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation == expectedGeneration && sessions.value == expectedSession) {
                    _uiState.update { it.copy(error = error.friendlyMessage()) }
                }
            } finally {
                if (generation == expectedGeneration) _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private fun invalidate() {
        generation++
        job?.cancel()
        job = null
    }
}
