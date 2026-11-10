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
import com.qbili.domain.model.DynamicAuthor
import com.qbili.domain.model.mergeDynamicAuthors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class DynamicViewModel(private val repository: DynamicRepository) : ViewModel() {
    data class UiState(
        val posts: List<DynamicPost> = emptyList(),
        val offset: String? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val failedOffset: String? = null,
        val selectedAuthor: DynamicAuthor? = null,
        val authors: List<DynamicAuthor> = emptyList(),
        val authorsPage: Int = 0,
        val authorsHasMore: Boolean = true,
        val authorsLoading: Boolean = false,
        val authorsError: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var authorsJob: Job? = null
    private var generation = 0L
    private var accountMid = 0L

    fun setAccount(mid: Long) {
        if (accountMid == mid) return
        clear()
        accountMid = mid
        if (mid > 0) refresh()
    }

    fun clear() {
        loadJob?.cancel()
        authorsJob?.cancel()
        generation++
        _uiState.value = UiState()
    }

    fun refresh() {
        loadJob?.cancel()
        generation++
        _uiState.update { it.copy(posts = emptyList(), offset = null, loading = false, failedOffset = null) }
        load(null)
    }

    fun selectAuthor(author: DynamicAuthor?) {
        if (_uiState.value.selectedAuthor?.mid == author?.mid) return
        _uiState.update { it.copy(selectedAuthor = author) }
        refresh()
    }

    fun loadAuthors() {
        val state = _uiState.value
        if (accountMid <= 0 || state.authorsLoading || !state.authorsHasMore) return
        val expectedMid = accountMid
        val page = state.authorsPage + 1
        _uiState.update { it.copy(authorsLoading = true, authorsError = null) }
        authorsJob = viewModelScope.launch {
            try {
                val result = repository.followedAuthors(expectedMid, page)
                currentCoroutineContext().ensureActive()
                if (accountMid != expectedMid) return@launch
                _uiState.update { it.copy(authors = it.authors.mergeDynamicAuthors(result.authors),
                    authorsPage = page, authorsHasMore = result.hasMore, authorsLoading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (accountMid == expectedMid) _uiState.update {
                    it.copy(authorsLoading = false, authorsError = error.friendlyMessage())
                }
            }
        }
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
        val expectedGeneration = generation
        val author = _uiState.value.selectedAuthor
        _uiState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = if (author == null) repository.following(offset) else repository.space(author.mid, offset)
                currentCoroutineContext().ensureActive()
                if (generation != expectedGeneration) return@launch
                _uiState.update { it.copy(
                    posts = (if (offset == null) page.posts else it.posts + page.posts)
                        .distinctBy { post -> post.id },
                    offset = page.next, loading = false, failedOffset = null,
                    authors = it.authors.mergeDynamicAuthors(page.posts.map { post ->
                        DynamicAuthor(post.authorMid, post.authorName, post.authorFace)
                    }),
                ) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (generation != expectedGeneration) return@launch
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
