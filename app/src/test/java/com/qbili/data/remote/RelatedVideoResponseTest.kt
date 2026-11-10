package com.qbili.data.remote

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.dto.RelatedVideoResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RelatedVideoResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `真实普通视频字段去重过滤当前视频并排除番剧跳转和不可见稿件`() {
        val response = json.decodeFromString<RelatedVideoResponse>("""{"code":0,"data":[
            {"aid":2,"bvid":"BV1xx411c7mD","title":"当前"},
            {"aid":3,"bvid":"BV1xx411c7mE","cid":30,"title":"<em>测试</em>&amp;视频","pic":"http://i0.hdslb.com/cover.jpg",
             "duration":60,"pubdate":100,"tname":"游戏","owner":{"mid":42,"name":"UP","face":"//i0.hdslb.com/face.jpg"},"stat":{"view":1000,"danmaku":10}},
            {"aid":3,"title":"重复AV"},{"aid":4,"bvid":"BV1xx411c7mE","title":"重复BV"},
            {"aid":5,"redirect_url":"https://www.bilibili.com/bangumi/play/ep1220770"},
            {"aid":6,"is_ogv":true},{"aid":7,"season_type":1},{"aid":8,"state":-4},
            {"aid":0},{"aid":9,"bvid":"javascript:bad","duration":-1,"stat":{"view":-5}}
        ]}""")
        val videos = response.videos(json, 2, "BV1xx411c7mD")
        assertEquals(listOf(3L, 9L), videos.map { it.aid })
        val video = videos.first()
        assertEquals("测试&视频", video.title)
        assertEquals("https://i0.hdslb.com/cover.jpg", video.cover)
        assertEquals("https://i0.hdslb.com/face.jpg", video.authorFace)
        assertEquals(42L, video.authorMid)
        assertEquals(30L, video.cid)
        assertEquals("游戏", video.channel)
        assertEquals(1000L, video.viewCount)
        assertEquals("av9", videos.last().key)
        assertEquals(0, videos.last().durationSeconds)
        assertEquals(0L, videos.last().viewCount)
    }

    @Test
    fun `空列表成功缺失或错误结构报错零码风控不当空列表`() {
        assertTrue(json.decodeFromString<RelatedVideoResponse>("""{"code":0,"data":[]}""")
            .videos(json, 2, "BV1xx411c7mD").isEmpty())
        listOf("""{"code":0}""", """{"code":0,"data":null}""", """{"code":0,"data":{}}""").forEach { raw ->
            assertThrows(IllegalStateException::class.java) { json.decodeFromString<RelatedVideoResponse>(raw).videos(json, 2, "") }
        }
        listOf("""{"code":0,"v_voucher":"risk"}""", """{"code":0,"data":{"v_voucher":"risk"}}""",
            """{"code":0,"result":{"v_voucher":"risk"}}""").forEach { raw ->
            assertThrows(BiliRiskControlException::class.java) { json.decodeFromString<RelatedVideoResponse>(raw).videos(json, 2, "") }
        }
        assertEquals(-412, assertThrows(BiliApiException::class.java) {
            json.decodeFromString<RelatedVideoResponse>("""{"code":-412,"message":"请求被拦截"}""").videos(json, 2, "")
        }.code)
    }
}
