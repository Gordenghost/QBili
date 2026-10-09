package com.qbili.ui.screen.favorite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.FavoriteRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.FavFolder
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FavoriteViewModel(private val repository: FavoriteRepository) : ViewModel() {
    data class UiState(
        val mid: Long = 0,
        val folders: List<FavFolder> = emptyList(),
        val selected: FavFolder? = null,
        val videos: List<VideoItem> = emptyList(),
        val page: Int = 0,
        val hasMore: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun start(mid: Long) {
        if (mid <= 0 || mid == _uiState.value.mid) return
        loadJob?.cancel()
        _uiState.value = UiState(mid = mid)
        refresh()
    }

    fun clear() {
        loadJob?.cancel()
        _uiState.value = UiState()
    }

    fun refresh() {
        val state = _uiState.value
        if (state.mid <= 0 || state.loading) return
        if (state.selected == null) loadFolders(state.mid) else {
            _uiState.update { it.copy(videos = emptyList(), page = 0, hasMore = true, error = null) }
            loadMore()
        }
    }

    private fun loadFolders(mid: Long) {
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val folders = repository.folders(mid)
                _uiState.update { if (it.mid == mid && it.selected == null)
                    it.copy(folders = folders, loading = false) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { if (it.mid == mid && it.selected == null)
                    it.copy(error = error.friendlyMessage(), loading = false) else it }
            }
        }
    }

    fun openFolder(folder: FavFolder) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(selected = folder, videos = emptyList(), page = 0, hasMore = true,
                loading = false, error = null)
        }
        loadMore()
    }

    fun closeFolder() {
        loadJob?.cancel()
        _uiState.update { it.copy(selected = null, videos = emptyList(), loading = false, error = null) }
        refresh()
    }

    fun loadMore() {
        val state = _uiState.value
        val folder = state.selected ?: return
        if (state.loading || !state.hasMore) return
        val next = state.page + 1
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.videos(folder.id, next)
                _uiState.update { current ->
                    if (current.selected?.id != folder.id) current else current.copy(
                        videos = (current.videos + page.videos).distinctBy { it.aid },
                        page = next,
                        hasMore = page.hasMore,
                        loading = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { if (it.selected?.id == folder.id)
                    it.copy(error = error.friendlyMessage(), loading = false) else it }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { FavoriteViewModel(container.favoriteRepository) }
        }
    }
}
