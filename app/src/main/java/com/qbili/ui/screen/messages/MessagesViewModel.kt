package com.qbili.ui.screen.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.MessageRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.PrivateConversation
import com.qbili.domain.model.PrivateMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MessagesViewModel(
    private val repository: MessageRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class UiState(
        val sessions: List<PrivateConversation> = emptyList(),
        val nextSession: Long? = null,
        val sessionsLoading: Boolean = false,
        val sessionsError: String? = null,
        val failedSessionCursor: Long? = null,
        val selected: PrivateConversation? = null,
        val messages: List<PrivateMessage> = emptyList(),
        val olderMessage: Long? = null,
        val messagesLoading: Boolean = false,
        val messagesError: String? = null,
        val draft: String = "",
        val sending: Boolean = false,
        val feedback: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null
    private var sendJob: Job? = null

    fun clear() {
        sessionsJob?.cancel()
        messagesJob?.cancel()
        sendJob?.cancel()
        _uiState.value = UiState()
    }

    fun refreshSessions() {
        if (!sessionManager.loginCookies.value.isLoggedIn) return
        sessionsJob?.cancel()
        loadSessions(null)
    }

    fun loadMoreSessions() {
        val state = _uiState.value
        if (state.sessionsLoading || state.nextSession == null) return
        loadSessions(state.nextSession)
    }

    fun retrySessions() {
        val state = _uiState.value
        if (state.sessionsLoading || state.sessionsError == null) return
        if (state.failedSessionCursor == null) refreshSessions()
        else loadSessions(state.failedSessionCursor)
    }

    private fun loadSessions(cursor: Long?) {
        _uiState.update { it.copy(sessionsLoading = true, sessionsError = null) }
        sessionsJob = viewModelScope.launch {
            try {
                val page = repository.sessions(cursor)
                _uiState.update { state -> state.copy(
                    sessions = (if (cursor == null) page.sessions else state.sessions + page.sessions)
                        .distinctBy { it.talkerId },
                    nextSession = page.next,
                    sessionsLoading = false, failedSessionCursor = null,
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(sessionsLoading = false,
                    sessionsError = error.friendlyMessage(), failedSessionCursor = cursor) }
            }
        }
    }

    fun open(conversation: PrivateConversation) {
        messagesJob?.cancel()
        _uiState.update { it.copy(
            selected = conversation, messages = emptyList(), olderMessage = null,
            messagesLoading = false, messagesError = null, draft = "",
        ) }
        refreshMessages()
    }

    fun close() {
        messagesJob?.cancel()
        _uiState.update { it.copy(
            selected = null, messages = emptyList(), olderMessage = null,
            messagesLoading = false, messagesError = null, draft = "",
        ) }
    }

    fun refreshMessages() {
        messagesJob?.cancel()
        loadMessages(null)
    }

    fun loadOlder() {
        val state = _uiState.value
        if (state.messagesLoading || state.olderMessage == null) return
        loadMessages(state.olderMessage)
    }

    private fun loadMessages(cursor: Long?) {
        val talkerId = _uiState.value.selected?.talkerId ?: return
        _uiState.update { it.copy(messagesLoading = true, messagesError = null) }
        messagesJob = viewModelScope.launch {
            try {
                val page = repository.history(talkerId, cursor)
                _uiState.update { state -> if (state.selected?.talkerId != talkerId) state else state.copy(
                    messages = (if (cursor == null) page.messages else page.messages + state.messages)
                        .distinctBy { it.key }.sortedBy { it.seqNo },
                    olderMessage = page.older,
                    messagesLoading = false,
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { state -> if (state.selected?.talkerId != talkerId) state else
                    state.copy(messagesLoading = false, messagesError = error.friendlyMessage()) }
            }
        }
    }

    fun setDraft(value: String) = _uiState.update { it.copy(draft = value) }

    fun send() {
        val state = _uiState.value
        val talkerId = state.selected?.talkerId ?: return
        if (state.sending) return
        val mid = sessionManager.loginCookies.value.mid ?: sessionManager.profile.value?.mid
        if (!sessionManager.loginCookies.value.isLoggedIn || mid == null) {
            _uiState.update { it.copy(feedback = "请先登录或等待账号信息加载") }
            return
        }
        val text = state.draft.trim()
        if (text.isEmpty() || text.length > MessageRepository.MAX_TEXT_LENGTH) {
            _uiState.update { it.copy(feedback = "私信须为 1-${MessageRepository.MAX_TEXT_LENGTH} 字") }
            return
        }
        _uiState.update { it.copy(sending = true) }
        sendJob = viewModelScope.launch {
            try {
                repository.send(mid, talkerId, text)
                _uiState.update { current -> if (current.selected?.talkerId != talkerId) current else
                    current.copy(draft = "", feedback = "私信已发送") }
                if (_uiState.value.selected?.talkerId == talkerId) refreshMessages()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(feedback = "发送失败：${error.friendlyMessage()}") }
            } finally {
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    fun consumeFeedback() = _uiState.update { it.copy(feedback = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MessagesViewModel(container.messageRepository, container.sessionManager) }
        }
    }
}
