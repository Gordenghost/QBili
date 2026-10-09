package com.qbili.data.repository

import com.qbili.data.remote.api.LiveApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.LiveChatConfigDto
import com.qbili.data.remote.dto.LivePlayDto
import com.qbili.data.remote.dto.LiveRoomDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    private class FakeApi : LiveApi {
        lateinit var roomResponse: BiliResponse<LiveRoomDto>
        lateinit var playResponse: BiliResponse<LivePlayDto>
        var playCalls = 0

        override suspend fun room(roomId: Long): BiliResponse<LiveRoomDto> {
            assertEquals(6L, roomId)
            return roomResponse
        }

        override suspend fun play(
            roomId: Long, protocol: String, format: String, codec: String,
            quality: Int, platform: String, playerType: Int,
        ): BiliResponse<LivePlayDto> {
            assertEquals(7734200L, roomId)
            assertEquals("0,1", protocol)
            assertEquals("0,1,2", format)
            assertEquals("0,1", codec)
            assertEquals(10000, quality)
            assertEquals("web", platform)
            assertEquals(8, playerType)
            playCalls++
            return playResponse
        }

        override suspend fun chat(roomId: Long): BiliResponse<LiveChatConfigDto> =
            BiliResponse(data = LiveChatConfigDto(token = "token"))
    }

    @Test
    fun `short room ID resolves to real room and selects AVC HLS`() = runBlocking {
        val api = FakeApi().apply {
            roomResponse = json.decodeFromString("""{"code":0,"data":{
              "room_info":{"room_id":7734200,"uid":42,"title":"直播中","live_status":1,
                "cover":"//cover.jpg","area_name":"游戏","online":100},
              "anchor_info":{"base_info":{"uname":"主播","face":"//face.jpg"}}}}""")
            playResponse = json.decodeFromString("""{"code":0,"data":{"playurl_info":{"playurl":{
              "stream":[{"protocol_name":"http_stream","format":[]},
              {"protocol_name":"http_hls","format":[{"format_name":"ts","codec":[
                {"codec_name":"hevc","current_qn":400,"base_url":"/hevc.m3u8?","url_info":[{"host":"https://cdn","extra":"q=400"}]},
                {"codec_name":"avc","current_qn":250,"base_url":"/avc.m3u8?","url_info":[{"host":"https://cdn","extra":"qn=250&sign=a%2Bb"}]}]}]}]}}}}""")
        }
        val room = LiveRepository(api).room(6)
        assertEquals(7734200L, room.roomId)
        assertEquals("主播", room.anchor)
        assertEquals("https://cdn/avc.m3u8?qn=250&sign=a%2Bb", room.streamUrl)
        assertEquals(250, room.quality)
        assertEquals(1, api.playCalls)
    }

    @Test
    fun `offline room does not request stream`() = runBlocking {
        val api = FakeApi().apply { roomResponse = json.decodeFromString(
            """{"code":0,"data":{"room_info":{"room_id":7734200,"title":"休息中","live_status":0}}}""",
        ) }
        val room = LiveRepository(api).room(6)
        assertFalse(room.isLive)
        assertNull(room.streamUrl)
        assertEquals(0, api.playCalls)
    }

    @Test
    fun `play response without HLS does not offer FLV as HLS`() {
        val data = json.decodeFromString<LivePlayDto>(
            """{"playurl_info":{"playurl":{"stream":[{"protocol_name":"http_stream",
             "format":[{"format_name":"flv","codec":[{"base_url":"/live.flv?",
              "url_info":[{"host":"https://cdn","extra":"key=x"}]}]}]}]}}}""",
        )
        assertNull(data.selectHls())
    }
}
