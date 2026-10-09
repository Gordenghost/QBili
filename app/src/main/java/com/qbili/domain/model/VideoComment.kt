package com.qbili.domain.model

data class VideoComment(
    val id: Long,
    val rootId: Long,
    val authorMid: Long,
    val authorName: String,
    val authorFace: String,
    val message: String,
    val emotes: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val likeCount: Long,
    val replyCount: Int,
    val previews: List<VideoComment> = emptyList(),
) {
    val replyRootId: Long get() = rootId.takeIf { it > 0 } ?: id
}

data class CommentPage(
    val comments: List<VideoComment>,
    val next: Long?,
    val total: Int,
)
