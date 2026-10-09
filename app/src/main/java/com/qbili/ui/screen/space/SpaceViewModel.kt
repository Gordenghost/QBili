package com.qbili.ui.screen.space

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.core.friendlyMessage
import com.qbili.data.repository.InteractionRepository
import com.qbili.data.repository.DynamicRepository
import com.qbili.data.repository.SpaceRepository
import com.qbili.data.session.SessionManager
import com.qbili.di.AppContainer
import com.qbili.domain.model.SpaceProfile
import com.qbili.domain.model.DynamicPost
import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.SpaceOpusItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpaceViewModel(
    private val repository: SpaceRepository,
    private val dynamicRepository: DynamicRepository,
    private val interactionRepository: InteractionRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    data class UiState(
        val mid: Long = 0,
        val profile: SpaceProfile? = null,
        val profileLoading: Boolean = false,
        val profileError: String? = null,
        val dynamics: List<DynamicPost> = emptyList(),
        val dynamicOffset: String? = null,
        val dynamicLoading: Boolean = false,
        val dynamicError: String? = null,
        val dynamicFailedOffset: String? = null,
        val videos: List<VideoItem> = emptyList(),
        val page: Int = 0,
        val hasMore: Boolean = true,
        val videosLoading: Boolean = false,
        val videosError: String? = null,
        val opus: List<SpaceOpusItem> = emptyList(),
        val opusPage: Int = 0,
        val opusOffset: String? = null,
        val opusHasMore: Boolean = true,
        val opusLoading: Boolean = false,
        val opusError: String? = null,
        val followBusy: Boolean = false,
        val feedback: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()
    private var profileJob: Job? = null
    private var dynamicJob: Job? = null
    private var videosJob: Job? = null
    private var opusJob: Job? = null
    private var followJob: Job? = null

    fun show(mid: Long) {
        if (mid == _uiState.value.mid) return
        profileJob?.cancel()
        dynamicJob?.cancel()
        videosJob?.cancel()
        opusJob?.cancel()
        followJob?.cancel()
        _uiState.value = UiState(mid = mid)
        if (mid > 0) refresh() else _uiState.update { it.copy(profileError = "无效的用户编号") }
    }

    fun refresh() {
        refreshProfile()
        refreshDynamics()
    }

    fun refreshDynamics() {
        val mid = _uiState.value.mid
        if (mid <= 0) return
        dynamicJob?.cancel()
        _uiState.update { it.copy(dynamics = emptyList(), dynamicOffset = null, dynamicError = null,
            dynamicFailedOffset = null, dynamicLoading = false) }
        loadDynamics(null)
    }

    fun loadMoreDynamics() {
        val state = _uiState.value
        if (!state.dynamicLoading && state.dynamicOffset != null) loadDynamics(state.dynamicOffset)
    }

    fun retryDynamics() {
        val state = _uiState.value
        if (state.dynamicLoading || state.dynamicError == null) return
        if (state.dynamicFailedOffset == null) refreshDynamics() else loadDynamics(state.dynamicFailedOffset)
    }

    private fun loadDynamics(offset: String?) {
        val mid = _uiState.value.mid
        _uiState.update { it.copy(dynamicLoading = true, dynamicError = null) }
        dynamicJob = viewModelScope.launch {
            try {
                val page = dynamicRepository.space(mid, offset)
                _uiState.update { if (it.mid == mid) it.copy(
                    dynamics = (if (offset == null) page.posts else it.dynamics + page.posts)
                        .distinctBy { post -> post.id },
                    dynamicOffset = page.next, dynamicLoading = false, dynamicFailedOffset = null,
                ) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { if (it.mid == mid) it.copy(dynamicLoading = false,
                    dynamicError = error.friendlyMessage(), dynamicFailedOffset = offset) else it }
            }
        }
    }

    fun ensureVideos() {
        val state = _uiState.value
        if (state.page == 0 && state.hasMore && !state.videosLoading && state.videosError == null) loadMore()
    }

    fun ensureOpus() {
        val state = _uiState.value
        if (state.opusPage == 0 && state.opusHasMore && !state.opusLoading && state.opusError == null) {
            loadMoreOpus()
        }
    }

    fun refreshOpus() {
        if (_uiState.value.mid <= 0) return
        opusJob?.cancel()
        _uiState.update { it.copy(opus = emptyList(), opusPage = 0, opusOffset = null,
            opusHasMore = true, opusLoading = false, opusError = null) }
        loadMoreOpus()
    }

    fun loadMoreOpus() {
        val state = _uiState.value
        if (state.mid <= 0 || state.opusLoading || !state.opusHasMore) return
        _uiState.update { it.copy(opusLoading = true, opusError = null) }
        opusJob = viewModelScope.launch {
            try {
                val page = repository.opus(state.mid, state.opusPage + 1, state.opusOffset)
                _uiState.update { if (it.mid == state.mid) it.copy(
                    opus = (it.opus + page.items).distinctBy { item -> item.id },
                    opusPage = state.opusPage + 1, opusOffset = page.next,
                    opusHasMore = page.next != null, opusLoading = false,
                ) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { if (it.mid == state.mid) it.copy(opusLoading = false,
                    opusError = error.friendlyMessage()) else it }
            }
        }
    }

    fun refreshProfile() {
        val mid = _uiState.value.mid
        if (mid <= 0) return
        profileJob?.cancel()
        _uiState.update { it.copy(profileLoading = true, profileError = null) }
        profileJob = viewModelScope.launch {
            try {
                val result = repository.profile(mid)
                _uiState.update { if (it.mid == mid) it.copy(profile = result, profileLoading = false) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { if (it.mid == mid) it.copy(
                    profileLoading = false, profileError = error.friendlyMessage(),
                ) else it }
            }
        }
    }

    fun refreshVideos() {
        val mid = _uiState.value.mid
        if (mid <= 0) return
        videosJob?.cancel()
        _uiState.update { it.copy(videos = emptyList(), page = 0, hasMore = true,
            videosLoading = false, videosError = null) }
        loadMore()
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.mid <= 0 || state.videosLoading || !state.hasMore) return
        val mid = state.mid
        val nextPage = state.page + 1
        _uiState.update { it.copy(videosLoading = true, videosError = null) }
        videosJob = viewModelScope.launch {
            try {
                val result = repository.uploads(mid, nextPage)
                _uiState.update { if (it.mid == mid) it.copy(
                    videos = (it.videos + result.videos).distinctBy { video -> video.aid },
                    page = nextPage, hasMore = result.hasMore, videosLoading = false,
                ) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { if (it.mid == mid) it.copy(
                    videosLoading = false, videosError = error.friendlyMessage(),
                ) else it }
            }
        }
    }

    fun toggleFollow() {
        val state = _uiState.value
        val profile = state.profile ?: return
        if (state.followBusy) return
        if (!sessionManager.loginCookies.value.isLoggedIn) {
            _uiState.update { it.copy(feedback = "请先登录") }
            return
        }
        val mid = profile.mid
        if (mid == (sessionManager.loginCookies.value.mid ?: sessionManager.profile.value?.mid)) return
        _uiState.update { it.copy(followBusy = true) }
        followJob = viewModelScope.launch {
            try {
                interactionRepository.setFollow(mid, !profile.following)
                _uiState.update { if (it.mid == mid) it.copy(
                    profile = it.profile?.copy(following = !profile.following),
                ) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(feedback = error.friendlyMessage()) }
            } finally {
                _uiState.update { it.copy(followBusy = false) }
            }
        }
    }

    fun consumeFeedback() = _uiState.update { it.copy(feedback = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SpaceViewModel(container.spaceRepository, container.dynamicRepository,
                container.interactionRepository,
                container.sessionManager) }
        }
    }
}
