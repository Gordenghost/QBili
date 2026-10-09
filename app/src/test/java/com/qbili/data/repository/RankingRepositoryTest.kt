package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.RankingApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.OwnerDto
import com.qbili.data.remote.dto.PopularPageDto
import com.qbili.data.remote.dto.PopularVideoDto
import com.qbili.data.remote.dto.VideoStatDto
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
    fun `api errors cannot masquerade as an empty ranking`() {
        val api = FakeApi().apply { code = -352 }
        assertThrows(BiliApiException::class.java) {
            runBlocking { RankingRepository(api).popular(1) }
        }
    }
}
