package com.qbili.data.remote.dto

import com.qbili.domain.model.VideoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FavoriteVideoListDto(
    val medias: List<FavoriteMediaDto>? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class FavoriteMediaDto(
    val id: Long = 0,
    val type: Int = 0,
    val bvid: String = "",
    @SerialName("bv_id") val bvId: String = "",
    val title: String = "",
    val cover: String = "",
    val duration: Int = 0,
    val upper: OwnerDto? = null,
    @SerialName("cnt_info") val counts: FavoriteMediaCountDto? = null,
) {
    fun toVideo(): VideoItem = VideoItem(
        aid = id,
        bvid = bvid.ifBlank { bvId },
        title = title,
        cover = cover,
        durationSeconds = duration,
        authorMid = upper?.mid ?: 0,
        authorName = upper?.name.orEmpty(),
        authorFace = upper?.face.orEmpty(),
        viewCount = counts?.play ?: 0,
        danmakuCount = counts?.danmaku ?: 0,
    )
}

@Serializable
data class FavoriteMediaCountDto(
    val play: Long = 0,
    val danmaku: Long = 0,
)
