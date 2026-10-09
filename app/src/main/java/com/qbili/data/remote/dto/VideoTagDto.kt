package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoTagDto(
    @SerialName("tag_name") val tagName: String,
)
