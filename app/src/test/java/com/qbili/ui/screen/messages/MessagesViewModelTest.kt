package com.qbili.ui.screen.messages

import com.qbili.data.remote.api.MessageApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.MessageEntryDto
import com.qbili.data.remote.dto.MessageHistoryDto
import com.qbili.data.remote.dto.MessageSessionsDto
import com.qbili.data.remote.dto.MessageSessionDto
import com.qbili.data.repository.MessageRepository
import com.qbili.domain.model.PrivateConversation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesViewModelTest {
    private class Api : MessageApi {
        var historyCall: suspend (Long, Long?) -> BiliResponse<MessageHistoryDto> = { _, _ -> BiliResponse(data = MessageHistoryDto()) }
        var sessionsCall: suspend () -> BiliResponse<MessageSessionsDto> = { BiliResponse(data = MessageSessionsDto()) }
        var sendCall: suspend () -> BiliResponse<JsonElement> = { BiliResponse(data = Json.parseToJsonElement("{}")) }
        override suspend fun sessions(sessionType: Int, groupFold: Int, unfollowFold: Int, sortRule: Int,
            size: Int, endTs: Long?, build: Int, app: String) = sessionsCall()
        override suspend fun history(talkerId: Long, sessionType: Int, size: Int, endSeqno: Long?,
            senderDeviceId: String, build: Int, app: String) = historyCall(talkerId, endSeqno)
        override suspend fun send(signedSender: Long, signedReceiver: Long, signedDevice: String, sender: Long,
            receiverType: Int, receiver: Long, messageType: Int, status: Int, content: String, newFaceVersion: Int,
            device: String, timestamp: Long, fromFirework: Int, build: Int, app: String) = sendCall()
    }
    private val login = LoginCookies("session", "csrf", 99)
    private fun conversation(mid: Long) = PrivateConversation(mid, "用户$mid", "", "", 0, 0, 0)
    private fun response(key: Long) = BiliResponse(data = MessageHistoryDto(listOf(MessageEntryDto(
        key = key, seqNo = key, senderMid = 42, type = 1, content = """{"content":"消息"}"""))))

    @Test
    fun `切换账号取消旧会话加载且旧响应不覆盖新账号`() = runBlocking {
        val delayed = CompletableDeferred<BiliResponse<MessageSessionsDto>>()
        val api = Api().apply { var calls = 0; sessionsCall = {
            calls++
            if (calls == 1) withContext(NonCancellable) { delayed.await() }
            else BiliResponse(data = MessageSessionsDto(listOf(MessageSessionDto(talkerId = 43))))
        } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(login)
            val model = MessagesViewModel(MessageRepository(api), sessions, scope)
            sessions.value = login.copy(mid = 100, sessData = "new")
            assertEquals(listOf(43L), model.uiState.value.sessions.map { it.talkerId })
            delayed.complete(BiliResponse(data = MessageSessionsDto(listOf(MessageSessionDto(talkerId = 42)))))
            assertEquals(listOf(43L), model.uiState.value.sessions.map { it.talkerId })
            assertFalse(model.uiState.value.sessionsLoading)
        } finally { scope.cancel() }
    }

    @Test
    fun `同一会话关闭重开后旧消息响应不会覆盖新消息`() = runBlocking {
        val delayed = CompletableDeferred<BiliResponse<MessageHistoryDto>>()
        val api = Api().apply { var calls = 0; historyCall = { _, _ ->
            calls++
            if (calls == 1) withContext(NonCancellable) { delayed.await() } else response(2)
        } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = MessagesViewModel(MessageRepository(api), MutableStateFlow(login), scope)
            model.open(conversation(42)); model.close(); model.open(conversation(42))
            assertEquals(listOf(2L), model.uiState.value.messages.map { it.key })
            delayed.complete(response(1))
            assertEquals(listOf(2L), model.uiState.value.messages.map { it.key })
        } finally { scope.cancel() }
    }

    @Test
    fun `旧发送完成不会清空另一个会话草稿或发送状态`() = runBlocking {
        val delayed = CompletableDeferred<BiliResponse<JsonElement>>()
        val api = Api().apply { sendCall = { withContext(NonCancellable) { delayed.await() } } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = MessagesViewModel(MessageRepository(api), MutableStateFlow(login), scope)
            model.open(conversation(42)); model.setDraft("旧内容"); model.send()
            assertTrue(model.uiState.value.sending)
            model.open(conversation(43)); model.setDraft("新草稿")
            delayed.complete(BiliResponse(data = Json.parseToJsonElement("{}")))
            assertEquals("新草稿", model.uiState.value.draft)
            assertFalse(model.uiState.value.sending)
            assertNull(model.uiState.value.feedback)
        } finally { scope.cancel() }
    }

    @Test
    fun `更早消息失败重试原游标不重置最新历史`() = runBlocking {
        val cursors = mutableListOf<Long?>()
        val api = Api().apply { var retry = false; historyCall = { _, cursor ->
            cursors += cursor
            if (cursor == null) response(10).copy(data = MessageHistoryDto(response(10).data!!.messages, 5, 1))
            else if (!retry) { retry = true; error("网络失败") } else response(4)
        } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = MessagesViewModel(MessageRepository(api), MutableStateFlow(login), scope)
            model.open(conversation(42)); model.loadOlder(); model.retryMessages()
            assertEquals(listOf(null, 5L, 5L), cursors)
            assertEquals(listOf(4L, 10L), model.uiState.value.messages.map { it.key })
            assertNull(model.uiState.value.messagesError)
        } finally { scope.cancel() }
    }
}
