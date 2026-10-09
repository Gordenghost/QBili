package com.qbili.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.qbili.domain.model.SearchPage

/**
 * 通用「页码翻页」分页源。搜索的三种结果类型都走这一个实现，
 * 差异只在传入的 [loader]。
 */
class PageNumberPagingSource<T : Any>(
    private val firstPage: Int = 1,
    private val loader: suspend (page: Int) -> SearchPage<T>,
) : PagingSource<Int, T>() {

    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val page = params.key ?: firstPage
        return try {
            val result = loader(page)
            LoadResult.Page(
                data = result.items,
                prevKey = if (page > firstPage) page - 1 else null,
                nextKey = if (result.hasMore) page + 1 else null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}
