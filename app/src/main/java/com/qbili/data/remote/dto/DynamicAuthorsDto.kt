package com.qbili.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class DynamicAuthorsDto(val list: List<DynamicAuthorDto> = emptyList(), val total: Int = 0)

@Serializable
data class DynamicAuthorDto(val mid: Long = 0, val uname: String = "", val face: String = "")
