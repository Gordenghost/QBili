package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ArticleViewDto(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val opus: JsonElement? = null,
    val author: ArticleAuthorDto? = null,
    val stats: ArticleStatsDto? = null,
    @SerialName("publish_time") val publishTime: Long = 0,
)

@Serializable
data class ArticleAuthorDto(
    val mid: Long = 0,
    val name: String = "",
    val face: String = "",
)

@Serializable
data class ArticleStatsDto(
    val view: Long = 0,
    val like: Long = 0,
    val favorite: Long = 0,
    val reply: Long = 0,
)

@Serializable
data class ArticleViewInfoDto(
    val like: Int = 0,
    val favorite: Boolean = false,
    val stats: ArticleStatsDto? = null,
)
