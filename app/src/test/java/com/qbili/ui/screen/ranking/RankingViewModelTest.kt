package com.qbili.ui.screen.ranking

import com.qbili.data.remote.api.RankingApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.PopularPageDto
import com.qbili.data.remote.dto.PopularVideoDto
import com.qbili.data.remote.dto.RankingListDto
import com.qbili.data.remote.dto.WeeklyIssueDto
import com.qbili.data.remote.dto.WeeklyIssuesDto
import com.qbili.data.repository.RankingRepository
import com.qbili.domain.model.RankingCategory
import com.qbili.domain.model.RankingTab
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingViewModelTest {
    private class Api : RankingApi {
        val popularPages = mutableListOf<Int>()
        val rankingRegions = mutableListOf<Int>()
        val weeklyNumbers = mutableListOf<Int>()
        var popularCall: suspend (Int) -> BiliResponse<PopularPageDto> = { page -> popularResponse(page.toLong()) }
        var rankingCall: suspend (Int) -> BiliResponse<RankingListDto> = { region ->
            BiliResponse(data = RankingListDto(list = listOf(PopularVideoDto(aid = region.toLong() + 100))))
        }
        var weeklyCall: suspend (Int) -> BiliResponse<RankingListDto> = { number ->
            BiliResponse(data = RankingListDto(list = listOf(PopularVideoDto(aid = number.toLong())),
                config = WeeklyIssueDto(number = number)))
        }
        var issues = listOf(WeeklyIssueDto(number = 2, status = 2), WeeklyIssueDto(number = 1, status = 2))
        var issueCalls = 0
        var issuesFailure = false

        override suspend fun popular(page: Int, pageSize: Int): BiliResponse<PopularPageDto> {
            popularPages += page
            return popularCall(page)
        }
        override suspend fun ranking(regionId: Int, type: String, webLocation: String): BiliResponse<RankingListDto> {
            rankingRegions += regionId
            return rankingCall(regionId)
        }
        override suspend fun weeklyIssues(): BiliResponse<WeeklyIssuesDto> {
            issueCalls++
            if (issuesFailure) error("期数列表失败")
            return BiliResponse(data = WeeklyIssuesDto(list = issues))
        }
        override suspend fun weekly(number: Int): BiliResponse<RankingListDto> {
            weeklyNumbers += number
            return weeklyCall(number)
        }
        override suspend fun precious() = BiliResponse(data = RankingListDto(list = listOf(PopularVideoDto(aid = 99))))
    }

    @Test
    fun `切换栏目取消旧响应即使其忽略取消也不能串页`() = runBlocking {
        val delayed = CompletableDeferred<BiliResponse<PopularPageDto>>()
        val api = Api().apply { popularCall = { withContext(NonCancellable) { delayed.await() } } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.refresh()
            model.selectTab(RankingTab.PRECIOUS)
            delayed.complete(popularResponse(42))
            assertEquals(RankingTab.PRECIOUS, model.uiState.value.tab)
            assertEquals(listOf(99L), model.uiState.value.videos.map { it.aid })
            assertFalse(model.uiState.value.loading)
        } finally { scope.cancel() }
    }

    @Test
    fun `分区切换旧返回和旧异常都不能覆盖新榜单`() = runBlocking {
        val delayed = CompletableDeferred<BiliResponse<RankingListDto>>()
        val api = Api().apply { rankingCall = { region ->
            if (region == 0) withContext(NonCancellable) { delayed.await() }
            else BiliResponse(data = RankingListDto(list = listOf(PopularVideoDto(aid = 50))))
        } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.selectTab(RankingTab.RANKING)
            model.selectCategory(RankingCategory.GAME)
            delayed.completeExceptionally(IllegalStateException("旧请求失败"))
            assertEquals(listOf(0, 4), api.rankingRegions)
            assertEquals(RankingCategory.GAME, model.uiState.value.category)
            assertEquals(50L, model.uiState.value.videos.single().aid)
            assertNull(model.uiState.value.error)
        } finally { scope.cancel() }
    }

    @Test
    fun `综合热门分页失败保留列表重试同页并阻止自动重试风暴`() = runBlocking {
        var fail = true
        val api = Api().apply { popularCall = { page ->
            if (page == 2 && fail) error("分页失败") else popularResponse(page.toLong(), noMore = page == 2)
        } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.refresh(); model.loadMore()
            assertEquals(1, model.uiState.value.page)
            assertEquals(listOf(1L), model.uiState.value.videos.map { it.aid })
            assertEquals(2, model.uiState.value.failedPage)
            assertNotNull(model.uiState.value.error)
            model.loadMore()
            assertEquals(listOf(1, 2), api.popularPages)
            fail = false
            model.retry()
            assertEquals(listOf(1, 2, 2), api.popularPages)
            assertEquals(listOf(1L, 2L), model.uiState.value.videos.map { it.aid })
            assertFalse(model.uiState.value.hasMore)
            assertNull(model.uiState.value.error)
        } finally { scope.cancel() }
    }

    @Test
    fun `刷新失败保留原列表重试第一页而不是追加下一页`() = runBlocking {
        var fail = false
        val api = Api().apply { popularCall = { if (fail) error("刷新失败") else popularResponse(1) } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.refresh(); fail = true; model.refresh()
            assertEquals(1L, model.uiState.value.videos.single().aid)
            assertEquals(1, model.uiState.value.failedPage)
            fail = false; model.retry()
            assertEquals(listOf(1, 1, 1), api.popularPages)
            assertNull(model.uiState.value.error)
        } finally { scope.cancel() }
    }

    @Test
    fun `重复页停止自动加载非综合栏目不分页`() = runBlocking {
        val api = Api().apply { popularCall = { popularResponse(1) } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.refresh(); model.loadMore(); model.loadMore()
            assertEquals(listOf(1, 2), api.popularPages)
            assertEquals(1, model.uiState.value.videos.size)
            assertFalse(model.uiState.value.hasMore)
            model.selectTab(RankingTab.PRECIOUS); model.loadMore()
            assertFalse(model.uiState.value.hasMore)
            assertEquals(listOf(1, 2), api.popularPages)
        } finally { scope.cancel() }
    }

    @Test
    fun `每周默认最新选期保留切换后选择失效期回退最新`() = runBlocking {
        val api = Api()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.selectTab(RankingTab.WEEKLY)
            assertEquals(2, model.uiState.value.weeklyNumber)
            model.selectWeek(1); model.selectWeek(999)
            assertEquals(1, model.uiState.value.weeklyNumber)
            model.selectTab(RankingTab.PRECIOUS); model.selectTab(RankingTab.WEEKLY)
            assertEquals(1, model.uiState.value.weeklyNumber)
            api.issues = listOf(WeeklyIssueDto(number = 3, status = 2)); model.refresh()
            assertEquals(3, model.uiState.value.weeklyNumber)
            assertEquals(listOf(2, 1, 1, 3), api.weeklyNumbers)
        } finally { scope.cancel() }
    }

    @Test
    fun `切换已加载期数复用期数列表但主动刷新重新获取`() = runBlocking {
        val api = Api()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.selectTab(RankingTab.WEEKLY)
            assertEquals(1, api.issueCalls)
            api.issuesFailure = true
            model.selectWeek(1)
            assertEquals(1, api.issueCalls)
            assertEquals(1L, model.uiState.value.videos.single().aid)
            assertNull(model.uiState.value.error)
            model.refresh()
            assertEquals(2, api.issueCalls)
            assertNotNull(model.uiState.value.error)
            assertEquals(1L, model.uiState.value.videos.single().aid)
        } finally { scope.cancel() }
    }

    @Test
    fun `同栏目后发刷新先完成时旧结果不能覆盖新内容`() = runBlocking {
        val delayed = CompletableDeferred<BiliResponse<PopularPageDto>>()
        var calls = 0
        val api = Api().apply { popularCall = {
            calls++
            if (calls == 1) withContext(NonCancellable) { delayed.await() } else popularResponse(2)
        } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.refresh(); model.refresh()
            delayed.complete(popularResponse(1))
            assertEquals(2L, model.uiState.value.videos.single().aid)
            assertFalse(model.uiState.value.loading)
            assertFalse(model.uiState.value.refreshing)
        } finally { scope.cancel() }
    }

    @Test
    fun `每周期数成功详情失败仍能选期重试且空期数不请求零期`() = runBlocking {
        val api = Api().apply { weeklyCall = { error("详情失败") } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = RankingViewModel(RankingRepository(api), scope)
            model.selectTab(RankingTab.WEEKLY)
            assertEquals(2, model.uiState.value.issues.size)
            assertNotNull(model.uiState.value.error)
            api.weeklyCall = { number -> BiliResponse(data = RankingListDto(list = listOf(PopularVideoDto(aid = number.toLong())))) }
            model.retry()
            assertEquals(2L, model.uiState.value.videos.single().aid)
            api.issues = emptyList(); model.refresh()
            assertEquals(0, model.uiState.value.weeklyNumber)
            assertTrue(model.uiState.value.videos.isEmpty())
            assertNull(model.uiState.value.error)
            assertEquals(listOf(2, 2), api.weeklyNumbers)
        } finally { scope.cancel() }
    }

    companion object {
        private fun popularResponse(aid: Long, noMore: Boolean = false) =
            BiliResponse(data = PopularPageDto(list = listOf(PopularVideoDto(aid = aid)), noMore = noMore))
    }
}
