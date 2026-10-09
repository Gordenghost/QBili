package com.qbili.data.remote.dto

import com.qbili.domain.model.VideoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PopularPageDto(
    val list: List<PopularVideoDto>? = null,
    @SerialName("no_more") val noMore: Boolean = false,
)

@Serializable
data class PopularVideoDto(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: OwnerDto? = null,
    val stat: VideoStatDto? = null,
) {
    fun toVideoItem(): VideoItem = VideoItem(
        aid = aid,
        bvid = bvid,
        title = title,
        cover = pic,
        durationSeconds = duration,
        pubDate = pubdate,
        authorMid = owner?.mid ?: 0,
        authorName = owner?.name.orEmpty(),
        authorFace = owner?.face.orEmpty(),
        viewCount = stat?.view ?: 0,
        danmakuCount = stat?.danmaku ?: 0,
    )
}
