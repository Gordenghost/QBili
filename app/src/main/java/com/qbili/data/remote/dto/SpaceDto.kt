package com.qbili.data.remote.dto

import com.qbili.core.parseDurationText
import com.qbili.domain.model.VideoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class SpaceCardResultDto(
    val card: SpaceCardDto? = null,
    val follower: Long = 0,
    @SerialName("archive_count") val archiveCount: Int = 0,
    val following: Boolean = false,
)

@Serializable
data class SpaceCardDto(
    val name: String = "",
    val face: String = "",
    val sign: String = "",
    @SerialName("level_info") val level: SpaceLevelDto? = null,
)

@Serializable
data class SpaceLevelDto(@SerialName("current_level") val value: Int = 0)

@Serializable
data class SpaceUploadsDto(
    val list: SpaceUploadListDto? = null,
    val page: SpacePageDto? = null,
    @SerialName("is_risk") val isRisk: Boolean = false,
)

@Serializable
data class SpaceUploadListDto(val vlist: List<SpaceVideoDto>? = null)

@Serializable
data class SpacePageDto(val count: Int = 0)

@Serializable
data class SpaceVideoDto(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val length: String = "",
    val play: JsonElement? = null,
    val created: Long = 0,
    val author: String = "",
) {
    fun toVideo(): VideoItem = VideoItem(
        aid = aid,
        bvid = bvid,
        title = title,
        cover = pic,
        durationSeconds = parseDurationText(length),
        viewCount = play?.jsonPrimitive?.longOrNull ?: 0,
        pubDate = created,
        authorName = author,
    )
}
