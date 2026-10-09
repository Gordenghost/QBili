package com.qbili.data.repository

import com.qbili.data.remote.api.RankingApi
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.VideoItem

class RankingRepository(private val api: RankingApi) {
    suspend fun popular(page: Int): RankingPage {
        require(page > 0)
        val data = api.popular(page, PAGE_SIZE).requireData()
        val items = data.list.orEmpty().filter { it.aid > 0 }.map { it.toVideoItem() }
        return RankingPage(items, !data.noMore && data.list.orEmpty().isNotEmpty())
    }

    data class RankingPage(val videos: List<VideoItem>, val hasMore: Boolean)

    companion object {
        const val PAGE_SIZE = 20
    }
}
