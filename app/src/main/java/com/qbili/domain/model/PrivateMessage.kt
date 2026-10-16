package com.qbili.domain.model

data class PrivateConversation(
    val talkerId: Long,
    val name: String,
    val face: String,
    val preview: String,
    val timestamp: Long,
    val unreadCount: Int,
    val cursor: Long,
    val profileUnavailable: Boolean = false,
)

data class PrivateMessage(
    val key: Long,
    val seqNo: Long,
    val senderMid: Long,
    val text: String,
    val timestamp: Long,
    val imageUrl: String? = null,
    val type: Int = 1,
    val card: MessageCard? = null,
    val systemNotice: Boolean = false,
)

data class MessageContact(val name: String, val face: String)
