package com.qbili.data.remote.dto

import com.qbili.domain.model.VideoComment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommentListDto(
    val cursor: CommentCursorDto? = null,
    val replies: List<CommentEntryDto>? = null,
)

@Serializable
data class CommentCursorDto(
    @SerialName("all_count") val allCount: Int = 0,
    val next: Long = 0,
    @SerialName("is_end") val isEnd: Boolean = true,
)

@Serializable
data class CommentEntryDto(
    val rpid: Long = 0,
    val root: Long = 0,
    val ctime: Long = 0,
    val like: Long = 0,
    val rcount: Int = 0,
    val member: CommentMemberDto? = null,
    val content: CommentContentDto? = null,
    val replies: List<CommentEntryDto>? = null,
) {
    fun toModel(): VideoComment = VideoComment(
        id = rpid,
        rootId = root,
        authorMid = member?.mid?.toLongOrNull() ?: 0,
        authorName = member?.uname.orEmpty(),
        authorFace = member?.face.orEmpty(),
        message = content?.message.orEmpty(),
        emotes = content?.emote.orEmpty().mapValues { it.value.url },
        createdAt = ctime,
        likeCount = like,
        replyCount = rcount,
        previews = replies.orEmpty().filter { it.rpid > 0 }.map { it.toModel() },
    )
}

@Serializable
data class CommentMemberDto(
    val mid: String = "",
    val uname: String = "",
    val face: String = "",
)

@Serializable
data class CommentContentDto(
    val message: String = "",
    val emote: Map<String, CommentEmoteDto>? = null,
)

@Serializable
data class CommentEmoteDto(val url: String = "")

@Serializable
data class CommentRepliesDto(
    val page: CommentRepliesPageDto? = null,
    val replies: List<CommentEntryDto>? = null,
)

@Serializable
data class CommentRepliesPageDto(
    val count: Int = 0,
    val num: Int = 1,
    val size: Int = 20,
)

@Serializable
data class CommentPostDto(
    val rpid: Long = 0,
    @SerialName("success_toast") val successToast: String = "",
)
