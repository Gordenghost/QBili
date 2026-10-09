package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.OpusActionApi
import com.qbili.data.remote.api.OpusFavoriteRequest
import com.qbili.data.remote.api.OpusLikeRequest
import com.qbili.data.remote.dto.BiliResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OpusInteractionRepositoryTest {
    private class FakeApi : OpusActionApi {
        val likes = mutableListOf<OpusLikeRequest>()
        val favorites = mutableListOf<OpusFavoriteRequest>()
        val tokens = mutableListOf<String>()

        override suspend fun like(request: OpusLikeRequest, referer: String): BiliResponse<JsonElement> {
            assertEquals("https://www.bilibili.com/opus/789", referer)
            likes += request
            return BiliResponse(code = 0)
        }

        override suspend fun favorite(csrf: String, request: OpusFavoriteRequest,
            referer: String): BiliResponse<JsonElement> {
            assertEquals("https://www.bilibili.com/opus/789", referer)
            tokens += csrf
            favorites += request
            return BiliResponse(code = 0)
        }
    }

    @Test
    fun `图文点赞收藏与取消传入正确状态和 csrf`() = runBlocking {
        val api = FakeApi()
        val repository = OpusInteractionRepository(api) { "test-csrf" }

        repository.setLike("789", true)
        repository.setLike("789", false)
        repository.setFavorite("789", true)
        repository.setFavorite("789", false)

        assertEquals(listOf(1, 2), api.likes.map { it.up })
        assertEquals(listOf(3, 4), api.favorites.map { it.action })
        assertEquals("789", api.likes.first().dynIdStr)
        assertEquals("test-csrf", api.likes.first().csrf)
        assertEquals(listOf("test-csrf", "test-csrf"), api.tokens)
        assertEquals("789", api.favorites.first().entity.objectIdStr)
        assertEquals(2, api.favorites.first().entity.type.biz)
        assertTrue(Json.encodeToString(api.favorites.first()).contains("object_id_str"))
    }

    @Test
    fun `没有 csrf 不发送互动请求`() {
        val api = FakeApi()
        val repository = OpusInteractionRepository(api) { null }
        assertThrows(BiliApiException::class.java) { runBlocking { repository.setLike("789", true) } }
        assertThrows(BiliApiException::class.java) { runBlocking { repository.setFavorite("789", true) } }
        assertTrue(api.likes.isEmpty())
        assertTrue(api.favorites.isEmpty())
    }
}
