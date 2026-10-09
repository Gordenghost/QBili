package com.qbili.domain.model

data class ArticleDetail(
    val id: Long,
    val title: String,
    val authorName: String,
    val authorMid: Long,
    val contentHtml: String,
    val publishedAt: Long,
    val viewCount: Long,
    val authorFace: String = "",
)

data class ArticleReaction(
    val liked: Boolean = false,
    val favorited: Boolean = false,
    val likeCount: Long = 0,
    val favoriteCount: Long = 0,
    val commentCount: Long = 0,
) {
    fun withLike(value: Boolean): ArticleReaction = copy(
        liked = value,
        likeCount = (likeCount + if (value) 1 else -1).coerceAtLeast(0),
    )

    fun withFavorite(value: Boolean): ArticleReaction = copy(
        favorited = value,
        favoriteCount = (favoriteCount + if (value) 1 else -1).coerceAtLeast(0),
    )
}
