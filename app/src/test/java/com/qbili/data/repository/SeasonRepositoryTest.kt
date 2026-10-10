package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.api.SeasonApi
import com.qbili.data.remote.dto.DashDto
import com.qbili.data.remote.dto.DashStreamDto
import com.qbili.data.remote.dto.DurlDto
import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonDetailDto
import com.qbili.data.remote.dto.SeasonEpisodeDto
import com.qbili.data.remote.dto.SeasonEpisodeRightsDto
import com.qbili.data.remote.dto.SeasonPlayurlDto
import com.qbili.data.remote.dto.SeasonSectionDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private class FakeApi : SeasonApi {
        var detailResponse = PgcResponse(result = SeasonDetailDto(seasonId = 3398))
        var playResponse = PgcResponse(result = SeasonPlayurlDto(
            durl = listOf(DurlDto(url = "https://cdn.example/video.mp4")), timelength = 90_000, quality = 32))
        val requests = mutableListOf<String>()
        var actionCode = 0
        override suspend fun detail(seasonId: Long): PgcResponse<SeasonDetailDto> {
            requests += "detail:$seasonId"
            return detailResponse
        }
        override suspend fun playurl(episodeId: Long, cid: Long, quality: Int, fnval: Int, fnver: Int, fourk: Int): PgcResponse<SeasonPlayurlDto> {
            requests += "play:$episodeId:$cid:$quality:$fnval:$fnver:$fourk"
            return playResponse
        }
        override suspend fun follow(seasonId: Long): PgcResponse<JsonElement> {
            requests += "follow:$seasonId"
            return PgcResponse(code = actionCode, message = "操作失败")
        }
        override suspend fun unfollow(seasonId: Long): PgcResponse<JsonElement> {
            requests += "unfollow:$seasonId"
            return PgcResponse(code = actionCode, message = "操作失败")
        }
    }

    private suspend fun episode(api: FakeApi = FakeApi()) = SeasonRepository(api.apply {
        detailResponse = PgcResponse(result = SeasonDetailDto(seasonId = 3398,
            episodes = listOf(SeasonEpisodeDto(id = 84776, aid = 4044639, cid = 11311248, duration = 90_000))))
    }).detail(3398).episodes.single()

    @Test
    fun `真实 result 结构保留标题封面简介剧集权限和更新状态`() = runBlocking {
        val dto = json.decodeFromString<PgcResponse<SeasonDetailDto>>("""{
            "code":0,"message":"success","result":{
                "season_id":3398,"title":"<em>冰菓</em>","type":1,
                "cover":"//i0.hdslb.com/cover.png","evaluate":"第一行\n第二行",
                "rating":{"score":9.8,"count":100},"new_ep":{"desc":"已完结, 全23话"},
                "areas":[{"id":2,"name":"日本"}],"styles":["推理","日常"],
                "user_status":{"follow":1,"area_limit":0},
                "episodes":[{"id":84776,"ep_id":84776,"aid":4044639,"cid":11311248,
                    "title":"1","long_title":"深具传统","show_title":"第1话 深具传统",
                    "cover":"http://i0.hdslb.com/ep.jpg","duration":1631000,"status":2,
                    "rights":{"area_limit":0}}],"rights":{"area_limit":328,"can_watch":1}
            }}""")
        val api = FakeApi().apply { detailResponse = dto }
        val detail = SeasonRepository(api).detail(3398)
        assertEquals("冰菓", detail.title)
        assertEquals("https://i0.hdslb.com/cover.png", detail.cover)
        assertEquals("第一行\n第二行", detail.description)
        assertEquals(9.8, detail.score, 0.01)
        assertTrue(detail.followed)
        assertFalse(detail.areaLimited)
        assertEquals("已完结, 全23话", detail.progress)
        assertEquals(listOf("日本"), detail.areas)
        assertEquals("第1话 深具传统", detail.episodes.single().title)
        assertEquals(1631, detail.episodes.single().durationSeconds)
    }

    @Test
    fun `正片和花絮合并去重而未开播和会员剧集仍显示`() = runBlocking {
        val first = SeasonEpisodeDto(id = 1, cid = 10, title = "1")
        val api = FakeApi().apply { detailResponse = PgcResponse(result = SeasonDetailDto(seasonId = 3398,
            episodes = listOf(first, first, SeasonEpisodeDto(id = 0), SeasonEpisodeDto(id = 2, hidden = true)),
            section = listOf(SeasonSectionDto(title = "花絮", episodes = listOf(first,
                SeasonEpisodeDto(episodeId = 3, cid = 30, status = 13, longTitle = "会员花絮"),
                SeasonEpisodeDto(id = 4, title = "未开播")))))) }
        val detail = SeasonRepository(api).detail(3398)
        assertEquals(listOf("正片", "花絮"), detail.sections.map { it.title })
        assertEquals(listOf(1L, 3L, 4L), detail.episodes.map { it.id })
        assertEquals("大会员", detail.episodes[1].accessHint)
        assertEquals(0L, detail.episodes.last().cid)
    }

    @Test
    fun `PGC 播放请求使用剧集ID且时长单位保持毫秒`() = runBlocking {
        val api = FakeApi()
        val result = SeasonRepository(api).playurl(episode(), 80)
        assertEquals(listOf("play:84776:11311248:80:4048:0:1"), api.requests)
        assertEquals(90_000L, result.playurl.durationMillis)
        assertEquals(90_000L, result.playurl.timelength)
        assertEquals(32, result.quality)
        assertFalse(result.preview)
    }

    @Test
    fun `未登录会员剧集的试看标记不能当作完整正片`() = runBlocking {
        val api = FakeApi().apply { playResponse = PgcResponse(result = SeasonPlayurlDto(
            preview = 1, durl = listOf(DurlDto(url = "https://cdn.example/preview.mp4")))) }
        assertTrue(SeasonRepository(api).playurl(episode().copy(status = 13), 80).preview)
    }

    @Test
    fun `DASH支持蛇形驼峰URL和音频轨道`() = runBlocking {
        val response = json.decodeFromString<PgcResponse<SeasonPlayurlDto>>("""{
            "code":0,"result":{"quality":32,"timelength":1630418,"is_preview":0,
                "dash":{"duration":1630,"minBufferTime":1.5,"video":[
                    {"id":32,"base_url":"https://cdn.example/video.m4s","codecid":7,"height":480}],
                    "audio":[{"id":30280,"baseUrl":"https://cdn.example/audio.m4s"}]}}
        }""")
        val api = FakeApi().apply { playResponse = response }
        val result = SeasonRepository(api).playurl(episode(), 80)
        assertEquals("https://cdn.example/video.m4s", result.playurl.dash!!.video.single().url)
        assertEquals("https://cdn.example/audio.m4s", result.playurl.dash!!.audio.single().url)
        assertEquals(1_630_418L, result.playurl.durationMillis)
    }

    @Test
    fun `地区限制和无CID不会请求错误的播放接口`() {
        val api = FakeApi()
        val episode = runBlocking { episode() }
        assertThrows(BiliApiException::class.java) { runBlocking { SeasonRepository(api).playurl(episode.copy(areaLimited = true), 80) } }
        assertThrows(BiliApiException::class.java) { runBlocking { SeasonRepository(api).playurl(episode.copy(cid = 0), 80) } }
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `接口鉴权错误空地址和空详情均不能当成功`() {
        val api = FakeApi().apply { playResponse = PgcResponse(code = -10403, message = "大会员专享") }
        val episode = runBlocking { episode() }
        assertThrows(BiliApiException::class.java) { runBlocking { SeasonRepository(api).playurl(episode, 80) } }
        api.playResponse = PgcResponse(result = SeasonPlayurlDto(dash = DashDto(video = listOf(DashStreamDto()))))
        assertThrows(BiliApiException::class.java) { runBlocking { SeasonRepository(api).playurl(episode, 80) } }
        api.detailResponse = PgcResponse()
        assertThrows(BiliApiException::class.java) { runBlocking { SeasonRepository(api).detail(3398) } }
        api.detailResponse = PgcResponse(result = SeasonDetailDto())
        assertThrows(BiliApiException::class.java) { runBlocking { SeasonRepository(api).detail(3398) } }
    }

    @Test
    fun `code0的Gaia风控响应不能显示空详情或没有播放地址`() {
        val detail = json.decodeFromString<PgcResponse<SeasonDetailDto>>("""{"code":0,"result":{"v_voucher":"challenge"}}""")
        val play = json.decodeFromString<PgcResponse<SeasonPlayurlDto>>("""{"code":0,"result":{"v_voucher":"challenge"}}""")
        assertThrows(BiliRiskControlException::class.java) { detail.requireResult() }
        assertThrows(BiliRiskControlException::class.java) { play.requireResult() }
    }

    @Test
    fun `追番取消追番分别调用写接口且失败不吞错`() = runBlocking {
        val api = FakeApi()
        val repository = SeasonRepository(api)
        repository.setFollowed(3398, true)
        repository.setFollowed(3398, false)
        assertEquals(listOf("follow:3398", "unfollow:3398"), api.requests)
        api.actionCode = -101
        assertThrows(BiliApiException::class.java) { runBlocking { repository.setFollowed(3398, true) } }
        Unit
    }

    @Test
    fun `接口data兼容而取消请求直接传播`() {
        val response = PgcResponse(data = SeasonDetailDto(seasonId = 3398))
        assertEquals(3398L, response.requireResult().seasonId)
        val api = object : SeasonApi by FakeApi() {
            override suspend fun detail(seasonId: Long): PgcResponse<SeasonDetailDto> = throw CancellationException("cancelled")
        }
        assertThrows(CancellationException::class.java) { runBlocking { SeasonRepository(api).detail(3398) } }
    }

    @Test
    fun `追番返回code0风控不能误报操作成功`() {
        val response = json.decodeFromString<PgcResponse<JsonElement>>("""{"code":0,"result":{"v_voucher":"challenge"}}""")
        val api = object : SeasonApi by FakeApi() {
            override suspend fun follow(seasonId: Long): PgcResponse<JsonElement> = response
        }
        assertThrows(BiliRiskControlException::class.java) { runBlocking { SeasonRepository(api).setFollowed(3398, true) } }
    }
}
