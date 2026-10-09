package com.qbili.data.repository

import com.qbili.data.remote.api.CommentApi
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.CommentPage
import com.qbili.domain.model.VideoComment

class CommentRepository(private val api: CommentApi) {
    suspend fun list(oid: Long, mode: Int, next: Long, type: Int = TYPE_VIDEO): CommentPage {
        val data = api.list(type, oid, mode, next, PAGE_SIZE).requireData()
        val comments = data.replies.orEmpty().filter { it.rpid > 0 }.map { it.toModel() }
        val cursor = data.cursor
        return CommentPage(
            comments = comments,
            next = cursor?.next?.takeIf { !cursor.isEnd && it != next && comments.isNotEmpty() },
            total = cursor?.allCount ?: 0,
        )
    }

    suspend fun post(oid: Long, text: String, replyTo: VideoComment?, type: Int = TYPE_VIDEO) {
        val message = text.trim()
        require(message.isNotEmpty() && message.length <= 1000) { "评论须为 1-1000 字" }
        api.post(
            type = type,
            oid = oid,
            message = message,
            platform = PLATFORM_WEB,
            root = replyTo?.replyRootId,
            parent = replyTo?.id,
        ).requireData()
    }

    suspend fun replies(oid: Long, rootId: Long, page: Int, type: Int = TYPE_VIDEO): ReplyPage {
        val data = api.replies(type, oid, rootId, page, PAGE_SIZE).requireData()
        val replies = data.replies.orEmpty().filter { it.rpid > 0 }.map { it.toModel() }
        val hasMore = data.page?.let { page * PAGE_SIZE < it.count && replies.isNotEmpty() } ?: false
        return ReplyPage(replies, hasMore)
    }

    data class ReplyPage(val replies: List<VideoComment>, val hasMore: Boolean)

    companion object {
        const val MODE_POPULAR = 3
        const val MODE_LATEST = 2
        const val TYPE_VIDEO = 1
        const val TYPE_ARTICLE = 12
        private const val PLATFORM_WEB = 1
        private const val PAGE_SIZE = 20
    }
}
