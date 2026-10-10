package com.qbili.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qbili.data.local.RecommendationFilterStore
import com.qbili.data.local.RecommendationSettingsStore
import com.qbili.data.repository.FeedRepository
import com.qbili.data.repository.InteractionRepository
import com.qbili.core.friendlyMessage
import com.qbili.di.AppContainer
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update

class HomeViewModel(
    private val repository: FeedRepository,
    private val filterStore: RecommendationFilterStore,
    private val interactionRepository: InteractionRepository,
    settingsStore: RecommendationSettingsStore,
) : ViewModel() {
    private val _hiddenKeys = MutableStateFlow<Set<String>>(emptySet())
    val hiddenKeys = _hiddenKeys.asStateFlow()
    private val feedbackEvents = HomeFeedbackEvents()
    val feedback = feedbackEvents.events
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()

    fun hide(video: VideoItem) = perform(video) {
        filterStore.hideVideo(video.key)
    }

    fun blockChannel(video: VideoItem) = perform(video) {
        filterStore.blockChannel(video.channel)
    }

    fun blockSimilar(video: VideoItem) = perform(video) {
        val tag = repository.primaryTag(video)
            ?: throw IllegalStateException("未找到可用于屏蔽的标签")
        filterStore.addTagKeyword(tag)
    }

    fun blockAuthor(video: VideoItem) = perform(video) {
        interactionRepository.blockUser(video.authorMid)
        filterStore.blockAuthor(video.authorMid)
    }

    private fun perform(video: VideoItem, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _hiddenKeys.update { it + video.key }
        viewModelScope.launch {
            try {
                action()
                feedbackEvents.send("成功屏蔽")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _hiddenKeys.update { it - video.key }
                feedbackEvents.send("操作失败：${error.friendlyMessage()}")
            } finally {
                _busy.value = false
            }
        }
    }

    private val feed = HomeRecommendationFeed(repository, filterStore.filters, _hiddenKeys, viewModelScope,
        settingsStore.source)
    val recommendationSource = feed.source
    val videos = feed.videos

    fun refresh() {
        feed.refresh()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container.feedRepository, container.recommendationFilterStore,
                container.interactionRepository, container.recommendationSettingsStore) }
        }
    }
}

internal class HomeFeedbackEvents {
    private val channel = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = channel.receiveAsFlow()

    suspend fun send(message: String) = channel.send(message)
}
