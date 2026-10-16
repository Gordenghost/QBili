package com.qbili.ui.screen.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.RankingRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.RankingCategory
import com.qbili.domain.model.RankingTab
import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.WeeklyIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RankingViewModel(private val repository: RankingRepository, private val scope: CoroutineScope? = null) : ViewModel() {
    data class UiState(
        val tab: RankingTab = RankingTab.POPULAR,
        val category: RankingCategory = RankingCategory.ALL,
        val issues: List<WeeklyIssue> = emptyList(),
        val weeklyNumber: Int = 0,
        val videos: List<VideoItem> = emptyList(),
        val title: String = "",
        val description: String = "",
        val page: Int = 0,
        val hasMore: Boolean = false,
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val error: String? = null,
        val failedPage: Int? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L
    private val workScope get() = scope ?: viewModelScope

    fun selectTab(tab: RankingTab) {
        if (_uiState.value.tab == tab) return
        reset { it.copy(tab = tab) }
    }

    fun selectCategory(category: RankingCategory) {
        if (_uiState.value.tab != RankingTab.RANKING || _uiState.value.category == category) return
        reset { it.copy(category = category) }
    }

    fun selectWeek(number: Int) {
        val state = _uiState.value
        if (state.tab != RankingTab.WEEKLY || state.weeklyNumber == number || state.issues.none { it.number == number }) return
        reset { it.copy(weeklyNumber = number) }
    }

    private fun reset(select: (UiState) -> UiState) {
        generation++
        loadJob?.cancel()
        _uiState.update { select(it).copy(videos = emptyList(), title = "", description = "",
            page = 0, hasMore = false, loading = false, refreshing = false, error = null, failedPage = null) }
        load(1, refresh = true, reloadIssues = _uiState.value.issues.isEmpty())
    }

    fun refresh() {
        generation++
        loadJob?.cancel()
        load(1, refresh = true, reloadIssues = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.tab != RankingTab.POPULAR || state.loading || !state.hasMore || state.page == 0 || state.error != null) return
        load(state.page + 1, refresh = false)
    }

    fun retry() {
        val state = _uiState.value
        if (state.loading || state.error == null) return
        val page = state.failedPage ?: 1
        if (page > 1 && state.tab == RankingTab.POPULAR) load(page, refresh = false) else refresh()
    }

    private fun load(page: Int, refresh: Boolean, reloadIssues: Boolean = false) {
        val request = _uiState.value
        val token = generation
        _uiState.update { it.copy(loading = true, refreshing = refresh, error = null, failedPage = null) }
        loadJob = workScope.launch {
            try {
                val result = when (request.tab) {
                    RankingTab.POPULAR -> repository.popular(page)
                    RankingTab.RANKING -> repository.ranking(request.category)
                    RankingTab.PRECIOUS -> repository.precious()
                    RankingTab.WEEKLY -> {
                        val issues = if (reloadIssues) repository.weeklyIssues() else request.issues
                        currentCoroutineContext().ensureActive()
                        if (token != generation) return@launch
                        val number = request.weeklyNumber.takeIf { chosen -> issues.any { it.number == chosen } }
                            ?: issues.firstOrNull()?.number ?: 0
                        _uiState.update { it.copy(issues = issues, weeklyNumber = number) }
                        if (number == 0) RankingRepository.RankingPage(emptyList(), false, "暂无已发布的每周必看")
                        else repository.weekly(number)
                    }
                }
                currentCoroutineContext().ensureActive()
                if (token != generation) return@launch
                _uiState.update { state ->
                    val videos = (if (refresh) result.videos else state.videos + result.videos).distinctBy { it.aid }
                    state.copy(videos = videos, page = page,
                        hasMore = request.tab == RankingTab.POPULAR && result.hasMore &&
                            (refresh || videos.size > state.videos.size),
                        title = result.title, description = result.description,
                        loading = false, refreshing = false)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != generation) return@launch
                _uiState.update { it.copy(loading = false, refreshing = false,
                    error = error.friendlyMessage(), failedPage = page) }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { RankingViewModel(container.rankingRepository) }
        }
    }
}
