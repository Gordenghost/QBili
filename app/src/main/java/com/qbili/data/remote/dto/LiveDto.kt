package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveRoomDto(
    @SerialName("room_info") val room: LiveRoomDetailsDto,
    @SerialName("anchor_info") val anchor: LiveAnchorDto? = null,
)

@Serializable
data class LiveRoomDetailsDto(
    @SerialName("room_id") val roomId: Long,
    val title: String = "",
    val cover: String = "",
    @SerialName("live_status") val liveStatus: Int = 0,
    @SerialName("area_name") val areaName: String = "",
    val online: Long = 0,
    val uid: Long = 0,
)

@Serializable
data class LiveAnchorDto(@SerialName("base_info") val base: LiveAnchorDetailsDto? = null)

@Serializable
data class LiveAnchorDetailsDto(val uname: String = "", val face: String = "")

@Serializable
data class LivePlayDto(
    @SerialName("playurl_info") val info: LivePlayInfoDto? = null,
)

@Serializable
data class LivePlayInfoDto(val playurl: LivePlayUrlsDto? = null)

@Serializable
data class LivePlayUrlsDto(val stream: List<LiveStreamDto> = emptyList())

@Serializable
data class LiveStreamDto(
    @SerialName("protocol_name") val protocol: String = "",
    val format: List<LiveFormatDto> = emptyList(),
)

@Serializable
data class LiveFormatDto(
    @SerialName("format_name") val name: String = "",
    val codec: List<LiveCodecDto> = emptyList(),
)

@Serializable
data class LiveCodecDto(
    @SerialName("codec_name") val name: String = "",
    @SerialName("current_qn") val quality: Int = 0,
    @SerialName("base_url") val path: String = "",
    @SerialName("url_info") val urls: List<LiveUrlDto> = emptyList(),
)

@Serializable
data class LiveUrlDto(val host: String = "", val extra: String = "")

@Serializable
data class LiveChatConfigDto(
    val token: String = "",
    @SerialName("host_server_list") val hosts: List<LiveChatHostDto> = emptyList(),
)

@Serializable
data class LiveChatHostDto(val host: String, @SerialName("wss_port") val port: Int = 443)
