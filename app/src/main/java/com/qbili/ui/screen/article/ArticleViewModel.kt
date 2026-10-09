package com.qbili.ui.screen.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.ArticleRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.ArticleDetail
import com.qbili.domain.model.ArticleReaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArticleViewModel(
    private val repository: ArticleRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class UiState(
        val id: Long = 0,
        val detail: ArticleDetail? = null,
        val reaction: ArticleReaction? = null,
        val loading: Boolean = false,
        val reactionLoading: Boolean = false,
        val actionBusy: Boolean = false,
        val error: String? = null,
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun show(id: Long) {
        if (id <= 0) {
            _uiState.value = UiState(error = "无效的专栏编号")
            return
        }
        if (id == _uiState.value.id && (_uiState.value.detail != null || _uiState.value.loading)) return
        loadJob?.cancel()
        _uiState.value = UiState(id = id, loading = true)
        loadJob = viewModelScope.launch {
            try {
                val detail = repository.detail(id)
                _uiState.update { state ->
                    if (state.id != id) state else state.copy(detail = detail, loading = false)
                }
                refreshReaction()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { state ->
                    if (state.id != id) state else state.copy(error = error.friendlyMessage(), loading = false)
                }
            }
        }
    }

    fun retry() {
        val id = _uiState.value.id
        if (id > 0) show(id)
    }

    fun refreshReaction() {
        val state = _uiState.value
        val id = state.detail?.id ?: return
        if (state.reactionLoading || state.actionBusy) return
        _uiState.update { it.copy(reactionLoading = true) }
        viewModelScope.launch {
            try {
                val reaction = repository.reaction(id)
                _uiState.update { current ->
                    if (current.id != id) current else current.copy(reaction = reaction, reactionLoading = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { current ->
                    if (current.id != id) current else current.copy(reactionLoading = false,
                        message = "互动状态加载失败：${error.friendlyMessage()}")
                }
            }
        }
    }

    fun toggleLike() = act("点赞") { id, reaction ->
        val liked = !reaction.liked
        repository.setLike(id, liked)
        reaction.withLike(liked)
    }

    fun toggleFavorite() = act("收藏") { id, reaction ->
        val favorited = !reaction.favorited
        repository.setFavorite(id, favorited)
        reaction.withFavorite(favorited)
    }

    private fun act(label: String, action: suspend (Long, ArticleReaction) -> ArticleReaction) {
        val state = _uiState.value
        val id = state.detail?.id ?: return
        if (state.actionBusy || state.reactionLoading) return
        if (!sessionManager.loginCookies.value.isLoggedIn) {
            _uiState.update { it.copy(message = "请先登录再$label") }
            return
        }
        val reaction = state.reaction
        if (reaction == null) {
            refreshReaction()
            return
        }
        _uiState.update { it.copy(actionBusy = true) }
        viewModelScope.launch {
            try {
                val updated = action(id, reaction)
                _uiState.update { current ->
                    if (current.id != id) current else current.copy(reaction = updated)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(message = "$label 失败：${error.friendlyMessage()}") }
            } finally {
                _uiState.update { it.copy(actionBusy = false) }
            }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ArticleViewModel(container.articleRepository, container.sessionManager) }
        }
    }
}
