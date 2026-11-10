package com.qbili.ui.screen.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.MessageRepository
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.di.AppContainer
import com.qbili.domain.model.PrivateConversation
import com.qbili.domain.model.PrivateMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class MessagesViewModel(
    private val repository: MessageRepository,
    private val sessions: StateFlow<LoginCookies>,
    private val scope: CoroutineScope? = null,
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
        val failedMessageCursor: Long? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var sessionsJob: Job? = null
    private var messagesJob: Job? = null
    private var sendJob: Job? = null
    private var conversationGeneration = 0L
    private val workScope: CoroutineScope get() = scope ?: viewModelScope

    init {
        workScope.launch {
            sessions.collect { account ->
                clear()
                if (account.isLoggedIn) refreshSessions()
            }
        }
    }

    fun clear() {
        conversationGeneration++
        sessionsJob?.cancel()
        messagesJob?.cancel()
        sendJob?.cancel()
        _uiState.value = UiState()
    }

    fun refreshSessions() {
        if (!sessions.value.isLoggedIn) return
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
        val account = sessions.value
        _uiState.update { it.copy(sessionsLoading = true, sessionsError = null) }
        sessionsJob = workScope.launch {
            try {
                val page = repository.sessions(cursor)
                currentCoroutineContext().ensureActive()
                if (account != sessions.value) return@launch
                _uiState.update { state -> state.copy(
                    sessions = (if (cursor == null) page.sessions else state.sessions + page.sessions)
                        .distinctBy { it.talkerId },
                    nextSession = page.next,
                    sessionsLoading = false, failedSessionCursor = null,
                    selected = state.selected?.let { selected -> page.sessions.firstOrNull {
                        it.talkerId == selected.talkerId } ?: selected },
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (account != sessions.value) return@launch
                _uiState.update { it.copy(sessionsLoading = false,
                    sessionsError = error.friendlyMessage(), failedSessionCursor = cursor) }
            }
        }
    }

    fun open(conversation: PrivateConversation) {
        conversationGeneration++
        messagesJob?.cancel()
        sendJob?.cancel()
        _uiState.update { it.copy(
            selected = conversation, messages = emptyList(), olderMessage = null,
            messagesLoading = false, messagesError = null, failedMessageCursor = null, draft = "", sending = false,
        ) }
        refreshMessages()
    }

    fun close() {
        conversationGeneration++
        messagesJob?.cancel()
        sendJob?.cancel()
        _uiState.update { it.copy(
            selected = null, messages = emptyList(), olderMessage = null,
            messagesLoading = false, messagesError = null, failedMessageCursor = null, draft = "", sending = false,
        ) }
    }

    fun refreshMessages() {
        messagesJob?.cancel()
        loadMessages(null)
        if (_uiState.value.selected?.profileUnavailable == true) refreshSessions()
    }

    fun loadOlder() {
        val state = _uiState.value
        if (state.messagesLoading || state.olderMessage == null) return
        loadMessages(state.olderMessage)
    }

    fun retryMessages() {
        val state = _uiState.value
        if (!state.messagesLoading && state.messagesError != null) loadMessages(state.failedMessageCursor)
    }

    private fun loadMessages(cursor: Long?) {
        val talkerId = _uiState.value.selected?.talkerId ?: return
        val account = sessions.value
        val generation = conversationGeneration
        _uiState.update { it.copy(messagesLoading = true, messagesError = null) }
        messagesJob = workScope.launch {
            try {
                val page = repository.history(talkerId, cursor)
                currentCoroutineContext().ensureActive()
                if (account != sessions.value || generation != conversationGeneration) return@launch
                _uiState.update { state -> if (state.selected?.talkerId != talkerId) state else state.copy(
                    messages = (if (cursor == null) page.messages else page.messages + state.messages)
                        .distinctBy { it.key }.sortedBy { it.seqNo },
                    olderMessage = page.older,
                    messagesLoading = false,
                    failedMessageCursor = null,
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (account != sessions.value || generation != conversationGeneration) return@launch
                _uiState.update { state -> if (state.selected?.talkerId != talkerId) state else
                    state.copy(messagesLoading = false, messagesError = error.friendlyMessage(), failedMessageCursor = cursor) }
            }
        }
    }

    fun setDraft(value: String) = _uiState.update { it.copy(draft = value) }

    fun send() {
        val state = _uiState.value
        val talkerId = state.selected?.talkerId ?: return
        if (state.sending) return
        val account = sessions.value
        val generation = conversationGeneration
        val mid = account.mid
        if (!account.isLoggedIn || mid == null) {
            _uiState.update { it.copy(feedback = "请先登录或等待账号信息加载") }
            return
        }
        val text = state.draft.trim()
        if (text.isEmpty() || text.length > MessageRepository.MAX_TEXT_LENGTH) {
            _uiState.update { it.copy(feedback = "私信须为 1-${MessageRepository.MAX_TEXT_LENGTH} 字") }
            return
        }
        _uiState.update { it.copy(sending = true) }
        sendJob = workScope.launch {
            try {
                repository.send(mid, talkerId, text)
                currentCoroutineContext().ensureActive()
                if (account != sessions.value || generation != conversationGeneration) return@launch
                _uiState.update { current -> if (current.selected?.talkerId != talkerId) current else
                    current.copy(draft = "", feedback = "私信已发送") }
                if (_uiState.value.selected?.talkerId == talkerId) refreshMessages()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (account != sessions.value || generation != conversationGeneration) return@launch
                _uiState.update { it.copy(feedback = "发送失败：${error.friendlyMessage()}") }
            } finally {
                if (account == sessions.value && generation == conversationGeneration) {
                    _uiState.update { it.copy(sending = false) }
                }
            }
        }
    }

    fun consumeFeedback() = _uiState.update { it.copy(feedback = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { MessagesViewModel(container.messageRepository, container.sessionManager.loginCookies) }
        }
    }
}
