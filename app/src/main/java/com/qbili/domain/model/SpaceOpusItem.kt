package com.qbili.domain.model

data class SpaceOpusItem(
    val id: String,
    val content: String,
    val cover: String,
    val likeCount: Long,
    val articleId: Long? = null,
)
