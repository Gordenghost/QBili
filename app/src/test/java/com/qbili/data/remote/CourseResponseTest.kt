package com.qbili.data.remote

import com.qbili.core.BiliApiException
import com.qbili.data.remote.dto.CourseResponse
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.player.selectStream
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CourseResponseTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun response(raw: String): CourseResponse = json.decodeFromString(raw)
    private val episode = CourseEpisode(790, 123, 1, "试看", "", 76, "全集试看", "平台试看提示", true, true)

    @Test
    fun `catalogue keeps platform order and seconds deduplicates ids and does not guess free access`() {
        val detail = response("""{"code":0,"data":{"season_id":61,"title":"<b>课程</b>","cover":"//i0.hdslb.com/c.jpg",
            "up_info":{"mid":42,"uname":"讲师","avatar":"//i0.hdslb.com/a.jpg"},"user_status":{"payed":0},
            "payment":{"price_format":"398","price_unit":"B币"},"episode_page":{"next":true,"total":6},
            "brief":{"content":"文字简介","img":[{"url":"//i0.hdslb.com/1.jpg"},{"url":"https://evil.com/2.jpg"}]},
            "episodes":[{"id":3,"cid":123,"duration":76,"from":"pugv","play_way":1,"playable":true,"episode_can_view":true,"title":"第一课"},
                {"id":3,"title":"重复"},{"id":0},{"id":4,"cid":124,"playable":false,"from":"pugv","play_way":1},
                {"id":5,"cid":125,"playable":true,"from":"pugv","play_way":3},{"id":6,"cid":0,"playable":true,"ep_status":-1,"from":"pugv","play_way":1}]}}""").detail()
        assertEquals(listOf(3L, 4L, 5L, 6L), detail.episodes.map { it.id })
        assertEquals(76, detail.episodes.first().durationSeconds)
        assertTrue(detail.episodes.first().playable)
        assertFalse(detail.episodes[1].playable)
        assertFalse(detail.episodes[2].video)
        assertFalse(detail.episodes[3].playable)
        assertEquals("课程", detail.title)
        assertEquals("文字简介", detail.description)
        assertEquals(listOf("https://i0.hdslb.com/1.jpg"), detail.images)
        assertEquals("398 B币", detail.price)
        assertTrue(detail.access.contains("未购买"))
        assertTrue(detail.catalogueIncomplete)
    }

    @Test
    fun `data result genuine empty and unknown permission supported`() {
        val detail = response("""{"code":0,"result":{"season_id":61,"episodes":[]}}""").detail()
        assertTrue(detail.episodes.isEmpty())
        assertTrue(detail.access.contains("以平台为准"))
    }

    @Test
    fun `risk missing list invalid id and nested failures are not empty success`() {
        listOf("""{"code":-403,"message":"权限不足"}""",
            """{"code":0,"result":{"v_voucher":"risk"}}""",
            """{"code":0,"v_voucher":"risk","data":{"season_id":61,"episodes":[]}}""",
            """{"code":0,"data":{"code":-403,"message":"权限不足"}}""",
            """{"code":0,"data":{"season_id":61}}""",
            """{"code":0,"data":{"season_id":0,"episodes":[]}}""",
            """{"code":0,"data":[]}""").forEach {
            try { response(it).detail(); fail(it) } catch (_: BiliApiException) { }
        }
    }

    @Test
    fun `platform durl preview parsed without treating full trial as purchase`() {
        val playback = response("""{"code":0,"data":{"quality":32,"is_preview":true,"timelength":76000,
            "durl":[{"order":1,"length":76000,"url":"https://cdn.example/video.mp4"}]}}""").playback(json, episode)
        assertTrue(playback.playback.preview)
        assertTrue(playback.previewHint.contains("试看"))
        assertEquals("https://cdn.example/video.mp4", playback.playback.selectStream(80)?.segments?.single()?.url)
        val fullTrial = response("""{"code":0,"data":{"quality":32,"is_preview":0,
            "durl":[{"order":1,"url":"https://cdn.example/video.mp4"}]}}""").playback(json, episode)
        assertFalse(fullTrial.playback.preview)
        assertEquals("平台试看提示", fullTrial.previewHint)
    }

    @Test
    fun `missing streams drm and risk playurl are rejected`() {
        listOf("""{"code":0,"data":{"quality":80}}""",
            """{"code":0,"data":{"is_drm":true}}""",
            """{"code":0,"data":{"v_voucher":"risk"}}""").forEach {
            try { response(it).playback(json, episode); fail(it) } catch (_: BiliApiException) { }
        }
    }
}
