package com.qbili.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.qbili.data.repository.FeedRepository
import com.qbili.domain.model.RecommendationFilters
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CancellationException

/**
 * 推荐流分页。key 就是 fresh_idx，每翻一页 +1。
 * 推荐流是无限流，没有「最后一页」的概念，只在接口返回空列表时停止。
 */
class RecommendPagingSource(
    private val repository: FeedRepository,
    /** 每次下拉刷新递增，使 PagingSource 重建后能拿到不同内容 */
    private val startIndex: Int,
    private val filters: RecommendationFilters,
    private val currentFilters: () -> RecommendationFilters = { filters },
) : PagingSource<Int, VideoItem>() {

    override fun getRefreshKey(state: PagingState<Int, VideoItem>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, VideoItem> {
        val page = params.key ?: startIndex
        return try {
            val result = repository.recommend(freshIdx = page, filters = currentFilters())
            LoadResult.Page(
                data = result.videos,
                prevKey = null, // 单向无限流，不支持向前翻
                // 只看接口原始条目是否为空；过滤后本页为空仍要继续拉后面的推荐。
                nextKey = if (result.hasMore) page + 1 else null,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
