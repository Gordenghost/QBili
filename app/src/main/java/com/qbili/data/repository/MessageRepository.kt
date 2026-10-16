package com.qbili.data.repository

import com.qbili.data.remote.api.MessageApi
import com.qbili.data.remote.dto.MessageTextDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.PrivateConversation
import com.qbili.domain.model.PrivateMessage
import com.qbili.domain.model.MessageContact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MessageRepository(private val api: MessageApi,
    private val contactLookup: suspend (Long) -> MessageContact? = { null },
) {
    private val deviceId = UUID.randomUUID().toString().uppercase()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sessions(endTs: Long? = null): SessionPage {
        val data = api.sessions(1, 1, 0, 2, PAGE_SIZE, endTs, 0, WEB_APP).requireData()
        val original = data.sessions.orEmpty()
        val limiter = Semaphore(3)
        val conversations = coroutineScope {
            original.filter { it.sessionType == 1 && it.systemType == 0 && it.talkerId > 0 }
                .distinctBy { it.talkerId }.map { session ->
                    async {
                        val existingName = session.account?.name.orEmpty().ifBlank { session.account?.uname.orEmpty() }
                        val existingFace = session.account?.face.orEmpty().ifBlank { session.account?.faceUrl.orEmpty() }
                        val contact = if (existingName.isBlank() || existingFace.isBlank()) limiter.withPermit {
                            try { withTimeoutOrNull(3_000) { contactLookup(session.talkerId) } }
                            catch (error: CancellationException) { throw error }
                            catch (_: Exception) { null }
                        } else null
                        val name = existingName.ifBlank { contact?.name.orEmpty() }
                        PrivateConversation(
                            talkerId = session.talkerId,
                            name = name.ifBlank { "用户${session.talkerId}" },
                            face = existingFace.ifBlank { contact?.face.orEmpty() },
                            preview = session.lastMessage?.let {
                                MessageContentParser.parse(it.type, it.content, it.status).text
                            }.orEmpty(),
                            timestamp = session.lastMessage?.timestamp ?: 0,
                            unreadCount = session.unreadCount,
                            cursor = session.sessionTs,
                            profileUnavailable = name.isBlank(),
                        )
                    }
                }.awaitAll()
        }
        return SessionPage(
            conversations,
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
                val content = MessageContentParser.parse(entry.type, entry.content, entry.status)
                PrivateMessage(entry.key, entry.seqNo, entry.senderMid, content.text, entry.timestamp,
                    imageUrl = content.imageUrl, type = if (entry.status == 1) 5 else entry.type,
                    card = content.card, systemNotice = content.systemNotice)
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

    data class SessionPage(val sessions: List<PrivateConversation>, val next: Long?)
    data class HistoryPage(val messages: List<PrivateMessage>, val older: Long?)

    companion object {
        const val MAX_TEXT_LENGTH = 500
        private const val PAGE_SIZE = 20
        private const val WEB_APP = "web"
    }
}
