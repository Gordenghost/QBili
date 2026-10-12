package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OwnerDto(
    val mid: Long = 0,
    val name: String? = null,
    val face: String? = null,
)

@Serializable
data class VideoStatDto(
    val aid: Long? = null,
    val view: Long = 0,
    val danmaku: Long = 0,
    val reply: Long = 0,
    val favorite: Long = 0,
    val coin: Long = 0,
    val share: Long = 0,
    val like: Long = 0,
    @SerialName("his_rank") val hisRank: Int? = null,
)

@Serializable
data class RecommendReasonDto(
    @SerialName("reason_type") val reasonType: Int? = null,
    val content: String? = null,
)

/** 首页推荐流单项 */
@Serializable
data class FeedItemDto(
    val id: Long = 0,
    val bvid: String? = null,
    val cid: Long? = null,
    /** av / bangumi / picture / live … 非 av 的条目当前版本直接过滤 */
    val goto: String? = null,
    val uri: String? = null,
    val pic: String? = null,
    val title: String? = null,
    val tname: String? = null,
    val duration: Int = 0,
    val pubdate: Long? = null,
    val owner: OwnerDto? = null,
    val stat: VideoStatDto? = null,
    @SerialName("rcmd_reason") val rcmdReason: RecommendReasonDto? = null,
)

@Serializable
data class RecommendDataDto(
    val item: List<FeedItemDto>? = null,
    val mid: Long? = null,
    @SerialName("v_voucher") val vVoucher: String? = null,
)
