package com.qbili.domain.model

import java.net.URI

data class MessageCard(
    val title: String,
    val label: String,
    val description: String = "",
    val author: String = "",
    val cover: String? = null,
    val links: List<MessageLink> = emptyList(),
)

data class MessageLink(val label: String, val target: MessageOpenTarget)

sealed interface MessageOpenTarget {
    data class Video(val id: String) : MessageOpenTarget
    data class Article(val id: Long) : MessageOpenTarget
    data class Opus(val id: String) : MessageOpenTarget
    data class Live(val roomId: Long) : MessageOpenTarget
    data class Season(val id: Long) : MessageOpenTarget
    data class Space(val mid: Long) : MessageOpenTarget
}

fun messageLinkTarget(source: String): MessageOpenTarget? {
    if (source.any { it.isISOControl() } || source.length > 4096) return null
    val raw = source.trim()
    val uri = runCatching { URI(if (raw.startsWith("//")) "https:$raw" else raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("https", "http") || uri.rawUserInfo != null ||
        uri.port !in setOf(-1, if (scheme == "https") 443 else 80)) return null
    val host = uri.host?.lowercase()
    val path = uri.rawPath.orEmpty()
    fun numericPath(pattern: String): Long? = Regex(pattern).matchEntire(path)?.groupValues?.get(1)
        ?.toLongOrNull()?.takeIf { it > 0 }
    if (host in setOf("www.bilibili.com", "bilibili.com", "m.bilibili.com")) {
        Regex("/video/(BV[0-9A-Za-z]{10})/?").matchEntire(path)?.groupValues?.get(1)
            ?.let { return MessageOpenTarget.Video(it) }
        numericPath("/video/av([1-9][0-9]*)/?")?.let { return MessageOpenTarget.Video("av$it") }
        numericPath("/read/cv([1-9][0-9]*)/?")?.let { return MessageOpenTarget.Article(it) }
        Regex("/opus/([1-9][0-9]{0,29})/?").matchEntire(path)?.groupValues?.get(1)
            ?.let { return MessageOpenTarget.Opus(it) }
        numericPath("/bangumi/play/ss([1-9][0-9]*)/?")?.let { return MessageOpenTarget.Season(it) }
    }
    if (host in setOf("live.bilibili.com", "m.live.bilibili.com")) {
        numericPath("/([1-9][0-9]*)/?")?.let { return MessageOpenTarget.Live(it) }
    }
    if (host == "space.bilibili.com") {
        numericPath("/([1-9][0-9]*)/?")?.let { return MessageOpenTarget.Space(it) }
    }
    return null
}

val MessageOpenTarget.contentLabel: String
    get() = when (this) {
        is MessageOpenTarget.Video -> "视频"
        is MessageOpenTarget.Article -> "专栏"
        is MessageOpenTarget.Opus -> "图文"
        is MessageOpenTarget.Live -> "直播"
        is MessageOpenTarget.Season -> "番剧／影视"
        is MessageOpenTarget.Space -> "个人主页"
    }
