package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class AppRecommendDataDto(
    val items: List<AppFeedItemDto>? = null,
    @SerialName("v_voucher") val vVoucher: String? = null,
)

@Serializable
data class AppFeedItemDto(
    val idx: Long? = null,
    val param: JsonPrimitive? = null,
    val bvid: String? = null,
    val goto: String? = null,
    @SerialName("card_goto") val cardGoto: String? = null,
    @SerialName("can_play") val canPlay: Int = 0,
    val title: String? = null,
    val cover: String? = null,
    @SerialName("cover_left_text_1") val coverLeftText1: String? = null,
    @SerialName("cover_left_text_2") val coverLeftText2: String? = null,
    @SerialName("cover_right_text") val coverRightText: String? = null,
    val args: AppFeedArgsDto? = null,
    @SerialName("player_args") val playerArgs: AppFeedPlayerArgsDto? = null,
    @SerialName("rcmd_reason") val rcmdReason: JsonElement? = null,
    @SerialName("ad_info") val adInfo: JsonElement? = null,
)

@Serializable
data class AppFeedArgsDto(
    @SerialName("up_id") val upId: Long = 0,
    @SerialName("up_name") val upName: String? = null,
    val aid: Long = 0,
    val tname: String? = null,
)

@Serializable
data class AppFeedPlayerArgsDto(
    val aid: Long = 0,
    val cid: Long? = null,
    val duration: Int? = null,
)
