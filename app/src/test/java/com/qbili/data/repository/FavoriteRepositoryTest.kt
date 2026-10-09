package com.qbili.data.repository

import com.qbili.data.remote.api.FavoriteApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.requireData
import com.qbili.data.remote.dto.FavFolderDto
import com.qbili.data.remote.dto.FavFolderListDto
import com.qbili.data.remote.dto.FavoriteMediaDto
import com.qbili.data.remote.dto.FavoriteVideoListDto
import com.qbili.data.remote.dto.OwnerDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteRepositoryTest {
    private class FakeApi : FavoriteApi {
        override suspend fun folders(mid: Long): BiliResponse<FavFolderListDto> {
            assertEquals(42L, mid)
            return BiliResponse(data = FavFolderListDto(
                list = listOf(FavFolderDto(id = 123456, fid = 1234, title = "我的收藏夹", mediaCount = 2)),
            ))
        }

        override suspend fun videos(
            folderId: Long, page: Int, pageSize: Int, platform: String,
        ): BiliResponse<FavoriteVideoListDto> {
            assertEquals(123456L, folderId)
            assertEquals(20, pageSize)
            assertEquals("web", platform)
            return BiliResponse(data = FavoriteVideoListDto(
                medias = if (page == 1) listOf(
                    FavoriteMediaDto(id = 9, type = 2, bvId = "BV1example", title = "视频", upper = OwnerDto(mid = 42, name = "UP主")),
                    FavoriteMediaDto(id = 10, type = 12, title = "音频"),
                ) else emptyList(),
                hasMore = page == 1,
            ))
        }
    }

    @Test
    fun `收藏夹使用完整 ID 视频过滤非稿件条目`() = runBlocking {
        val repository = FavoriteRepository(FakeApi())
        val folder = repository.folders(42).first()
        assertEquals(123456L, folder.id)
        assertEquals(2, folder.mediaCount)
        val first = repository.videos(folder.id, 1)
        assertTrue(first.hasMore)
        assertEquals(1, first.videos.size)
        assertEquals("BV1example", first.videos.first().key)
        assertEquals("UP主", first.videos.first().authorName)
        assertFalse(repository.videos(folder.id, 2).hasMore)
    }

    @Test
    fun `收藏列表解析 bvid 和 has_more`() {
        val result = Json { ignoreUnknownKeys = true }.decodeFromString<BiliResponse<FavoriteVideoListDto>>(
            """{"code":0,"data":{"has_more":true,"medias":[
            {"id":1,"type":2,"title":"测试","bv_id":"BV1test","cnt_info":{"play":10,"danmaku":3}}
            ]}}""",
        )
        assertTrue(result.data!!.hasMore)
        assertEquals("BV1test", result.data.medias!!.first().toVideo().bvid)
        assertEquals(3L, result.data.medias.first().toVideo().danmakuCount)
    }

    @Test
    fun `empty favorite folders decode safely`() {
        val data = Json.decodeFromString<BiliResponse<FavFolderListDto>>(
            """{"code":0,"data":{"count":0,"list":null}}""",
        )
        assertEquals(emptyList<FavFolderDto>(), data.requireData().list.orEmpty())
    }
}
