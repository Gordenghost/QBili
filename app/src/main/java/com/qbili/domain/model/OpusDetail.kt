package com.qbili.domain.model

data class OpusDetail(
    val id: String,
    val title: String,
    val author: String,
    val blocks: List<OpusBlock>,
    val authorMid: Long = 0,
    val authorFace: String = "",
    val commentType: Int = 0,
    val commentOid: Long = 0,
    val commentCount: Long = 0,
    val likeCount: Long = 0,
    val favoriteCount: Long = 0,
    val liked: Boolean = false,
    val favorited: Boolean = false,
) {
    fun withLike(value: Boolean) = copy(
        liked = value,
        likeCount = (likeCount + if (value) 1 else -1).coerceAtLeast(0),
    )

    fun withFavorite(value: Boolean) = copy(
        favorited = value,
        favoriteCount = (favoriteCount + if (value) 1 else -1).coerceAtLeast(0),
    )
}

sealed interface OpusBlock {
    data class Paragraph(val text: String) : OpusBlock
    data class Picture(val url: String) : OpusBlock
}
