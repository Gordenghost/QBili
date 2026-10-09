package com.qbili.ui.screen.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.RankingRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RankingViewModel(private val repository: RankingRepository) : ViewModel() {
    data class UiState(
        val videos: List<VideoItem> = emptyList(),
        val page: Int = 0,
        val hasMore: Boolean = true,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun refresh() {
        loadJob?.cancel()
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val result = repository.popular(1)
                _uiState.update { it.copy(
                    videos = result.videos.distinctBy { video -> video.aid },
                    page = 1, hasMore = result.hasMore, loading = false,
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.loading || !state.hasMore || state.page == 0) return
        val page = state.page + 1
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val result = repository.popular(page)
                _uiState.update { it.copy(
                    videos = (it.videos + result.videos).distinctBy { video -> video.aid },
                    page = page, hasMore = result.hasMore, loading = false,
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RankingViewModel(container.rankingRepository) }
        }
    }
}
