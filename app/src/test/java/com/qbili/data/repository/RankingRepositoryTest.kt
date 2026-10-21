package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.RankingApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.OwnerDto
import com.qbili.data.remote.dto.PopularPageDto
import com.qbili.data.remote.dto.PopularVideoDto
import com.qbili.data.remote.dto.VideoStatDto
import com.qbili.data.remote.dto.RankingListDto
import com.qbili.data.remote.dto.WeeklyIssueDto
import com.qbili.data.remote.dto.WeeklyIssuesDto
import com.qbili.domain.model.RankingCategory
import com.qbili.domain.model.SeasonRankingCategory
import com.qbili.domain.model.SeasonRankingPeriod
import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonRankingDto
import com.qbili.data.remote.dto.SeasonRankingEntryDto
import com.qbili.core.BiliRiskControlException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingRepositoryTest {
    private class FakeApi : RankingApi {
        var requestedPage = 0
        var code = 0
        var requestedRegion = -1
        var requestedWeek = 0
        val seasonRequests = mutableListOf<Triple<Boolean, Int, Int>>()
        var seasonData = SeasonRankingDto(list = listOf(SeasonRankingEntryDto(seasonId = 42)), note = "正片榜")
        var rankingData = RankingListDto(list = listOf(PopularVideoDto(aid = 42)), note = "榜单说明")
        var weeks = WeeklyIssuesDto(list = listOf(
            WeeklyIssueDto(number = 3, name = "第3期", subject = "主题三", status = 2),
            WeeklyIssueDto(number = 1, status = 2), WeeklyIssueDto(number = 3, status = 2),
            WeeklyIssueDto(number = 0, status = 2), WeeklyIssueDto(number = 4, status = 1),
        ))
        var weeklyData = RankingListDto(list = listOf(PopularVideoDto(aid = 43)),
            config = WeeklyIssueDto(number = 3, name = "第三期", subject = "本周主题"), reminder = "每周精选")

        override suspend fun ranking(regionId: Int, type: String, webLocation: String): BiliResponse<RankingListDto> {
            requestedRegion = regionId
            assertEquals("all", type)
            assertEquals("333.934", webLocation)
            return BiliResponse(code = code, data = rankingData)
        }

        override suspend fun weeklyIssues(): BiliResponse<WeeklyIssuesDto> = BiliResponse(code = code, data = weeks)

        override suspend fun animationRanking(days: Int, seasonType: Int): PgcResponse<SeasonRankingDto> {
            seasonRequests += Triple(true, days, seasonType)
            return PgcResponse(code = code, result = seasonData)
        }

        override suspend fun cinemaRanking(days: Int, seasonType: Int): PgcResponse<SeasonRankingDto> {
            seasonRequests += Triple(false, days, seasonType)
            return PgcResponse(code = code, data = seasonData)
        }

        override suspend fun weekly(number: Int): BiliResponse<RankingListDto> {
            requestedWeek = number
            return BiliResponse(code = code, data = weeklyData)
        }

        override suspend fun precious(): BiliResponse<RankingListDto> = BiliResponse(code = code,
            data = RankingListDto(list = listOf(PopularVideoDto(aid = 44)), title = "入站必刷", explain = "精选说明"))

        override suspend fun popular(page: Int, pageSize: Int): BiliResponse<PopularPageDto> {
            requestedPage = page
            assertEquals(20, pageSize)
            return BiliResponse(code = code, data = PopularPageDto(
                list = listOf(
                    PopularVideoDto(
                        aid = 42, bvid = "BV1test", title = "video", pic = "cover",
                        duration = 99, owner = OwnerDto(mid = 7, name = "author"),
                        stat = VideoStatDto(view = 100, danmaku = 5),
                    ),
                    PopularVideoDto(aid = 0),
                ),
                noMore = page >= 2,
            ))
        }
    }

    @Test
    fun `popular videos can be opened and paginated`() = runBlocking {
        val api = FakeApi()
        val repository = RankingRepository(api)
        val first = repository.popular(1)
        assertEquals(1, api.requestedPage)
        assertEquals(1, first.videos.size)
        assertEquals("BV1test", first.videos.single().key)
        assertEquals("author", first.videos.single().authorName)
        assertEquals(100L, first.videos.single().viewCount)
        assertTrue(first.hasMore)
        assertFalse(repository.popular(2).hasMore)
        assertEquals(2, api.requestedPage)
    }

    @Test
    fun `popular json follows real response field names`() {
        val data = Json { ignoreUnknownKeys = true }.decodeFromString<BiliResponse<PopularPageDto>>(
            """{"code":0,"data":{"no_more":true,"list":[
            {"aid":123,"bvid":"BV1test","title":"title","pic":"cover","duration":13,
            "owner":{"mid":7,"name":"author"},"stat":{"view":100,"danmaku":5}}
            ]}}""",
        )
        assertTrue(data.data!!.noMore)
        assertEquals(123L, data.data.list!!.single().toVideoItem().aid)
        assertEquals(5L, data.data.list.single().toVideoItem().danmakuCount)
    }

    @Test
    fun `榜单固定分区参数去重隐藏失效稿件并保持列表顺序`() = runBlocking {
        val api = FakeApi().apply { rankingData = RankingListDto(list = listOf(
            PopularVideoDto(aid = 2, title = "<em>第一</em>", pic = "//i0.hdslb.com/a.jpg", cid = 8),
            PopularVideoDto(aid = 0), PopularVideoDto(aid = 2), PopularVideoDto(aid = 3, state = -4),
            PopularVideoDto(aid = 1),
        ), note = "动态更新") }
        val result = RankingRepository(api).ranking(RankingCategory.GAME)
        assertEquals(4, api.requestedRegion)
        assertEquals(listOf(2L, 1L), result.videos.map { it.aid })
        assertEquals("第一", result.videos.first().title)
        assertEquals("https://i0.hdslb.com/a.jpg", result.videos.first().cover)
        assertEquals(8L, result.videos.first().cid)
        assertEquals("动态更新", result.description)
        assertFalse(result.hasMore)
    }

    @Test
    fun `每周期数仅使用已发布正数去重倒序缺名称提供占位`() = runBlocking {
        val result = RankingRepository(FakeApi()).weeklyIssues()
        assertEquals(listOf(3, 1), result.map { it.number })
        assertEquals("主题三", result.first().subject)
        assertEquals("第1期", result.last().name)
    }

    @Test
    fun `每周详情与经典栏目使用独立接口且不假分页`() = runBlocking {
        val api = FakeApi()
        val repository = RankingRepository(api)
        val weekly = repository.weekly(3)
        assertEquals(3, api.requestedWeek)
        assertEquals("第三期", weekly.title)
        assertEquals("本周主题\n每周精选", weekly.description)
        assertEquals(43L, weekly.videos.single().aid)
        assertFalse(weekly.hasMore)
        val precious = repository.precious()
        assertEquals("入站必刷", precious.title)
        assertEquals("精选说明", precious.description)
        assertFalse(precious.hasMore)
    }

    @Test
    fun `不同推荐理由形状和经典成就不导致整页解析失败`() {
        val items = Json { ignoreUnknownKeys = true }.decodeFromString<RankingListDto>("""{"list":[
            {"aid":1,"rcmd_reason":{"content":"编辑推荐"}},
            {"aid":2,"rcmd_reason":"精选理由"}, {"aid":3,"rcmd_reason":null},
            {"aid":4,"achievement":"入站必刷成就","rcmd_reason":"不覆盖成就"},
            {"aid":5,"rcmd_reason":true}, {"aid":6,"rcmd_reason":[]}
        ]}""").list.orEmpty().map { it.toVideoItem() }
        assertEquals(listOf("编辑推荐", "精选理由", null, "入站必刷成就", null, null), items.map { it.recommendReason })
    }

    @Test
    fun `缺失视频列表或每周期数不被当作成功空列表`() {
        val api = FakeApi().apply { rankingData = RankingListDto(); weeks = WeeklyIssuesDto() }
        val repository = RankingRepository(api)
        assertThrows(BiliApiException::class.java) { runBlocking { repository.ranking(RankingCategory.ALL) } }
        assertThrows(BiliApiException::class.java) { runBlocking { repository.weeklyIssues() } }
        api.weeklyData = RankingListDto(list = emptyList(), config = WeeklyIssueDto(number = 99))
        assertThrows(BiliApiException::class.java) { runBlocking { repository.weekly(3) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.weekly(0) } }
    }

    @Test
    fun `所有新增栏目错误码必须传播而非显示暂无内容`() {
        val repository = RankingRepository(FakeApi().apply { code = -352 })
        assertThrows(BiliApiException::class.java) { runBlocking { repository.ranking(RankingCategory.ALL) } }
        assertThrows(BiliApiException::class.java) { runBlocking { repository.weeklyIssues() } }
        assertThrows(BiliApiException::class.java) { runBlocking { repository.weekly(3) } }
        assertThrows(BiliApiException::class.java) { runBlocking { repository.precious() } }
    }

    @Test
    fun `正片榜按season类型分流两套接口与三七日参数不混用投稿ID`() = runBlocking {
        val api = FakeApi()
        val repository = RankingRepository(api)
        SeasonRankingCategory.entries.forEach { category ->
            val result = repository.seasonRanking(category, SeasonRankingPeriod.THREE_DAYS)
            assertEquals(42L, result.seasons.single().seasonId)
            assertTrue(result.videos.isEmpty())
            assertFalse(result.hasMore)
            assertEquals("${category.label} · 近3日榜", result.title)
            assertEquals("正片榜", result.description)
        }
        repository.seasonRanking(SeasonRankingCategory.MOVIE, SeasonRankingPeriod.WEEK)
        assertEquals(listOf(Triple(true, 3, 1), Triple(false, 3, 4), Triple(false, 3, 2),
            Triple(false, 3, 5), Triple(false, 3, 3), Triple(false, 3, 7), Triple(false, 7, 2)), api.seasonRequests)
    }

    @Test
    fun `正片榜去重过滤非法seasonID保留平台排名且不使用epID`() = runBlocking {
        val api = FakeApi().apply { seasonData = SeasonRankingDto(list = listOf(
            SeasonRankingEntryDto(seasonId = 42, rank = 1), SeasonRankingEntryDto(seasonId = 0, rank = 2),
            SeasonRankingEntryDto(seasonId = 43, rank = 3), SeasonRankingEntryDto(seasonId = 42, rank = 4),
            SeasonRankingEntryDto(seasonId = 44),
        )) }
        val result = RankingRepository(api).seasonRanking(SeasonRankingCategory.ANIME, SeasonRankingPeriod.WEEK)
        assertEquals(listOf(42L, 43L, 44L), result.seasons.map { it.seasonId })
        assertEquals(listOf(1, 3, 5), result.seasons.map { it.rank })
    }

    @Test
    fun `正片榜支持result或data结构且忽略未知字段`() {
        val json = Json { ignoreUnknownKeys = true }
        val template = """{"code":0,"PAYLOAD":{"note":"说明","list":[{"season_id":42,"ep_id":99,
            "title":"番剧","rating":{"score":9.5},"new_ep":{"index_show":"更新至12集"},
            "stat":{"view":100,"follow":8},"badge":"会员"}]}}"""
        listOf("result", "data").forEach { field ->
            val result = json.decodeFromString<PgcResponse<SeasonRankingDto>>(template.replace("PAYLOAD", field)).requireResult()
            val item = result.list!!.single().toRankingItem(1)
            assertEquals(42L, item.seasonId)
            assertEquals(9.5, item.score!!, 0.0)
            assertEquals("更新至12集", item.progress)
            assertEquals("会员", item.badge)
            assertEquals(100L, item.viewCount)
        }
    }

    @Test
    fun `正片榜非零错误缺列表与零码风控不能当作成功空内容`() {
        val api = FakeApi()
        val repository = RankingRepository(api)
        api.code = -352
        assertThrows(BiliApiException::class.java) { runBlocking {
            repository.seasonRanking(SeasonRankingCategory.ANIME, SeasonRankingPeriod.THREE_DAYS)
        } }
        api.code = 0; api.seasonData = SeasonRankingDto()
        assertThrows(BiliApiException::class.java) { runBlocking {
            repository.seasonRanking(SeasonRankingCategory.MOVIE, SeasonRankingPeriod.WEEK)
        } }
        api.seasonData = SeasonRankingDto(voucher = "challenge")
        assertThrows(BiliRiskControlException::class.java) { runBlocking {
            repository.seasonRanking(SeasonRankingCategory.ANIME, SeasonRankingPeriod.WEEK)
        } }
        api.seasonData = SeasonRankingDto(list = emptyList())
        assertTrue(runBlocking { repository.seasonRanking(SeasonRankingCategory.MOVIE, SeasonRankingPeriod.WEEK) }.seasons.isEmpty())
    }

    @Test
    fun `api errors cannot masquerade as an empty ranking`() {
        val api = FakeApi().apply { code = -352 }
        assertThrows(BiliApiException::class.java) {
            runBlocking { RankingRepository(api).popular(1) }
        }
    }
}
