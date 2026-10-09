package com.qbili.ui.screen.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.CommentRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.VideoComment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CommentViewModel(
    private val repository: CommentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class UiState(
        val oid: Long = 0,
        val type: Int = CommentRepository.TYPE_VIDEO,
        val mode: Int = CommentRepository.MODE_POPULAR,
        val comments: List<VideoComment> = emptyList(),
        val total: Int = 0,
        val next: Long? = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val draft: String = "",
        val replyTo: VideoComment? = null,
        val replyRoot: VideoComment? = null,
        val threadReplies: List<VideoComment> = emptyList(),
        val threadPage: Int = 0,
        val threadHasMore: Boolean = false,
        val threadLoading: Boolean = false,
        val threadError: String? = null,
        val sending: Boolean = false,
        val feedback: String? = null,
    ) {
        fun selectReply(comment: VideoComment?): UiState =
            if (sending || replyTo?.id == comment?.id) this
            else copy(replyTo = comment, draft = "")
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var pageJob: Job? = null
    private var threadJob: Job? = null

    fun show(oid: Long, type: Int = CommentRepository.TYPE_VIDEO) {
        if (oid > 0 && oid == _uiState.value.oid && type == _uiState.value.type) return
        if (oid <= 0 || type <= 0) {
            _uiState.value = UiState(error = "无效的评论对象编号")
            return
        }
        threadJob?.cancel()
        _uiState.value = UiState()
        load(oid, type, CommentRepository.MODE_POPULAR)
    }

    fun changeMode(mode: Int) {
        if (mode == _uiState.value.mode || _uiState.value.oid <= 0) return
        load(_uiState.value.oid, _uiState.value.type, mode)
    }

    fun refresh() {
        if (_uiState.value.oid > 0) load(_uiState.value.oid, _uiState.value.type, _uiState.value.mode)
    }

    private fun load(oid: Long, type: Int, mode: Int) {
        pageJob?.cancel()
        _uiState.update {
            it.copy(oid = oid, type = type, mode = mode, comments = emptyList(), next = 0, total = 0,
                loading = true, loadingMore = false, error = null)
        }
        pageJob = viewModelScope.launch { fetchPage(oid, type, mode, next = 0, first = true) }
    }

    fun loadMore() {
        val state = _uiState.value
        val next = state.next ?: return
        if (state.loading || state.loadingMore || state.oid <= 0) return
        _uiState.update { it.copy(loadingMore = true, error = null) }
        pageJob = viewModelScope.launch { fetchPage(state.oid, state.type, state.mode, next, first = false) }
    }

    private suspend fun fetchPage(oid: Long, type: Int, mode: Int, next: Long, first: Boolean) {
        try {
            val page = repository.list(oid, mode, next, type)
            _uiState.update { state ->
                if (state.oid != oid || state.type != type || state.mode != mode) state else state.copy(
                    comments = (if (first) page.comments else state.comments + page.comments)
                        .distinctBy { it.id },
                    next = page.next,
                    total = page.total,
                    loading = false,
                    loadingMore = false,
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _uiState.update { it.copy(error = error.friendlyMessage(), loading = false, loadingMore = false) }
        }
    }

    fun setDraft(text: String) = _uiState.update { it.copy(draft = text) }

    fun replyTo(comment: VideoComment?) = _uiState.update { it.selectReply(comment) }

    fun openReplies(root: VideoComment) {
        threadJob?.cancel()
        _uiState.update { it.copy(replyRoot = root, threadReplies = emptyList(),
            threadPage = 0, threadHasMore = true, threadError = null) }
        loadMoreReplies()
    }

    fun closeReplies() {
        threadJob?.cancel()
        _uiState.update { it.copy(replyRoot = null, threadLoading = false, threadError = null) }
    }

    fun loadMoreReplies() {
        val state = _uiState.value
        val root = state.replyRoot ?: return
        if (!state.threadHasMore || state.threadLoading) return
        val page = state.threadPage + 1
        _uiState.update { it.copy(threadLoading = true, threadError = null) }
        threadJob = viewModelScope.launch {
            try {
                val result = repository.replies(state.oid, root.id, page, state.type)
                _uiState.update { current ->
                    if (current.replyRoot?.id != root.id) current else current.copy(
                        threadReplies = (current.threadReplies + result.replies).distinctBy { it.id },
                        threadPage = page,
                        threadHasMore = result.hasMore,
                        threadLoading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(threadLoading = false, threadError = error.friendlyMessage()) }
            }
        }
    }

    fun send() {
        val state = _uiState.value
        if (state.sending || state.oid <= 0) return
        if (!sessionManager.loginCookies.value.isLoggedIn) {
            _uiState.update { it.copy(feedback = "请先登录再发表评论") }
            return
        }
        if (state.draft.trim().isEmpty() || state.draft.trim().length > 1000) {
            _uiState.update { it.copy(feedback = "评论须为 1-1000 字") }
            return
        }
        _uiState.update { it.copy(sending = true) }
        viewModelScope.launch {
            try {
                repository.post(state.oid, state.draft, state.replyTo, state.type)
                _uiState.update {
                    it.copy(draft = "", replyTo = null, feedback = "评论已提交，可能需要审核")
                }
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(feedback = error.friendlyMessage()) }
            } finally {
                _uiState.update { it.copy(sending = false) }
            }
        }
    }

    fun consumeFeedback() = _uiState.update { it.copy(feedback = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CommentViewModel(container.commentRepository, container.sessionManager) }
        }
    }
}
