package com.qbili.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.qbili.data.repository.FeedRepository
import com.qbili.domain.model.RecommendationFilters
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CancellationException

class AppRecommendPagingSource(
    private val repository: FeedRepository,
    private val currentFilters: () -> RecommendationFilters,
) : PagingSource<Long, VideoItem>() {
    override fun getRefreshKey(state: PagingState<Long, VideoItem>): Long? = null

    override suspend fun load(params: LoadParams<Long>): LoadResult<Long, VideoItem> = try {
        val result = repository.recommendApp(params.key ?: 0L, currentFilters())
        LoadResult.Page(data = result.videos, prevKey = null, nextKey = result.nextIndex)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        LoadResult.Error(error)
    }
}
