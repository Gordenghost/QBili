package com.qbili.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class OpusDetailDto(val item: JsonElement? = null)
