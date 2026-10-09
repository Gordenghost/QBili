package com.qbili.data.repository

import com.qbili.data.remote.api.MessageApi
import com.qbili.data.remote.dto.MessageEntryDto
import com.qbili.data.remote.dto.MessageTextDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.PrivateConversation
import com.qbili.domain.model.PrivateMessage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MessageRepository(private val api: MessageApi) {
    private val deviceId = UUID.randomUUID().toString().uppercase()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sessions(endTs: Long? = null): SessionPage {
        val data = api.sessions(1, 1, 0, 2, PAGE_SIZE, endTs, 0, WEB_APP).requireData()
        val original = data.sessions.orEmpty()
        return SessionPage(
            original.filter { it.sessionType == 1 && it.systemType == 0 && it.talkerId > 0 }
                .map { session ->
                    PrivateConversation(
                        talkerId = session.talkerId,
                        name = session.account?.name?.takeIf { it.isNotBlank() } ?: "用户${session.talkerId}",
                        face = session.account?.face.orEmpty(),
                        preview = session.lastMessage?.displayText().orEmpty(),
                        timestamp = session.lastMessage?.timestamp ?: 0,
                        unreadCount = session.unreadCount,
                        cursor = session.sessionTs,
                    )
                },
            // 分页游标必须取原始列表末尾，过滤系统会话后仍需正确翻页。
            next = original.lastOrNull()?.sessionTs?.takeIf {
                data.hasMore == 1 && it > 0 && it != endTs
            },
        )
    }

    suspend fun history(talkerId: Long, endSeqNo: Long? = null): HistoryPage {
        require(talkerId > 0)
        val data = api.history(talkerId, 1, PAGE_SIZE, endSeqNo, "1", 0, WEB_APP).requireData()
        val original = data.messages.orEmpty()
        return HistoryPage(
            messages = original.filter { it.key > 0 }.map { entry ->
                PrivateMessage(entry.key, entry.seqNo, entry.senderMid, entry.displayText(), entry.timestamp)
            }.sortedBy { it.seqNo },
            older = data.minSeqNo.takeIf {
                data.hasMore == 1 && original.isNotEmpty() && it > 0 && it != endSeqNo
            },
        )
    }

    suspend fun send(senderMid: Long, talkerId: Long, text: String) {
        require(senderMid > 0 && talkerId > 0 && senderMid != talkerId)
        val message = text.trim()
        require(message.isNotEmpty() && message.length <= MAX_TEXT_LENGTH)
        val content = json.encodeToString(MessageTextDto(message))
        api.send(
            signedSender = senderMid, signedReceiver = talkerId, signedDevice = deviceId,
            sender = senderMid, receiverType = 1, receiver = talkerId,
            messageType = 1, status = 0, content = content,
            newFaceVersion = 0, device = deviceId,
            timestamp = System.currentTimeMillis() / 1000,
            fromFirework = 0, build = 0, app = WEB_APP,
        ).requireData()
    }

    private fun MessageEntryDto.displayText(): String = when (type) {
        1 -> runCatching { json.decodeFromString<MessageTextDto>(content).content }
            .getOrDefault("[文字消息无法显示]")
        2 -> "[图片]"
        5 -> "[已撤回的消息]"
        6 -> "[表情]"
        else -> "[其他类型的消息]"
    }

    data class SessionPage(val sessions: List<PrivateConversation>, val next: Long?)
    data class HistoryPage(val messages: List<PrivateMessage>, val older: Long?)

    companion object {
        const val MAX_TEXT_LENGTH = 500
        private const val PAGE_SIZE = 20
        private const val WEB_APP = "web"
    }
}
