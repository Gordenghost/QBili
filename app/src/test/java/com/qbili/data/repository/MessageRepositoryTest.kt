package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.MessageApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.MessageHistoryDto
import com.qbili.data.remote.dto.MessageSessionsDto
import com.qbili.domain.model.MessageContact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {
    @Test
    fun `会话摘要和聊天使用相同卡片解析撤回后不能跳转`() = runBlocking {
        val content = """{"source":5,"id":42,"title":"分享视频"}"""
        val api = FakeApi().apply {
            val card = com.qbili.data.remote.dto.MessageEntryDto(key = 1, seqNo = 1, type = 7, content = content)
            sessionResponse = BiliResponse(data = MessageSessionsDto(sessions = listOf(
                com.qbili.data.remote.dto.MessageSessionDto(talkerId = 42, lastMessage = card),
            )))
            historyResponse = BiliResponse(data = MessageHistoryDto(messages = listOf(
                card, card.copy(key = 2, seqNo = 2, status = 1),
                card.copy(key = 3, seqNo = 3, type = 18, content = """{"content":"[{\"text\":\"系统提示\"}]"}"""),
            )))
        }
        val repository = MessageRepository(api)
        val preview = repository.sessions().sessions.single().preview
        val messages = repository.history(42).messages
        assertEquals("[视频] 分享视频", preview)
        assertEquals(preview, messages.first().text)
        assertEquals(com.qbili.domain.model.MessageOpenTarget.Video("av42"), messages.first().card?.links?.single()?.target)
        assertEquals(5, messages[1].type)
        assertNull(messages[1].card)
        assertTrue(messages[2].systemNotice)
        assertEquals("系统提示", messages[2].text)
    }

    @Test
    fun `缺少账号资料时补查昵称头像且不改变会话分页游标`() = runBlocking {
        val api = FakeApi().apply { sessionResponse = Json.decodeFromString("""{"data":{"has_more":1,"session_list":[
            {"talker_id":42,"session_ts":300},{"talker_id":42,"session_ts":250},
            {"talker_id":18,"session_type":2,"session_ts":200}]}}""") }
        val lookedUp = mutableListOf<Long>()
        val result = MessageRepository(api) { mid -> lookedUp += mid; MessageContact("真实昵称", "//avatar.jpg") }.sessions(500)
        assertEquals(listOf(42L), lookedUp)
        assertEquals("真实昵称", result.sessions.single().name)
        assertEquals("//avatar.jpg", result.sessions.single().face)
        assertFalse(result.sessions.single().profileUnavailable)
        assertEquals(200L, result.next)
    }

    @Test
    fun `接口已有昵称头像不重复查询兼容uname与face`() = runBlocking {
        val api = FakeApi().apply { sessionResponse = Json.decodeFromString("""{"data":{"session_list":[
            {"talker_id":42,"account_info":{"uname":"接口昵称","face":"//avatar.jpg"}}]}}""") }
        var calls = 0
        val result = MessageRepository(api) { calls++; null }.sessions()
        assertEquals(0, calls)
        assertEquals("接口昵称", result.sessions.single().name)
        assertEquals("//avatar.jpg", result.sessions.single().face)
    }

    @Test
    fun `补查失败只降级对应用户不吞取消且不覆盖已有昵称`() = runBlocking<Unit> {
        val api = FakeApi().apply { sessionResponse = Json.decodeFromString("""{"data":{"session_list":[
            {"talker_id":42,"account_info":{"name":"已有昵称"}}, {"talker_id":43}]}}""") }
        val result = MessageRepository(api) { error("资料失败") }.sessions()
        assertEquals(listOf("已有昵称", "用户43"), result.sessions.map { it.name })
        assertFalse(result.sessions.first().profileUnavailable)
        assertTrue(result.sessions.last().profileUnavailable)
        assertThrows(CancellationException::class.java) {
            runBlocking { MessageRepository(api) { throw CancellationException("取消") }.sessions() }
        }
    }

    @Test
    fun `图片和表情保留可信图床URL非法或缺失载荷明确占位`() = runBlocking {
        val api = FakeApi().apply { historyResponse = Json.decodeFromString("""{"data":{"messages":[
            {"msg_key":1,"msg_seqno":1,"msg_type":2,"content":"{\"url\":\"//i0.hdslb.com/image.jpg\"}"},
            {"msg_key":2,"msg_seqno":2,"msg_type":6,"content":"{\"url\":\"https://i0.hdslb.com/doge.png\"}"},
            {"msg_key":3,"msg_seqno":3,"msg_type":2,"content":"{\"url\":\"https://evil.test/image.jpg\"}"},
            {"msg_key":4,"msg_seqno":4,"msg_type":2,"content":"not json"},
            {"msg_key":5,"msg_seqno":5,"msg_type":6,"content":"{\"url\":\"javascript:alert(1)\"}"}
        ]}}""") }
        val result = MessageRepository(api).history(42).messages
        assertEquals("https://i0.hdslb.com/image.jpg", result[0].imageUrl)
        assertEquals("https://i0.hdslb.com/doge.png", result[1].imageUrl)
        assertEquals(listOf("[图片]", "[表情]", "[图片]", "[图片]", "[表情]"), result.map { it.text })
        assertTrue(result.drop(2).all { it.imageUrl == null })
    }

    @Test
    fun `已撤回图片隐藏原图和原文不误展示内容`() = runBlocking {
        val api = FakeApi().apply { historyResponse = Json.decodeFromString("""{"data":{"messages":[
            {"msg_key":1,"msg_seqno":1,"msg_type":2,"msg_status":1,"content":"{\"url\":\"https://i0.hdslb.com/image.jpg\"}"},
            {"msg_key":2,"msg_seqno":2,"msg_type":5,"content":"{}"}]}}""") }
        val result = MessageRepository(api).history(42).messages
        assertTrue(result.all { it.imageUrl == null && it.type == 5 && it.text == "[已撤回的消息]" })
    }

    private class FakeApi : MessageApi {
        var sessionCursor: Long? = null
        var historyCursor: Long? = null
        var sessionResponse = BiliResponse<MessageSessionsDto>(data = MessageSessionsDto())
        var historyResponse = BiliResponse<MessageHistoryDto>(data = MessageHistoryDto())
        var sentContent = ""
        var sendCount = 0
        var sendResponse = BiliResponse<JsonElement>(data = Json.parseToJsonElement("{}"))

        override suspend fun sessions(
            sessionType: Int, groupFold: Int, unfollowFold: Int, sortRule: Int,
            size: Int, endTs: Long?, build: Int, app: String,
        ): BiliResponse<MessageSessionsDto> {
            assertEquals(1, sessionType)
            assertEquals(2, sortRule)
            assertEquals(20, size)
            assertEquals("web", app)
            sessionCursor = endTs
            return sessionResponse
        }

        override suspend fun history(
            talkerId: Long, sessionType: Int, size: Int, endSeqno: Long?,
            senderDeviceId: String, build: Int, app: String,
        ): BiliResponse<MessageHistoryDto> {
            assertEquals(42L, talkerId)
            assertEquals(1, sessionType)
            assertEquals(20, size)
            assertEquals("1", senderDeviceId)
            historyCursor = endSeqno
            return historyResponse
        }

        override suspend fun send(
            signedSender: Long, signedReceiver: Long, signedDevice: String,
            sender: Long, receiverType: Int, receiver: Long, messageType: Int,
            status: Int, content: String, newFaceVersion: Int, device: String,
            timestamp: Long, fromFirework: Int, build: Int, app: String,
        ): BiliResponse<JsonElement> {
            assertEquals(99L, signedSender)
            assertEquals(42L, signedReceiver)
            assertEquals(signedSender, sender)
            assertEquals(signedReceiver, receiver)
            assertEquals(signedDevice, device)
            assertTrue(device.isNotBlank())
            assertEquals(1, receiverType)
            assertEquals(1, messageType)
            assertEquals(0, status)
            assertTrue(timestamp > 0)
            assertEquals("web", app)
            sentContent = content
            sendCount++
            return sendResponse
        }
    }

    @Test
    fun `session cursor follows unfiltered list even when system sessions are hidden`() = runBlocking {
        val api = FakeApi()
        api.sessionResponse = Json { ignoreUnknownKeys = true }.decodeFromString(
            """{"code":0,"data":{"has_more":1,"session_list":[
            {"talker_id":42,"session_type":1,"session_ts":300,"unread_count":2,
            "account_info":{"name":"Alice","pic_url":"//i0.hdslb.com/a.jpg"},
            "last_msg":{"msg_key":10,"msg_seqno":5,"msg_type":1,"content":"{\"content\":\"hi\"}"}},
            {"talker_id":18,"session_type":2,"session_ts":200}]}}""",
        )
        val result = MessageRepository(api).sessions(500)
        assertEquals(500L, api.sessionCursor)
        assertEquals(200L, result.next)
        assertEquals(1, result.sessions.size)
        assertEquals("Alice", result.sessions.single().name)
        assertEquals("hi", result.sessions.single().preview)
        assertEquals(2, result.sessions.single().unreadCount)
    }

    @Test
    fun `history decodes text and hides unsupported payloads`() = runBlocking {
        val api = FakeApi()
        api.historyResponse = Json { ignoreUnknownKeys = true }.decodeFromString(
            """{"code":0,"data":{"min_seqno":7,"has_more":1,"messages":[
            {"msg_key":2,"msg_seqno":8,"sender_uid":42,"msg_type":2,"content":"{}"},
            {"msg_key":1,"msg_seqno":7,"sender_uid":99,"msg_type":1,
            "content":"{\"content\":\"Hello\"}"}]}}""",
        )
        val page = MessageRepository(api).history(42)
        assertEquals(7L, page.older)
        assertEquals(listOf("Hello", "[图片]"), page.messages.map { it.text })
        val older = MessageRepository(api).history(42, 7)
        assertEquals(7L, api.historyCursor)
        assertEquals(null, older.older)
    }

    @Test
    fun `text message is sent once with json content and matching signed fields`() = runBlocking {
        val api = FakeApi()
        val repository = MessageRepository(api)
        repository.send(99, 42, "  你好 \"B站\"  ")
        assertEquals(1, api.sendCount)
        assertEquals("""{"content":"你好 \"B站\""}""", api.sentContent)
        assertEquals("你好 \"B站\"", Json.decodeFromString<com.qbili.data.remote.dto.MessageTextDto>(api.sentContent).content)
        assertNotEquals("你好 \"B站\"", api.sentContent)
    }

    @Test
    fun `missing data or api errors never claim the message was delivered`() {
        val api = FakeApi()
        val repository = MessageRepository(api)
        api.sendResponse = BiliResponse(code = 0, data = null)
        assertThrows(BiliApiException::class.java) { runBlocking { repository.send(99, 42, "hello") } }
        assertFalse(api.sentContent.isEmpty())
    }
}
