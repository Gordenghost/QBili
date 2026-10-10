package com.qbili.ui.screen.home

import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import com.qbili.data.paging.RecommendPagingSource
import com.qbili.data.remote.api.FeedApi
import com.qbili.data.remote.api.AppFeedApi
import com.qbili.data.remote.api.VideoTagApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.AppRecommendDataDto
import com.qbili.data.remote.dto.AppFeedItemDto
import com.qbili.data.remote.dto.FeedItemDto
import com.qbili.data.remote.dto.RecommendDataDto
import com.qbili.data.remote.dto.VideoTagDto
import com.qbili.data.repository.FeedRepository
import com.qbili.domain.model.RecommendationFilters
import com.qbili.domain.model.RecommendationSource
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRecommendationFeedTest {
    private class FakeFeedApi : FeedApi {
        val requests = mutableListOf<Int>()
        override suspend fun recommend(freshType: Int, pageSize: Int, freshIdx: Int, freshIdx1h: Int,
            brush: Int, feedVersion: String, homepageVer: Int, webLocation: String, yNum: Int,
            lastYNum: Int): BiliResponse<RecommendDataDto> {
            requests += freshIdx
            return BiliResponse(data = RecommendDataDto(item = listOf(
                FeedItemDto(id = 1, bvid = "BV1", title = "first"),
                FeedItemDto(id = 2, bvid = "BV2", title = "second"),
                FeedItemDto(id = 3, bvid = "BV3", title = "third"),
            )))
        }
    }

    private class FakeTagApi : VideoTagApi {
        var requests = 0
        override suspend fun tags(bvid: String?, aid: Long?): BiliResponse<List<VideoTagDto>> {
            requests++
            return BiliResponse(data = listOf(VideoTagDto(if (bvid == "BV2") "game" else "music")))
        }
    }

    private fun presenter() = object : PagingDataPresenter<VideoItem>(Dispatchers.Unconfined) {
        override suspend fun presentPagingDataEvent(event: PagingDataEvent<VideoItem>) = Unit
    }

    private class FakeAppFeedApi(private val gate: CompletableDeferred<Unit>? = null,
        private val count: Int = 1) : AppFeedApi {
        val requests = mutableListOf<Long>()
        override suspend fun recommend(parameters: Map<String, String>, buvid: String): BiliResponse<AppRecommendDataDto> {
            requests += parameters.getValue("idx").toLong()
            gate?.await()
            return BiliResponse(data = AppRecommendDataDto(items = List(count) { itemIndex ->
                AppFeedItemDto(idx = 100L - itemIndex, param = JsonPrimitive((170001 + itemIndex).toString()),
                    bvid = if (itemIndex == 0) "BVapp" else "BVapp$itemIndex", goto = "av",
                    cardGoto = "av", canPlay = 1, title = "App视频")
            }))
        }
    }

    @Test
    fun `切换来源清空旧推荐重新分页并且屏蔽规则不丢失`() = runBlocking {
        val web = FakeFeedApi()
        val app = FakeAppFeedApi()
        val sources = MutableStateFlow(RecommendationSource.WEB)
        val rules = MutableStateFlow(RecommendationFilters(hiddenVideos = setOf("BV2")))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(web, FakeTagApi(), app), rules,
            MutableStateFlow(emptySet()), scope, sources)
        val differ = presenter()
        try {
            scope.launch { feed.videos.collectLatest { differ.collectFrom(it) } }
            withTimeout(5000) { while (differ.size != 2) yield() }
            sources.value = RecommendationSource.APP
            withTimeout(5000) { while (differ.snapshot().items.map { it.key } != listOf("BVapp")) yield() }
            assertEquals(listOf(1), web.requests)
            assertEquals(listOf(0L), app.requests)
            feed.refresh()
            withTimeout(5000) { while (app.requests.size < 2) yield() }
            assertEquals(listOf(0L, 0L), app.requests)
            sources.value = RecommendationSource.WEB
            withTimeout(5000) { while (differ.snapshot().items.map { it.key } != listOf("BV1", "BV3")) yield() }
            assertEquals(listOf(1, 9), web.requests)
            assertEquals(listOf(0L, 0L), app.requests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `推荐来源尚未读取时不能误发网页推荐请求`() = runBlocking {
        val web = FakeFeedApi()
        val app = FakeAppFeedApi()
        val sources = MutableSharedFlow<RecommendationSource>(replay = 1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(web, FakeTagApi(), app),
            MutableStateFlow(RecommendationFilters()), MutableStateFlow(emptySet()), scope, sources)
        val differ = presenter()
        try {
            scope.launch { feed.videos.collectLatest { differ.collectFrom(it) } }
            repeat(10) { yield() }
            assertTrue(web.requests.isEmpty())
            assertTrue(app.requests.isEmpty())
            sources.emit(RecommendationSource.APP)
            withTimeout(5000) { while (differ.size != 1) yield() }
            assertTrue(web.requests.isEmpty())
            assertEquals(listOf(0L), app.requests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `切换时旧来源在途请求取消不能覆盖新推荐`() = runBlocking {
        val web = FakeFeedApi()
        val gate = CompletableDeferred<Unit>()
        val app = FakeAppFeedApi(gate)
        val sources = MutableStateFlow(RecommendationSource.APP)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(web, FakeTagApi(), app),
            MutableStateFlow(RecommendationFilters()), MutableStateFlow(emptySet()), scope, sources)
        val differ = presenter()
        try {
            scope.launch { feed.videos.collectLatest { differ.collectFrom(it) } }
            withTimeout(5000) { while (app.requests.isEmpty()) yield() }
            sources.value = RecommendationSource.WEB
            withTimeout(5000) { while (differ.size != 3) yield() }
            gate.complete(Unit)
            repeat(20) { yield() }
            assertEquals(listOf("BV1", "BV2", "BV3"), differ.snapshot().items.map { it.key })
            assertEquals(listOf(1), web.requests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `App推荐隐藏与更新屏蔽只移除卡片不重新请求`() = runBlocking {
        val web = FakeFeedApi()
        val app = FakeAppFeedApi(count = 12)
        val rules = MutableStateFlow(RecommendationFilters())
        val hidden = MutableStateFlow<Set<String>>(emptySet())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(web, FakeTagApi(), app), rules, hidden, scope,
            MutableStateFlow(RecommendationSource.APP))
        val differ = presenter()
        try {
            scope.launch { feed.videos.collectLatest { differ.collectFrom(it) } }
            withTimeout(5000) { while (differ.size != 12) yield() }
            hidden.value = setOf("BVapp")
            withTimeout(5000) { while (differ.size != 11) yield() }
            rules.value = RecommendationFilters(hiddenVideos = setOf("BVapp"))
            repeat(10) { yield() }
            assertEquals(listOf(0L), app.requests)
            assertEquals((1..11).map { "BVapp$it" }, differ.snapshot().items.map { it.key })
            assertTrue(web.requests.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `隐藏和更新规则只过滤缓存不重新拉取推荐`() = runBlocking {
        val api = FakeFeedApi()
        val rules = MutableStateFlow(RecommendationFilters())
        val hidden = MutableStateFlow<Set<String>>(emptySet())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(api, FakeTagApi()), rules, hidden, scope)
        val differ = presenter()
        val snapshots = mutableListOf<List<String>>()
        try {
            scope.launch { differ.onPagesUpdatedFlow.collect { snapshots += differ.snapshot().items.map { it.key } } }
            scope.launch { feed.videos.collectLatest { differ.collectFrom(it) } }
            withTimeout(5000) { while (differ.size != 3) yield() }
            val originalRequests = api.requests.toList()
            hidden.value = setOf("BV2")
            withTimeout(5000) { while (differ.snapshot().items.map { it.key } != listOf("BV1", "BV3")) yield() }
            rules.value = RecommendationFilters(hiddenVideos = setOf("BV2"))
            withTimeout(5000) { while (snapshots.size < 3) yield() }
            assertEquals(originalRequests, api.requests)
            assertEquals(listOf("BV1", "BV3"), differ.snapshot().items.map { it.key })
            assertFalse(snapshots.any { it.isEmpty() })
            feed.refresh()
            withTimeout(5000) { while (api.requests.last() != 9) yield() }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `标签规则更新时校验已有视频但不请求新推荐`() = runBlocking {
        val api = FakeFeedApi()
        val tags = FakeTagApi()
        val rules = MutableStateFlow(RecommendationFilters())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(api, tags), rules, MutableStateFlow(emptySet()), scope)
        val differ = presenter()
        try {
            scope.launch { feed.videos.collectLatest { differ.collectFrom(it) } }
            withTimeout(5000) { while (differ.size != 3) yield() }
            val originalRequests = api.requests.toList()
            rules.value = RecommendationFilters(tagKeywords = setOf("game"))
            withTimeout(5000) { while (differ.snapshot().items.map { it.key } != listOf("BV1", "BV3")) yield() }
            assertEquals(originalRequests, api.requests)
            assertEquals(3, tags.requests)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `持久化规则未读取完成前不能请求推荐或放行视频`() = runBlocking {
        val api = FakeFeedApi()
        val rules = MutableSharedFlow<RecommendationFilters>(replay = 1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val feed = HomeRecommendationFeed(FeedRepository(api, FakeTagApi()), rules, MutableStateFlow(emptySet()), scope)
        val presenter = presenter()
        try {
            scope.launch { feed.videos.collectLatest { presenter.collectFrom(it) } }
            repeat(10) { yield() }
            assertEquals(emptyList<Int>(), api.requests)
            rules.emit(RecommendationFilters(hiddenVideos = setOf("BV2")))
            withTimeout(5000) { while (presenter.size != 2) yield() }
            assertEquals(listOf("BV1", "BV3"), presenter.snapshot().items.map { it.key })
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `同一分页源加载后续页时使用最新屏蔽规则`() = runBlocking {
        val repository = FeedRepository(FakeFeedApi(), FakeTagApi())
        var rules = RecommendationFilters()
        val source = RecommendPagingSource(repository, 1, rules, currentFilters = { rules })
        source.load(androidx.paging.PagingSource.LoadParams.Refresh(1, 12, false))
        rules = RecommendationFilters(hiddenVideos = setOf("BV2"))
        val result = source.load(androidx.paging.PagingSource.LoadParams.Append(2, 12, false))
        val page = result as androidx.paging.PagingSource.LoadResult.Page
        assertEquals(listOf("BV1", "BV3"), page.data.map { it.key })
    }
}
