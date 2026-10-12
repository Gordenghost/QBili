package com.qbili.ui.screen.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.local.VideoProgressStore
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.repository.HistoryRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.HistoryCursor
import com.qbili.domain.model.VideoProgress
import com.qbili.domain.model.WatchHistoryItem
import com.qbili.domain.model.toHistoryItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: HistoryRepository,
    private val progressStore: VideoProgressStore,
    private val sessions: StateFlow<LoginCookies>,
) : ViewModel() {
    data class UiState(
        val local: Boolean = true,
        val loggedIn: Boolean = false,
        val items: List<WatchHistoryItem> = emptyList(),
        val loading: Boolean = false,
        val hasMore: Boolean = false,
        val busy: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val seasonTarget: Pair<Long, WatchHistoryItem>? = null,
        val accountMid: Long = 0,
    )
    private val mutableState = MutableStateFlow(UiState())
    val state = mutableState.asStateFlow()
    private var session = LoginCookies.EMPTY
    private var localEntries = emptyList<VideoProgress>()
    private var cursor = HistoryCursor()
    private var loadJob: Job? = null
    private var actionJob: Job? = null

    init {
        viewModelScope.launch {
            sessions.collect { current ->
                loadJob?.cancel()
                actionJob?.cancel()
                session = current
                cursor = HistoryCursor()
                mutableState.value = UiState(local = !current.isLoggedIn, loggedIn = current.isLoggedIn, accountMid = current.mid ?: 0)
                if (current.isLoggedIn) refresh() else updateLocal()
            }
        }
        viewModelScope.launch {
            progressStore.entries.collect {
                localEntries = it
                updateLocal()
            }
        }
    }

    private fun updateLocal() {
        if (state.value.local) mutableState.update { it.copy(
            items = localEntries.filter { entry -> entry.accountMid == (session.mid ?: 0L) }.map { entry -> entry.toHistoryItem() },
            loading = false, hasMore = false, error = null,
        ) }
    }

    fun selectSource(local: Boolean) {
        if (state.value.busy || local == state.value.local || (!local && !session.isLoggedIn)) return
        loadJob?.cancel()
        actionJob?.cancel()
        cursor = HistoryCursor()
        mutableState.update { UiState(local = local, loggedIn = session.isLoggedIn, accountMid = session.mid ?: 0) }
        refresh()
    }

    fun refresh() {
        if (state.value.busy) return
        if (state.value.local) { updateLocal(); return }
        loadJob?.cancel()
        cursor = HistoryCursor()
        fetch(append = false)
    }

    fun loadMore() {
        if (state.value.loading || state.value.busy || !state.value.hasMore || state.value.local) return
        fetch(append = true)
    }

    private fun fetch(append: Boolean) {
        val expected = session
        mutableState.update { it.copy(loading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val page = repository.page(expected, cursor)
                if (expected != session || state.value.local) return@launch
                cursor = page.cursor
                mutableState.update { it.copy(items = (if (append) it.items + page.items else page.items)
                    .distinctBy { item -> item.key }, hasMore = page.hasMore, loading = false) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (expected == session) mutableState.update { it.copy(loading = false, error = error.friendlyMessage()) }
            }
        }
    }

    fun delete(item: WatchHistoryItem) = manage(item)
    fun clear() = manage(null)

    private fun manage(item: WatchHistoryItem?) {
        if (state.value.busy || state.value.loading) return
        if (item != null && state.value.items.none { it.key == item.key }) return
        val expected = session
        val local = state.value.local
        mutableState.update { it.copy(busy = true) }
        actionJob = viewModelScope.launch {
            try {
                if (!local) {
                    if (item == null) repository.clear(expected) else repository.delete(expected, item)
                }
                if (local) {
                    if (item == null) progressStore.clear(expected.mid ?: 0) else {
                        progressStore.delete(expected.mid ?: 0, item.aid)
                    }
                }
                if (expected != session) return@launch
                mutableState.update { it.copy(items = if (item == null) emptyList() else it.items.filterNot { entry -> entry.key == item.key },
                    hasMore = item != null && it.hasMore, busy = false,
                    message = if (item == null) "已清空观看历史" else "已删除观看记录") }
                if (item == null) cursor = HistoryCursor()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (expected == session) mutableState.update { it.copy(busy = false, message = error.friendlyMessage()) }
            }
        }
    }

    fun openSeason(item: WatchHistoryItem) {
        if (state.value.busy) return
        val expected = session
        mutableState.update { it.copy(busy = true) }
        actionJob = viewModelScope.launch {
            try {
                val seasonId = repository.seasonIdForEpisode(item.episodeId)
                if (expected == session) mutableState.update { it.copy(busy = false, seasonTarget = seasonId to item) }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (expected == session) mutableState.update { it.copy(busy = false, message = error.friendlyMessage()) }
            }
        }
    }

    fun unsupported() { mutableState.update { it.copy(message = "此类历史记录暂不支持打开，可删除或到官方客户端查看") } }
    fun consumeMessage() { mutableState.update { it.copy(message = null) } }
    fun consumeTarget() { mutableState.update { it.copy(seasonTarget = null) } }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(container.historyRepository, container.videoProgressStore,
                container.sessionManager.loginCookies) }
        }
    }
}
