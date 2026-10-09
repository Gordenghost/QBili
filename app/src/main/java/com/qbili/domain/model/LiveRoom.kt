package com.qbili.domain.model

data class LiveRoom(
    val roomId: Long,
    val title: String,
    val cover: String,
    val anchor: String,
    val anchorFace: String,
    val anchorMid: Long,
    val area: String,
    val online: Long,
    val isLive: Boolean,
    val streamUrl: String? = null,
    val quality: Int = 0,
)

data class LiveChatConnection(val roomId: Long, val url: String, val token: String)

data class LiveChatMessage(val sender: String, val text: String)
