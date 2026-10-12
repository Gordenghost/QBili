package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppTokenStatusDto(
    val mid: Long = 0,
    @SerialName("expires_in") val expiresIn: Long? = null,
)

@Serializable
data class AccountBlacklistDto(val list: List<BlacklistUserDto>? = null, val total: Int? = null)

@Serializable
data class BlacklistUserDto(val mid: Long)
