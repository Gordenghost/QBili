package com.qbili.ui.screen.dynamic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.DynamicRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.DynamicPost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DynamicViewModel(private val repository: DynamicRepository) : ViewModel() {
    data class UiState(
        val posts: List<DynamicPost> = emptyList(),
        val offset: String? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val failedOffset: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null

    fun clear() {
        loadJob?.cancel()
        _uiState.value = UiState()
    }

    fun refresh() {
        loadJob?.cancel()
        load(null)
    }

    fun loadMore() {
        val state = _uiState.value
        if (!state.loading && state.offset != null) load(state.offset)
    }

    fun retry() {
        val state = _uiState.value
        if (state.loading || state.error == null) return
        if (state.failedOffset == null) refresh() else load(state.failedOffset)
    }

    private fun load(offset: String?) {
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.following(offset)
                _uiState.update { it.copy(
                    posts = (if (offset == null) page.posts else it.posts + page.posts)
                        .distinctBy { post -> post.id },
                    offset = page.next, loading = false, failedOffset = null,
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(loading = false, error = error.friendlyMessage(),
                    failedOffset = offset) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DynamicViewModel(container.dynamicRepository) }
        }
    }
}
