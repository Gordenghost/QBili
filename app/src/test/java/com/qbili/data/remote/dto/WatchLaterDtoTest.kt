package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WatchLaterDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `列表字段映射并保留播放进度`() {
        val response = json.decodeFromString<BiliResponse<WatchLaterListDto>>(
            """{
                "code":0,
                "data":{"count":2,"list":[
                    {"aid":123,"bvid":"BV1example","title":"视频", "pic":"//i0.hdslb.com/a.jpg",
                     "duration":200,"progress":85,"add_at":1730000000,
                     "owner":{"mid":42,"name":"UP主"},"stat":{"view":1200,"danmaku":15},
                     "unused_field":true},
                    {"aid":456,"title":"已看完","duration":80,"progress":-1}
                ]}
            }""",
        )

        val first = response.requireData().list.first().toModel()
        assertEquals("BV1example", first.video.key)
        assertEquals(42L, first.video.authorMid)
        assertEquals("UP主", first.video.authorName)
        assertEquals(1200L, first.video.viewCount)
        assertEquals(15L, first.video.danmakuCount)
        assertEquals(1730000000L, first.addedAt)
        assertEquals("看到 01:25", first.progressLabel)

        val second = response.requireData().list.last().toModel()
        assertEquals("av456", second.video.key)
        assertEquals("已看完", second.progressLabel)
    }

    @Test
    fun `空列表与接口错误不混淆`() {
        val empty = json.decodeFromString<BiliResponse<WatchLaterListDto>>(
            """{"code":0,"data":{"count":0,"list":[]}}""",
        )
        assertEquals(emptyList<WatchLaterEntryDto>(), empty.requireData().list)

        val failure = json.decodeFromString<BiliResponse<WatchLaterListDto>>(
            """{"code":-101,"message":"未登录"}""",
        )
        assertEquals(-101, assertThrows(BiliApiException::class.java) { failure.requireData() }.code)
    }

    @Test
    fun `未观看与看完的进度边界`() {
        assertEquals("未观看", WatchLaterEntryDto(aid = 1).toModel().progressLabel)
        assertEquals(
            "已看完",
            WatchLaterEntryDto(aid = 2, duration = 60, progress = 60).toModel().progressLabel,
        )
    }
}
