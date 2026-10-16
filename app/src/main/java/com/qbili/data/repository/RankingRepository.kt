package com.qbili.data.repository

import com.qbili.data.remote.api.RankingApi
import com.qbili.data.remote.dto.requireData
import com.qbili.core.BiliApiException
import com.qbili.data.remote.dto.PopularVideoDto
import com.qbili.domain.model.RankingCategory
import com.qbili.domain.model.WeeklyIssue
import com.qbili.domain.model.VideoItem

class RankingRepository(private val api: RankingApi) {
    suspend fun popular(page: Int): RankingPage {
        require(page > 0)
        val data = api.popular(page, PAGE_SIZE).requireData()
        val list = data.list ?: throw BiliApiException(-1, "热门接口未返回视频列表")
        return RankingPage(list.videos(), !data.noMore && list.isNotEmpty())
    }

    suspend fun ranking(category: RankingCategory): RankingPage {
        val data = api.ranking(category.rid, "all", "333.934").requireData()
        return RankingPage(data.list.requireVideos(), false, data.note)
    }

    suspend fun weeklyIssues(): List<WeeklyIssue> {
        val list = api.weeklyIssues().requireData().list
            ?: throw BiliApiException(-1, "每周必看接口未返回期数列表")
        return list.filter { it.number > 0 && it.status == 2 }.distinctBy { it.number }
            .sortedByDescending { it.number }.map {
                WeeklyIssue(it.number, it.name.ifBlank { "第${it.number}期" }, it.subject)
            }
    }

    suspend fun weekly(number: Int): RankingPage {
        require(number > 0)
        val data = api.weekly(number).requireData()
        if (data.config != null && data.config.number > 0 && data.config.number != number) {
            throw BiliApiException(-1, "每周必看接口返回了其他期数，请重试")
        }
        return RankingPage(data.list.requireVideos(), false,
            listOf(data.config?.subject.orEmpty(), data.reminder).filter { it.isNotBlank() }.joinToString("\n"),
            data.config?.name.orEmpty())
    }

    suspend fun precious(): RankingPage {
        val data = api.precious().requireData()
        return RankingPage(data.list.requireVideos(), false, data.explain, data.title)
    }

    private fun List<PopularVideoDto>?.requireVideos(): List<VideoItem> =
        (this ?: throw BiliApiException(-1, "接口未返回视频列表，请重试")).videos()

    private fun List<PopularVideoDto>.videos(): List<VideoItem> = filter { it.aid > 0 && it.state >= 0 }
        .distinctBy { it.aid }.map { it.toVideoItem() }

    data class RankingPage(val videos: List<VideoItem>, val hasMore: Boolean,
        val description: String = "", val title: String = "")

    companion object {
        const val PAGE_SIZE = 20
    }
}
