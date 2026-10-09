package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.LiveApi
import com.qbili.data.remote.dto.LivePlayDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.LiveChatConnection
import com.qbili.domain.model.LiveRoom

class LiveRepository(private val api: LiveApi) {
    suspend fun room(roomId: Long): LiveRoom {
        require(roomId > 0) { "无效的直播间号" }
        val data = api.room(roomId).requireData()
        val room = data.room
        val stream = if (room.liveStatus == 1) api.play(
            roomId = room.roomId, protocol = "0,1", format = "0,1,2", codec = "0,1",
            quality = 10000, platform = "web", playerType = 8,
        ).requireData().selectHls() else null
        return LiveRoom(
            roomId = room.roomId, title = room.title, cover = room.cover,
            anchor = data.anchor?.base?.uname.orEmpty(),
            anchorFace = data.anchor?.base?.face.orEmpty(), anchorMid = room.uid,
            area = room.areaName, online = room.online, isLive = room.liveStatus == 1,
            streamUrl = stream?.first, quality = stream?.second ?: 0,
        )
    }

    suspend fun chat(roomId: Long): LiveChatConnection {
        val config = api.chat(roomId).requireData()
        val host = config.hosts.firstOrNull { it.host.isNotBlank() }
            ?: throw BiliApiException(-1, "未返回直播弹幕服务器")
        return LiveChatConnection(roomId, "wss://${host.host}:${host.port}/sub", config.token)
    }
}

internal fun LivePlayDto.selectHls(): Pair<String, Int>? {
    val streams = info?.playurl?.stream.orEmpty().filter { it.protocol == "http_hls" }
    for (format in listOf("ts", "fmp4")) {
        for (stream in streams) {
            val codecs = stream.format.firstOrNull { it.name == format }?.codec.orEmpty()
            for (codec in codecs.sortedBy { if (it.name == "avc") 0 else 1 }) {
                val url = codec.urls.firstOrNull { it.host.startsWith("https://") }
                if (url != null && codec.path.isNotBlank()) {
                    return "${url.host}${codec.path}${url.extra}" to codec.quality
                }
            }
        }
    }
    return null
}
