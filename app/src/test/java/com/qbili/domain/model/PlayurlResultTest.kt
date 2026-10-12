package com.qbili.domain.model

import com.qbili.data.remote.dto.PlayurlDataDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayurlResultTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `DASH蛇形地址和音轨不能丢失且时长单位保持毫秒`() {
        val dto = json.decodeFromString<PlayurlDataDto>("""{
            "timelength":1630418,"dash":{"duration":1630,"video":[
                {"id":32,"base_url":"https://cdn.example/video.m4s"}],
                "audio":[{"id":30280,"base_url":"https://cdn.example/audio.m4s"}]}
        }""")
        val result = PlayurlResult.fromDto(dto)
        assertEquals(1_630_418L, result.durationMillis)
        assertEquals("https://cdn.example/video.m4s", result.dash!!.video.single().url)
        assertEquals("https://cdn.example/audio.m4s", result.dash!!.audio.single().url)
    }

    @Test
    fun `蛇形HLS地址与驼峰DASH地址均兼容`() {
        val dto = json.decodeFromString<PlayurlDataDto>("""{
            "dash":{"video":[{"baseUrl":"https://cdn.example/dash.m4s"}]},
            "hls":{"video":[{"base_url":"https://cdn.example/video.m3u8"}],
                "audio":[{"base_url":"https://cdn.example/audio.m3u8"}]}
        }""")
        val result = PlayurlResult.fromDto(dto)
        assertEquals("https://cdn.example/dash.m4s", result.dash!!.video.single().url)
        assertEquals("https://cdn.example/video.m3u8", result.hls!!.video.single().url)
        assertEquals("https://cdn.example/audio.m3u8", result.hls!!.audio.single().url)
    }
}
