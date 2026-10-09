package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MessageSessionsDto(
    @SerialName("session_list") val sessions: List<MessageSessionDto>? = null,
    @SerialName("has_more") val hasMore: Int = 0,
)

@Serializable
data class MessageSessionDto(
    @SerialName("talker_id") val talkerId: Long = 0,
    @SerialName("session_type") val sessionType: Int = 1,
    @SerialName("system_msg_type") val systemType: Int = 0,
    @SerialName("session_ts") val sessionTs: Long = 0,
    @SerialName("unread_count") val unreadCount: Int = 0,
    @SerialName("account_info") val account: MessageAccountDto? = null,
    @SerialName("last_msg") val lastMessage: MessageEntryDto? = null,
)

@Serializable
data class MessageAccountDto(
    val name: String = "",
    @SerialName("pic_url") val face: String = "",
)

@Serializable
data class MessageHistoryDto(
    val messages: List<MessageEntryDto>? = null,
    @SerialName("min_seqno") val minSeqNo: Long = 0,
    @SerialName("has_more") val hasMore: Int = 0,
)

@Serializable
data class MessageEntryDto(
    @SerialName("msg_key") val key: Long = 0,
    @SerialName("msg_seqno") val seqNo: Long = 0,
    @SerialName("sender_uid") val senderMid: Long = 0,
    @SerialName("msg_type") val type: Int = 0,
    val content: String = "",
    val timestamp: Long = 0,
)

@Serializable
data class MessageTextDto(val content: String = "")
