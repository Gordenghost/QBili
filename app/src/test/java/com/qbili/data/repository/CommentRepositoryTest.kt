package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.CommentApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.CommentContentDto
import com.qbili.data.remote.dto.CommentCursorDto
import com.qbili.data.remote.dto.CommentEntryDto
import com.qbili.data.remote.dto.CommentListDto
import com.qbili.data.remote.dto.CommentMemberDto
import com.qbili.data.remote.dto.CommentPostDto
import com.qbili.data.remote.dto.CommentRepliesDto
import com.qbili.data.remote.dto.CommentRepliesPageDto
import com.qbili.data.remote.dto.requireData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CommentRepositoryTest {
    private class FakeApi : CommentApi {
        var expectedType = CommentRepository.TYPE_VIDEO
        var requestedType = 0
        var requestedMode = 0
        var requestedNext = -1L
        var postedRoot: Long? = null
        var postedParent: Long? = null
        var postedMessage = ""
        var requestedRoot = 0L
        var requestedReplyPage = 0
        var response = BiliResponse(
            data = CommentListDto(
                cursor = CommentCursorDto(allCount = 25, next = 21, isEnd = false),
                replies = listOf(
                    CommentEntryDto(
                        rpid = 7, member = CommentMemberDto(mid = "42", uname = "UP主"),
                        content = CommentContentDto("你好"), rcount = 1,
                        replies = listOf(CommentEntryDto(rpid = 8, root = 7, content = CommentContentDto("回复"))),
                    ),
                ),
            ),
        )

        override suspend fun list(
            type: Int, oid: Long, mode: Int, next: Long, pageSize: Int,
        ): BiliResponse<CommentListDto> {
            requestedType = type
            requestedMode = mode
            requestedNext = next
            assertEquals(123L, oid)
            assertEquals(20, pageSize)
            return response
        }

        override suspend fun post(
            type: Int, oid: Long, message: String, platform: Int, root: Long?, parent: Long?,
        ): BiliResponse<CommentPostDto> {
            assertEquals(expectedType, type)
            assertEquals(123L, oid)
            assertEquals(1, platform)
            postedRoot = root
            postedParent = parent
            postedMessage = message
            return BiliResponse(data = CommentPostDto(rpid = 9))
        }

        override suspend fun replies(
            type: Int, oid: Long, root: Long, page: Int, pageSize: Int,
        ): BiliResponse<CommentRepliesDto> {
            assertEquals(expectedType, type)
            assertEquals(123L, oid)
            assertEquals(20, pageSize)
            requestedRoot = root
            requestedReplyPage = page
            return BiliResponse(data = CommentRepliesDto(
                page = CommentRepliesPageDto(count = 21, num = page),
                replies = listOf(CommentEntryDto(rpid = 10, root = root)),
            ))
        }
    }

    @Test
    fun `评论分页使用服务端游标和稿件类型`() = runBlocking {
        val api = FakeApi()
        val page = CommentRepository(api).list(123, CommentRepository.MODE_POPULAR, 0)
        assertEquals(1, api.requestedType)
        assertEquals(3, api.requestedMode)
        assertEquals(0L, api.requestedNext)
        assertEquals(21L, page.next)
        assertEquals(25, page.total)
        assertEquals(42L, page.comments.first().authorMid)
        assertEquals(7L, page.comments.first().previews.first().replyRootId)

        api.response = BiliResponse(data = CommentListDto(cursor = CommentCursorDto(isEnd = true)))
        assertNull(CommentRepository(api).list(123, CommentRepository.MODE_LATEST, 21).next)
    }

    @Test
    fun `一级评论不带 root parent 回复时携带原评论关系`() = runBlocking {
        val api = FakeApi()
        val repository = CommentRepository(api)
        val original = repository.list(123, 3, 0).comments.first()
        repository.post(123, " 一级评论 ", null)
        assertNull(api.postedRoot)
        assertNull(api.postedParent)
        assertEquals("一级评论", api.postedMessage)
        repository.post(123, "回复内容", original.previews.first())
        assertEquals(7L, api.postedRoot)
        assertEquals(8L, api.postedParent)
    }

    @Test
    fun `专栏评论的列表发送和楼中楼都使用专栏类型`() = runBlocking {
        val api = FakeApi().apply { expectedType = CommentRepository.TYPE_ARTICLE }
        val repository = CommentRepository(api)
        val type = CommentRepository.TYPE_ARTICLE

        val comment = repository.list(123, CommentRepository.MODE_POPULAR, 0, type).comments.first()
        repository.post(123, "专栏回复", comment, type)
        repository.replies(123, comment.id, 1, type)

        assertEquals(type, api.requestedType)
        assertEquals("专栏回复", api.postedMessage)
        assertEquals(comment.id, api.postedRoot)
        assertEquals(comment.id, api.postedParent)
    }

    @Test
    fun `code 为零但 data 缺失不作为评论成功`() {
        val api = FakeApi()
        api.response = BiliResponse(code = 0, data = null)
        assertThrows(BiliApiException::class.java) {
            runBlocking { CommentRepository(api).list(123, 3, 0) }
        }
    }

    @Test
    fun `评论接口的嵌套回复可解析`() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val data = json.decodeFromString<BiliResponse<CommentListDto>>(
            """{"code":0,"data":{"cursor":{"all_count":2,"next":2,"is_end":false},
            "replies":[{"rpid":12345,"member":{"mid":"987","uname":"用户"},
            "content":{"message":"第一条"},"replies":null,"like":3}]}}""",
        ).requireData()
        assertEquals(987L, data.replies!!.first().toModel().authorMid)
        assertEquals(3L, data.replies.first().toModel().likeCount)
    }

    @Test
    fun `根评论回复以页码查询并在末页停止`() = runBlocking {
        val api = FakeApi()
        val repository = CommentRepository(api)
        assertEquals(true, repository.replies(123, 7, 1).hasMore)
        assertEquals(7L, api.requestedRoot)
        assertEquals(1, api.requestedReplyPage)
        assertEquals(false, repository.replies(123, 7, 2).hasMore)
    }

    @Test
    fun `表情字典与回复内容一起解析`() {
        val json = Json { ignoreUnknownKeys = true }
        val entry = json.decodeFromString<CommentEntryDto>(
            """{"rpid":1,"content":{"message":"[doge]你好", "emote":{
            "[doge]":{"url":"https://i0.hdslb.com/doge.png","meta":{"size":1}}}}}""",
        )
        assertEquals("https://i0.hdslb.com/doge.png", entry.toModel().emotes["[doge]"])
    }

    @Test
    fun `null emoji dictionary preserves ordinary comment`() {
        val entry = Json.decodeFromString<CommentEntryDto>(
            """{"rpid":1,"content":{"message":"plain text","emote":null}}""",
        )
        assertEquals("plain text", entry.toModel().message)
        assertEquals(emptyMap<String, String>(), entry.toModel().emotes)
    }
}
