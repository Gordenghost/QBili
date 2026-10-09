package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.SpaceApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.SpaceCardResultDto
import com.qbili.data.remote.dto.SpaceUploadsDto
import com.qbili.data.remote.dto.SpaceOpusFeedDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.GET
import retrofit2.http.Headers

class SpaceRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `video uploads use signed WBI endpoint`() {
        val endpoint = SpaceApi::class.java.methods.first { it.name == "uploads" }
            .getAnnotation(GET::class.java)
        assertEquals("x/space/wbi/arc/search", endpoint?.value)
    }

    private class FakeApi : SpaceApi {
        var cardResponse = BiliResponse<SpaceCardResultDto>(data = SpaceCardResultDto())
        var uploadsResponse = BiliResponse<SpaceUploadsDto>(data = SpaceUploadsDto())
        var receivedPage = 0
        var opusResponse = BiliResponse(data = SpaceOpusFeedDto(items = emptyList()))
        var opusRequests = mutableListOf<Pair<Int, String?>>()

        override suspend fun opus(mid: Long, page: Int, offset: String?, type: String,
            webLocation: String): BiliResponse<SpaceOpusFeedDto> {
            assertEquals(42L, mid)
            assertEquals("all", type)
            assertEquals("333.1387", webLocation)
            opusRequests += page to offset
            return opusResponse
        }

        override suspend fun card(mid: Long): BiliResponse<SpaceCardResultDto> {
            assertEquals(42L, mid)
            return cardResponse
        }

        override suspend fun uploads(mid: Long, page: Int, pageSize: Int, order: String): BiliResponse<SpaceUploadsDto> {
            assertEquals(42L, mid)
            assertEquals(20, pageSize)
            assertEquals("pubdate", order)
            receivedPage = page
            return uploadsResponse
        }
    }

    @Test
    fun `profile and uploads map web response including text duration and play count`() = runBlocking {
        val api = FakeApi().apply {
            cardResponse = json.decodeFromString(
                """{"code":0,"data":{"following":true,"follower":1234,"archive_count":21,
                "card":{"mid":"42","name":"UP主","face":"//face.jpg","sign":"简介",
                "level_info":{"current_level":6}}}}""",
            )
            uploadsResponse = json.decodeFromString(
                """{"code":0,"data":{"is_risk":false,"page":{"count":21},
                "list":{"vlist":[{"aid":7,"bvid":"BV1test","title":"视频","pic":"//cover.jpg",
                "length":"1:23","play":"--","created":100,"author":"UP主"}]}}}""",
            )
        }
        val repo = SpaceRepository(api)
        val profile = repo.profile(42)
        assertTrue(profile.following)
        assertEquals(1234L, profile.followers)
        assertEquals(21, profile.videoCount)
        assertEquals("简介", profile.sign)
        val page = repo.uploads(42, 1)
        assertEquals(1, api.receivedPage)
        assertTrue(page.hasMore)
        assertEquals("BV1test", page.videos.single().key)
        assertEquals(83, page.videos.single().durationSeconds)
        assertEquals(0L, page.videos.single().viewCount)
    }

    @Test
    fun `图文使用专用接口及WBI而不是过滤第一页动态`() {
        val method = SpaceApi::class.java.methods.first { it.name == "opus" }
        assertEquals("x/polymer/web-dynamic/v1/opus/feed/space", method.getAnnotation(GET::class.java)?.value)
        assertTrue(method.getAnnotation(Headers::class.java)!!.value.contains("X-QBili-Wbi: 1"))
    }

    @Test
    fun `图文和专栏投稿均可分页并保留数字字符串点赞数`() = runBlocking {
        val api = FakeApi()
        api.opusResponse = json.decodeFromString("""{"code":0,"data":{"has_more":true,"offset":"200",
            "items":[
                {"opus_id":"201","content":"纯文字图文","stat":{"like":"12"},"jump_url":"//www.bilibili.com/opus/201"},
                {"opus_id":"200","content":"专栏","cover":{"url":"http://i0.hdslb.com/a.png"},"stat":{"like":3},
                    "jump_url":"//www.bilibili.com/read/cv42"}
            ]}}""")
        val repository = SpaceRepository(api)
        val first = repository.opus(42, 1, null)
        assertEquals("200", first.next)
        assertEquals(listOf("201", "200"), first.items.map { it.id })
        assertEquals(12L, first.items.first().likeCount)
        assertEquals("", first.items.first().cover)
        assertEquals(42L, first.items.last().articleId)
        assertEquals(3L, first.items.last().likeCount)
        api.opusResponse = json.decodeFromString("""{"code":0,"data":{"has_more":false,"offset":"199",
            "items":[{"opus_id":"199","content":"更早的图文"}]}}""")
        val second = repository.opus(42, 2, first.next)
        assertEquals(listOf(1 to null, 2 to "200"), api.opusRequests)
        assertEquals(null, second.next)
        assertEquals("199", second.items.single().id)
    }

    @Test
    fun `风控标记和缺失图文数据不能伪装成暂无图文`() {
        val api = FakeApi()
        listOf(
            SpaceOpusFeedDto(voucher = "risk-token"),
            SpaceOpusFeedDto(voucher = ""),
            SpaceOpusFeedDto(isRisk = true),
            SpaceOpusFeedDto(items = null),
        ).forEach { data ->
            api.opusResponse = BiliResponse(data = data)
            assertThrows(BiliApiException::class.java) { runBlocking { SpaceRepository(api).opus(42, 1, null) } }
        }
    }

    @Test
    fun `游标重复时结束图文分页防止反复请求`() = runBlocking {
        val api = FakeApi().apply {
            opusResponse = BiliResponse(data = SpaceOpusFeedDto(items = emptyList(), hasMore = true, offset = "200"))
        }
        assertEquals(null, SpaceRepository(api).opus(42, 2, "200").next)
    }

    @Test
    fun `risk flag never masquerades as empty uploads`() {
        val api = FakeApi().apply {
            uploadsResponse = BiliResponse(data = SpaceUploadsDto(isRisk = true))
        }
        assertThrows(BiliApiException::class.java) {
            runBlocking { SpaceRepository(api).uploads(42, 1) }
        }
        api.uploadsResponse = BiliResponse(data = SpaceUploadsDto())
        assertFalse(runBlocking { SpaceRepository(api).uploads(42, 1) }.hasMore)
    }
}
