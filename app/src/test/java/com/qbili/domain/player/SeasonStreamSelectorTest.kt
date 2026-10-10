package com.qbili.domain.player

import com.qbili.domain.model.DashResult
import com.qbili.domain.model.DashStream
import com.qbili.domain.model.DurlResult
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonPlayback
import com.qbili.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonStreamSelectorTest {
    @Test
    fun `优先真实DASH轨道并匹配编码和音轨`() {
        val result = SeasonPlayback(PlayurlResult(dash = DashResult(
            video = listOf(DashStream(id = 32, url = "video-avc", codecid = 7), DashStream(id = 32, url = "video-hevc", codecid = 12)),
            audio = listOf(DashStream(id = 30280, url = "audio"))),
            durl = listOf(DurlResult(order = 1, length = 1000, size = 1, url = "fallback", backupUrls = emptyList()))),
            preview = false, quality = 32)
        val stream = result.selectStream(80, VideoCodec.HEVC)!!
        assertEquals(32, stream.quality)
        assertEquals("video-hevc", stream.dash!!.videoUrl)
        assertEquals("audio", stream.dash!!.audioUrl)
        assertTrue(stream.segments.isEmpty())
    }

    @Test
    fun `试看及旧格式完整保留所有有效分段并按顺序播放`() {
        fun segment(order: Int, url: String) = DurlResult(order, 1000, 1, url, emptyList())
        val result = SeasonPlayback(PlayurlResult(durl = listOf(segment(2, "second"), segment(1, "first"), segment(3, ""))), true, 32)
        val stream = result.selectStream(80)!!
        assertNull(stream.dash)
        assertEquals(listOf("first", "second"), stream.segments.map { it.url })
        assertEquals("480P", stream.label)
    }

    @Test
    fun `空DASH可回退durl而无任何地址不生成播放项`() {
        val result = SeasonPlayback(PlayurlResult(dash = DashResult(),
            durl = listOf(DurlResult(1, 1000, 1, "fallback", emptyList()))), false, 16)
        assertNotNull(result.selectStream(80))
        assertNull(SeasonPlayback(PlayurlResult(), false, 32).selectStream(80))
    }
}
