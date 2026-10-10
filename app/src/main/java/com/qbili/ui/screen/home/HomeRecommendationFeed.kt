package com.qbili.ui.screen.home

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.qbili.data.paging.RecommendPagingSource
import com.qbili.data.paging.AppRecommendPagingSource
import com.qbili.data.repository.FeedRepository
import com.qbili.domain.model.RecommendationFilters
import com.qbili.domain.model.RecommendationSource
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex

@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeRecommendationFeed(
    repository: FeedRepository,
    filterUpdates: Flow<RecommendationFilters>,
    hiddenKeys: StateFlow<Set<String>>,
    scope: CoroutineScope,
    sourceUpdates: Flow<RecommendationSource> = flowOf(RecommendationSource.WEB),
) {
    private val filters = filterUpdates.stateIn(scope, SharingStarted.Eagerly, null)
    val source = sourceUpdates.stateIn(scope, SharingStarted.Eagerly, null)
    private val refreshSeed = MutableStateFlow(1)

    private val pages = source.filterNotNull().withIndex().flatMapLatest { (changeIndex, selectedSource) ->
        flow {
            if (changeIndex > 0) emit(PagingData.empty<VideoItem>())
            emitAll(refreshSeed.flatMapLatest { seed ->
                val initialFilters = filters.filterNotNull().first()
                val config = PagingConfig(
                    pageSize = FeedRepository.PAGE_SIZE,
                    initialLoadSize = FeedRepository.PAGE_SIZE,
                    prefetchDistance = 4,
                    enablePlaceholders = false,
                )
                when (selectedSource) {
                    RecommendationSource.WEB -> Pager(config) {
                        RecommendPagingSource(repository, seed, initialFilters,
                            currentFilters = { filters.value ?: initialFilters })
                    }.flow
                    RecommendationSource.APP -> Pager(config) {
                        AppRecommendPagingSource(repository) { filters.value ?: initialFilters }
                    }.flow
                }
            })
        }
    }.cachedIn(scope)

    val videos: Flow<PagingData<VideoItem>> = combine(pages, filters.filterNotNull(), hiddenKeys) { page, rules, hidden ->
        page.filter { video -> video.key !in hidden && !repository.isBlocked(video, rules) }
    }.cachedIn(scope)

    fun refresh() {
        refreshSeed.update { it + 8 }
    }
}
