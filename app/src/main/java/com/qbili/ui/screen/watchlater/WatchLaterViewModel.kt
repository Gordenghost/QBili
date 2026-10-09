package com.qbili.ui.screen.watchlater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.InteractionRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.WatchLaterItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WatchLaterViewModel(
    private val repository: InteractionRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    data class UiState(
        val videos: List<WatchLaterItem> = emptyList(),
        val loading: Boolean = false,
        val error: String? = null,
        val removing: Set<Long> = emptySet(),
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun refresh() {
        if (!sessionManager.loginCookies.value.isLoggedIn ||
            _uiState.value.loading || _uiState.value.removing.isNotEmpty()
        ) return
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val videos = repository.watchLaterList()
                _uiState.update { it.copy(videos = videos, loading = false, error = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    fun remove(item: WatchLaterItem) {
        val aid = item.video.aid
        if (aid in _uiState.value.removing || _uiState.value.loading ||
            !sessionManager.loginCookies.value.isLoggedIn
        ) return
        _uiState.update { it.copy(removing = it.removing + aid) }
        viewModelScope.launch {
            try {
                repository.removeFromWatchLater(aid)
                _uiState.update { state ->
                    state.copy(videos = state.videos.filterNot { it.video.aid == aid })
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(message = "移除失败：${error.friendlyMessage()}") }
            } finally {
                _uiState.update { it.copy(removing = it.removing - aid) }
            }
        }
    }

    fun clear() {
        loadJob?.cancel()
        _uiState.value = UiState()
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                WatchLaterViewModel(container.interactionRepository, container.sessionManager)
            }
        }
    }
}
