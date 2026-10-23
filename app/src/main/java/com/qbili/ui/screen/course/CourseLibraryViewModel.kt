package com.qbili.ui.screen.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qbili.data.local.CourseProgressStorage
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun filterCourseRecords(entries: List<CourseProgress>, query: String): List<CourseProgress> {
    val words = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return entries.filter { entry ->
        val text = "${entry.title} ${entry.author} ${entry.episodeTitle} ss${entry.seasonId} ep${entry.episodeId}"
        words.all { text.contains(it, ignoreCase = true) }
    }
}

class CourseLibraryViewModel(private val sessions: StateFlow<LoginCookies>, private val store: CourseProgressStorage,
    private val scope: CoroutineScope? = null) : ViewModel() {
    data class UiState(val accountMid: Long = 0, val entries: List<CourseProgress> = emptyList(),
        val query: String = "", val loading: Boolean = true, val busy: Boolean = false,
        val error: String? = null, val message: String? = null) {
        val filtered: List<CourseProgress> get() = filterCourseRecords(entries, query)
    }
    private val workScope get() = scope ?: viewModelScope
    private fun account() = sessions.value.takeIf { it.isLoggedIn }?.mid ?: 0
    private val mutable = MutableStateFlow(UiState(accountMid = account()))
    val state = mutable.asStateFlow()
    private var readJob: Job? = null
    private var actionJob: Job? = null
    private var session = sessions.value
    private var generation = 0L

    init {
        workScope.launch {
            sessions.collect { current ->
                if (current != session) {
                    session = current
                    generation++
                    actionJob?.cancel()
                    mutable.value = UiState(accountMid = account())
                    observe()
                }
            }
        }
        observe()
    }

    fun retry() { if (!mutable.value.busy) observe() }

    private fun observe() {
        readJob?.cancel()
        val token = generation
        mutable.update { it.copy(loading = true, error = null) }
        readJob = workScope.launch {
            try {
                combine(sessions, store.entries) { current, entries ->
                    val mid = current.takeIf { it.isLoggedIn }?.mid ?: 0
                    mid to entries.filter { it.accountMid == mid }
                }.collect { (mid, entries) ->
                    if (token == generation && mid == account()) mutable.update {
                        it.copy(accountMid = mid, entries = entries, loading = false, error = null)
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (token == generation) mutable.update { it.copy(loading = false, error = "课程记录读取失败，请重试") }
            }
        }
    }

    fun search(query: String) { mutable.update { it.copy(query = query) } }
    fun consumeMessage() { mutable.update { it.copy(message = null) } }
    fun delete(expectedMid: Long, seasonId: Long) {
        if (mutable.value.entries.none { it.accountMid == expectedMid && it.seasonId == seasonId }) return
        edit(expectedMid) { store.delete(expectedMid, seasonId) }
    }
    fun clear(expectedMid: Long) { edit(expectedMid) { store.clear(expectedMid) } }

    private fun edit(expectedMid: Long, action: suspend () -> Unit) {
        if (expectedMid != account() || mutable.value.accountMid != expectedMid || mutable.value.busy || mutable.value.loading) return
        val token = generation
        val captured = sessions.value
        mutable.update { it.copy(busy = true) }
        actionJob = workScope.launch {
            try {
                action()
                if (token == generation && captured == sessions.value) mutable.update { it.copy(message = "已删除本机课程记录") }
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) {
                if (token == generation && captured == sessions.value) mutable.update { it.copy(message = "删除失败，请重试") }
            } finally {
                if (token == generation && captured == sessions.value) mutable.update { it.copy(busy = false) }
            }
        }
    }
}
