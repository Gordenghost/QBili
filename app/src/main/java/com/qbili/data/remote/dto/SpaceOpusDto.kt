package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class SpaceOpusFeedDto(
    val items: List<SpaceOpusItemDto>? = null,
    val offset: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
    @SerialName("v_voucher") val voucher: String? = null,
    @SerialName("is_risk") val isRisk: Boolean = false,
)

@Serializable
data class SpaceOpusItemDto(
    @SerialName("opus_id") val opusId: String,
    val content: String = "",
    val cover: SpaceOpusCoverDto? = null,
    @SerialName("jump_url") val jumpUrl: String = "",
    val stat: SpaceOpusStatDto? = null,
)

@Serializable
data class SpaceOpusCoverDto(val url: String = "")

@Serializable
data class SpaceOpusStatDto(val like: JsonElement? = null)
