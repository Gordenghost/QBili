package com.qbili.data.remote.dto

import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.WatchLaterItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WatchLaterListDto(
    val count: Int = 0,
    val list: List<WatchLaterEntryDto> = emptyList(),
)

@Serializable
data class WatchLaterEntryDto(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val owner: OwnerDto? = null,
    val stat: VideoStatDto? = null,
    val progress: Int = 0,
    @SerialName("add_at") val addedAt: Long = 0,
) {
    fun toModel(): WatchLaterItem = WatchLaterItem(
        video = VideoItem(
            aid = aid,
            bvid = bvid,
            title = title,
            cover = pic,
            durationSeconds = duration,
            authorMid = owner?.mid ?: 0,
            authorName = owner?.name.orEmpty(),
            authorFace = owner?.face.orEmpty(),
            viewCount = stat?.view ?: 0,
            danmakuCount = stat?.danmaku ?: 0,
        ),
        progressSeconds = progress,
        addedAt = addedAt,
    )
}
