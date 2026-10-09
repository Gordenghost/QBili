package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.MessageApi
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.MessageHistoryDto
import com.qbili.data.remote.dto.MessageSessionsDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {
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
