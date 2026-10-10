package com.qbili.data.repository

import androidx.paging.PagingSource
import com.qbili.core.BiliRiskControlException
import com.qbili.data.paging.RecommendPagingSource
import com.qbili.data.remote.api.FeedApi
import com.qbili.data.remote.api.VideoTagApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.FeedItemDto
import com.qbili.data.remote.dto.RecommendDataDto
import com.qbili.data.remote.dto.VideoTagDto
import com.qbili.domain.model.RecommendationFilters
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FeedRepositoryTest {
    private class FakeFeedApi(private val pages: Map<Int, List<FeedItemDto>>) : FeedApi {
        override suspend fun recommend(
            freshType: Int,
            pageSize: Int,
            freshIdx: Int,
            freshIdx1h: Int,
            brush: Int,
            feedVersion: String,
            homepageVer: Int,
            webLocation: String,
            yNum: Int,
            lastYNum: Int,
        ): BiliResponse<RecommendDataDto> {
            assertEquals(12, pageSize)
            return BiliResponse(data = RecommendDataDto(item = pages[freshIdx].orEmpty()))
        }
    }

    private class FakeVideoTagApi(
        private val tagsByVideo: Map<String, List<String>> = emptyMap(),
        private val missingDataFor: String? = null,
    ) : VideoTagApi {
        val requested: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override suspend fun tags(bvid: String?, aid: Long?): BiliResponse<List<VideoTagDto>> {
            val key = bvid ?: "av$aid"
            requested += key
            if (key == missingDataFor) return BiliResponse(code = 0, data = null)
            return BiliResponse(data = tagsByVideo[key].orEmpty().map(::VideoTagDto))
        }
    }

    @Test
    fun `本地不感兴趣频道及UP屏蔽在请求标签前生效`() = runBlocking {
        val tags = FakeVideoTagApi()
        val repository = FeedRepository(FakeFeedApi(mapOf(1 to listOf(
            FeedItemDto(id = 1, bvid = "BVhidden", tname = "生活"),
            FeedItemDto(id = 2, bvid = "BVauthor", owner = com.qbili.data.remote.dto.OwnerDto(mid = 42)),
            FeedItemDto(id = 3, bvid = "BVchannel", tname = "游戏"),
            FeedItemDto(id = 4, bvid = "BVok", tname = "生活"),
        ))), tags)
        val result = repository.recommend(1, RecommendationFilters(
            hiddenVideos = setOf("BVhidden"), blockedAuthors = setOf(42),
            blockedChannels = setOf("游戏"),
        ))
        assertEquals(listOf("BVok"), result.videos.map { it.bvid })
        assertTrue(tags.requested.isEmpty())
    }

    @Test
    fun `先过滤标题再查询未命中标题视频的标签`() = runBlocking {
        val feedApi = FakeFeedApi(mapOf(1 to listOf(
            FeedItemDto(id = 1, bvid = "BV1title", title = "GAME 实况"),
            FeedItemDto(id = 2, bvid = "BV2tag", title = "今天去哪玩"),
            FeedItemDto(id = 3, bvid = "BV3ok", title = "手工小屋"),
            FeedItemDto(id = 4, bvid = "BV4picture", goto = "picture", title = "图文"),
        )))
        val tagApi = FakeVideoTagApi(mapOf(
            "BV2tag" to listOf("户外VLOG"),
            "BV3ok" to listOf("手工"),
        ))
        val result = FeedRepository(feedApi, tagApi).recommend(
            freshIdx = 1,
            filters = RecommendationFilters(titleKeywords = setOf("game"), tagKeywords = setOf("vlog")),
        )

        assertEquals(listOf("BV3ok"), result.videos.map { it.bvid })
        assertEquals(setOf("BV2tag", "BV3ok"), tagApi.requested.toSet())
        assertTrue(result.hasMore)
    }

    @Test
    fun `没有 Tag 屏蔽时完全不请求标签`() = runBlocking {
        val tags = FakeVideoTagApi(missingDataFor = "BV1ok")
        val repository = FeedRepository(FakeFeedApi(mapOf(1 to listOf(
            FeedItemDto(id = 1, bvid = "BV1ok", title = "安全标题"),
        ))), tags)

        assertEquals(1, repository.recommend(1, RecommendationFilters()).videos.size)
        assertTrue(tags.requested.isEmpty())
    }

    @Test
    fun `推荐视频仅有 aid 时也能查询标签`() = runBlocking {
        val tagApi = FakeVideoTagApi(mapOf("av42" to listOf("宠物")))
        val repository = FeedRepository(FakeFeedApi(mapOf(1 to listOf(
            FeedItemDto(id = 42, title = "毛孩子日常"),
        ))), tagApi)

        val result = repository.recommend(1, RecommendationFilters(tagKeywords = setOf("宠物")))
        assertTrue(result.videos.isEmpty())
        assertEquals(listOf("av42"), tagApi.requested)
    }

    @Test
    fun `整页被屏蔽后继续翻页且接口原始空页才结束`() = runBlocking {
        val repository = FeedRepository(FakeFeedApi(mapOf(
            7 to listOf(FeedItemDto(id = 7, bvid = "BV7", title = "不想看")),
            8 to listOf(FeedItemDto(id = 8, bvid = "BV8", title = "想看")),
        )), FakeVideoTagApi())
        val source = RecommendPagingSource(repository, 7, RecommendationFilters(titleKeywords = setOf("不想看")))
        val first = source.load(refresh(key = 7))
        assertTrue(first is PagingSource.LoadResult.Page)
        first as PagingSource.LoadResult.Page
        assertTrue(first.data.isEmpty())
        assertEquals(8, first.nextKey)

        val second = source.load(PagingSource.LoadParams.Append(key = 8, loadSize = 12, placeholdersEnabled = false))
        assertTrue(second is PagingSource.LoadResult.Page)
        second as PagingSource.LoadResult.Page
        assertEquals("BV8", second.data.single().bvid)
        assertEquals(9, second.nextKey)

        val last = source.load(PagingSource.LoadParams.Append(key = 9, loadSize = 12, placeholdersEnabled = false))
        assertTrue(last is PagingSource.LoadResult.Page)
        last as PagingSource.LoadResult.Page
        assertTrue(last.data.isEmpty())
        assertEquals(null, last.nextKey)
    }

    @Test
    fun `标签接口 code 为零但不返回标签时不放行视频`() = runBlocking {
        val repository = FeedRepository(FakeFeedApi(mapOf(1 to listOf(
            FeedItemDto(id = 1, bvid = "BV1unknown", title = "看似正常"),
        ))), FakeVideoTagApi(missingDataFor = "BV1unknown"))
        val source = RecommendPagingSource(repository, 1, RecommendationFilters(tagKeywords = setOf("旅行")))

        assertTrue(source.load(refresh(key = 1)) is PagingSource.LoadResult.Error)
    }

    @Test
    fun `标签接口可以解析 tag_name`() {
        val response = json.decodeFromString<BiliResponse<List<VideoTagDto>>>(
                """{"code":0,"data":[{"tag_id":1,"tag_name":"旅行VLOG"}]}""",
            )
        assertEquals("旅行VLOG", response.data!!.single().tagName)
    }

    @Test
    fun `标签接口漏掉名称不视为无标签`() {
        try {
            json.decodeFromString<BiliResponse<List<VideoTagDto>>>(
                """{"code":0,"data":[{"tag_id":1}]}""",
            )
            fail("缺失 tag_name 必须抛出异常")
        } catch (_: SerializationException) {
            // 标签缺失必须中断筛选，不能当作空标签允许视频出现。
        }
    }

    @Test
    fun `网页推荐缺失列表不能假装成功空页且风控应抛出`() = runBlocking {
        val bodies = listOf(
            """{"code":0,"data":{}}""",
            """{"code":0,"data":{"v_voucher":"challenge"}}""",
        )
        bodies.forEachIndexed { index, body ->
            val api = object : FeedApi {
                override suspend fun recommend(freshType: Int, pageSize: Int, freshIdx: Int, freshIdx1h: Int,
                    brush: Int, feedVersion: String, homepageVer: Int, webLocation: String, yNum: Int,
                    lastYNum: Int): BiliResponse<RecommendDataDto> = json.decodeFromString(body)
            }
            val source = RecommendPagingSource(FeedRepository(api, FakeVideoTagApi()), 1, RecommendationFilters())
            val result = source.load(refresh(1))
            assertTrue(result is PagingSource.LoadResult.Error)
            if (index == 1) assertTrue((result as PagingSource.LoadResult.Error).throwable is BiliRiskControlException)
        }
    }

    private fun refresh(key: Int): PagingSource.LoadParams.Refresh<Int> =
        PagingSource.LoadParams.Refresh(key = key, loadSize = 12, placeholdersEnabled = false)

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
