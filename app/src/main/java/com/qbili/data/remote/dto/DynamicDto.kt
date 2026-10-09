package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DynamicFeedDto(
    val items: List<JsonElement>? = null,
    val offset: String? = null,
    @SerialName("has_more") val hasMore: Boolean = false,
)
