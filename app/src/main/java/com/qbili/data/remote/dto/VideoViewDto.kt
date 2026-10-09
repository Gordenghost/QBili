package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/x/web-interface/view` 的返回。
 *
 * [cid] 是**首个分 P** 的 cid；多 P 视频要播其它分 P 得从 [pages] 里取。
 * playurl 和弹幕接口都按 cid 索引，不认 aid/bvid。
 */
@Serializable
data class VideoViewDto(
    val bvid: String = "",
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    val desc: String = "",
    val pic: String = "",
    /** 秒 */
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: OwnerDto? = null,
    val stat: VideoStatDto? = null,
    val pages: List<VideoPageDto> = emptyList(),
)

@Serializable
data class VideoPageDto(
    val cid: Long = 0,
    val page: Int = 1,
    /** 分 P 标题 */
    val part: String = "",
    /** 秒 */
    val duration: Int = 0,
    val dimension: VideoDimensionDto? = null,
)

@Serializable
data class VideoDimensionDto(
    val width: Int = 0,
    val height: Int = 0,
    /** 1 表示需要交换宽高（竖屏拍摄） */
    @SerialName("rotate") val rotate: Int = 0,
)
