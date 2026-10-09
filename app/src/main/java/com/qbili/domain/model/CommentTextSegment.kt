package com.qbili.domain.model

data class CommentTextSegment(val text: String, val imageUrl: String? = null)

private val emotePattern = Regex("""\[[^\[\]\r\n]{1,80}]""")

/** 只替换服务端明确给出 URL 的表情，未知转义符保留原文。 */
fun segmentCommentText(message: String, emotes: Map<String, String>): List<CommentTextSegment> {
    val segments = mutableListOf<CommentTextSegment>()
    var cursor = 0
    for (match in emotePattern.findAll(message)) {
        val url = emotes[match.value]?.takeIf { it.isNotBlank() } ?: continue
        if (match.range.first > cursor) {
            segments += CommentTextSegment(message.substring(cursor, match.range.first))
        }
        segments += CommentTextSegment(match.value, url)
        cursor = match.range.last + 1
    }
    if (cursor < message.length) segments += CommentTextSegment(message.substring(cursor))
    return segments
}
