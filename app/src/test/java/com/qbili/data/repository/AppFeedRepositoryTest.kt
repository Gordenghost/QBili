package com.qbili.data.repository

import androidx.paging.PagingSource
import com.qbili.data.paging.AppRecommendPagingSource
import com.qbili.data.remote.api.AppFeedApi
import com.qbili.data.remote.api.FeedApi
import com.qbili.data.remote.api.VideoTagApi
import com.qbili.data.remote.dto.AppFeedArgsDto
import com.qbili.data.remote.dto.AppFeedItemDto
import com.qbili.data.remote.dto.AppRecommendDataDto
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.RecommendDataDto
import com.qbili.data.remote.dto.VideoTagDto
import com.qbili.domain.model.RecommendationFilters
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFeedRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val web = object : FeedApi {
        override suspend fun recommend(freshType: Int, pageSize: Int, freshIdx: Int, freshIdx1h: Int,
            brush: Int, feedVersion: String, homepageVer: Int, webLocation: String, yNum: Int,
            lastYNum: Int): BiliResponse<RecommendDataDto> = error("不能静默调用网页接口")
    }
    private val tags = object : VideoTagApi {
        override suspend fun tags(bvid: String?, aid: Long?): BiliResponse<List<VideoTagDto>> =
            BiliResponse(data = listOf(VideoTagDto(if (bvid == "BVtag") "游戏" else "音乐")))
    }

    private class AppApi(private val response: (Long) -> BiliResponse<AppRecommendDataDto>) : AppFeedApi {
        val requests = mutableListOf<Map<String, String>>()
        val devices = mutableListOf<String>()
        override suspend fun recommend(parameters: Map<String, String>, buvid: String): BiliResponse<AppRecommendDataDto> {
            requests += parameters
            devices += buvid
            return response(parameters.getValue("idx").toLong())
        }
    }

    private fun video(aid: Long, index: Long, bvid: String = "BV$aid", title: String = "视频") =
        AppFeedItemDto(idx = index, param = JsonPrimitive(aid.toString()), bvid = bvid,
            goto = "av", cardGoto = "av", canPlay = 1, title = title)

    @Test
    fun `真实卡片字段解析并补BV升级封面协议`() {
        val item = json.decodeFromString<AppFeedItemDto>("""{
            "idx":1791628518,"card_goto":"av","goto":"av","param":"117364108629613",
            "can_play":1,"title":"标题","cover":"http://i1.hdslb.com/a.jpg",
            "cover_left_text_1":"22.1万","cover_left_text_2":"97","cover_right_text":"4:54",
            "args":{"up_id":403324095,"up_name":"UP","tname":"音乐"},
            "player_args":{"aid":117364108629613,"cid":42360506026,"duration":294},
            "rcmd_reason":"已关注"
        }""")
        val result = requireNotNull(item.toVideoItemOrNull())
        assertEquals(117364108629613L, result.aid)
        assertTrue(result.bvid.matches(Regex("BV1[0-9a-zA-Z]{9}")))
        assertEquals(42360506026L, result.cid)
        assertEquals("https://i1.hdslb.com/a.jpg", result.cover)
        assertEquals(294, result.durationSeconds)
        assertEquals("UP", result.authorName)
        assertEquals(403324095L, result.authorMid)
        assertEquals("音乐", result.channel)
        assertEquals(221000L, result.viewCount)
        assertEquals(97L, result.danmakuCount)
        assertEquals("已关注", result.recommendReason)
    }

    @Test
    fun `排除广告直播番剧和不可播放卡片但保留竖屏视频`() {
        val item = video(1, 1)
        assertNull(item.copy(adInfo = JsonObject(emptyMap())).toVideoItemOrNull())
        assertNull(item.copy(cardGoto = "ad_av").toVideoItemOrNull())
        assertNull(item.copy(goto = "live").toVideoItemOrNull())
        assertNull(item.copy(goto = "bangumi").toVideoItemOrNull())
        assertNull(item.copy(canPlay = 0).toVideoItemOrNull())
        assertEquals("BV1", item.copy(goto = "vertical_av").toVideoItemOrNull()?.bvid)
        assertNull(item.copy(param = JsonPrimitive("invalid")).toVideoItemOrNull())
    }

    @Test
    fun `兼容推荐理由对象和缺失player参数并正确解析数字`() {
        val item = video(170001, 1, "").copy(coverRightText = "1:02:03",
            rcmdReason = JsonObject(mapOf("content" to JsonPrimitive("热门"))))
        val result = requireNotNull(item.toVideoItemOrNull())
        assertEquals("BV17x411w7KC", result.bvid)
        assertEquals(3723, result.durationSeconds)
        assertEquals("热门", result.recommendReason)
        assertEquals(120000000L, parseAppFeedCount("1.2亿"))
        assertEquals(12345L, parseAppFeedCount("12,345"))
        assertEquals(0L, parseAppFeedCount("直播中"))
    }

    @Test
    fun `App同样应用标题标签频道UP和隐藏视频屏蔽`() = runBlocking {
        val app = AppApi { BiliResponse(data = AppRecommendDataDto(items = listOf(
            video(1, 9, "BVhidden"), video(2, 8, "BVtitle", "game实况"),
            video(3, 7, "BVauthor").copy(args = AppFeedArgsDto(upId = 42)),
            video(4, 6, "BVchannel").copy(args = AppFeedArgsDto(tname = "生活")),
            video(5, 5, "BVtag"), video(6, 4, "BVok"),
        ))) }
        val result = FeedRepository(web, tags, app).recommendApp(0, RecommendationFilters(
            titleKeywords = setOf("GAME"), tagKeywords = setOf("游戏"), blockedAuthors = setOf(42),
            hiddenVideos = setOf("BVhidden"), blockedChannels = setOf("生活"),
        ))
        assertEquals(listOf("BVok"), result.videos.map { it.key })
        assertEquals(4L, result.nextIndex)
        assertTrue(result.hasMore)
    }

    @Test
    fun `整页被屏蔽仍按原始末项Long游标加载且后续使用最新规则`() = runBlocking {
        val cursor = 4_000_000_000L
        val app = AppApi { index -> BiliResponse(data = AppRecommendDataDto(items = when (index) {
            0L -> listOf(video(1, cursor, "BVhidden"))
            cursor -> listOf(video(2, cursor - 1, "BVnew"))
            else -> emptyList()
        })) }
        var filters = RecommendationFilters(hiddenVideos = setOf("BVhidden"))
        val source = AppRecommendPagingSource(FeedRepository(web, tags, app)) { filters }
        val first = source.load(PagingSource.LoadParams.Refresh(null, 12, false)) as PagingSource.LoadResult.Page
        assertTrue(first.data.isEmpty())
        assertEquals(cursor, first.nextKey)
        filters = RecommendationFilters(hiddenVideos = setOf("BVnew"))
        val next = source.load(PagingSource.LoadParams.Append(cursor, 12, false)) as PagingSource.LoadResult.Page
        assertTrue(next.data.isEmpty())
        assertEquals(cursor - 1, next.nextKey)
        val last = source.load(PagingSource.LoadParams.Append(cursor - 1, 12, false)) as PagingSource.LoadResult.Page
        assertNull(last.nextKey)
        assertEquals(listOf("true", "false", "false"), app.requests.map { it["pull"] })
    }

    @Test
    fun `移动端凭据每次读取且匿名不带空access_key`() = runBlocking {
        var token: String? = "example-token"
        val app = AppApi { BiliResponse(data = AppRecommendDataDto(items = emptyList())) }
        val repository = FeedRepository(web, tags, app, { token }, { "XYdevice" })
        repository.recommendApp(0, RecommendationFilters())
        token = null
        repository.recommendApp(1, RecommendationFilters())
        assertEquals("example-token", app.requests[0]["access_key"])
        assertFalse(app.requests[1].containsKey("access_key"))
        assertEquals(listOf("XYdevice", "XYdevice"), app.devices)
    }

    @Test
    fun `App错误不回退网页端且空数据风控和坏游标不当成成功`() = runBlocking {
        val responses = listOf(
            BiliResponse<AppRecommendDataDto>(code = -101, message = "未登录"),
            BiliResponse(data = AppRecommendDataDto()),
            BiliResponse(data = AppRecommendDataDto(items = emptyList(), vVoucher = "challenge")),
            BiliResponse(data = AppRecommendDataDto(items = listOf(video(1, 1).copy(idx = null)))),
            BiliResponse(data = AppRecommendDataDto(items = listOf(video(1, 10)))),
        )
        responses.forEach { response ->
            val paging = AppRecommendPagingSource(FeedRepository(web, tags, AppApi { response })) { RecommendationFilters() }
            assertTrue(paging.load(PagingSource.LoadParams.Refresh(10L, 12, false)) is PagingSource.LoadResult.Error)
        }
    }
}
