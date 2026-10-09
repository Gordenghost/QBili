package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CommentTextSegmentTest {
    @Test
    fun `只替换服务端确认的表情并保留其它原文`() {
        val parts = segmentCommentText("你好[doge] [不认识]再见[doge]", mapOf("[doge]" to "https://i0/a.png"))
        assertEquals(
            listOf(
                CommentTextSegment("你好"), CommentTextSegment("[doge]", "https://i0/a.png"),
                CommentTextSegment(" [不认识]再见"), CommentTextSegment("[doge]", "https://i0/a.png"),
            ),
            parts,
        )
    }

    @Test
    fun `服务端不提供图片时原文不丢失`() {
        assertEquals(
            listOf(CommentTextSegment("[doge]正文")),
            segmentCommentText("[doge]正文", mapOf("[doge]" to "")),
        )
    }
}
