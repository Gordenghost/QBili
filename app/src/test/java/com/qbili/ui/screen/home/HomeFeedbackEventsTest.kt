package com.qbili.ui.screen.home

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFeedbackEventsTest {
    @Test
    fun `重复成功消息也会排队并逐条显示`() = runBlocking {
        val feedback = HomeFeedbackEvents()
        feedback.send("成功屏蔽")
        feedback.send("成功屏蔽")

        val messages = async { feedback.events.take(2).toList() }
        assertEquals(listOf("成功屏蔽", "成功屏蔽"), messages.await())
    }
}
