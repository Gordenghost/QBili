package com.qbili.ui.screen.comment

import com.qbili.domain.model.VideoComment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentDraftTest {
    private val root = VideoComment(
        id = 42, rootId = 0, authorMid = 1, authorName = "root",
        authorFace = "", message = "original comment", createdAt = 0,
        likeCount = 0, replyCount = 1,
    )

    private val nested = root.copy(id = 43, rootId = 42, authorName = "nested")

    @Test
    fun `selecting another reply target cannot resend the previous draft`() {
        val previous = CommentViewModel.UiState(draft = "original comment")
        val replying = previous.selectReply(root)
        assertEquals(root, replying.replyTo)
        assertEquals("", replying.draft)

        val nestedReply = replying.copy(draft = "new answer").selectReply(nested)
        assertEquals(nested, nestedReply.replyTo)
        assertEquals("", nestedReply.draft)
        assertEquals("new answer", replying.copy(draft = "new answer").selectReply(root).draft)

        val cancelled = nestedReply.copy(draft = "unsubmitted").selectReply(null)
        assertNull(cancelled.replyTo)
        assertEquals("", cancelled.draft)
    }
}
