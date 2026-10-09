package com.qbili.core

import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMessagesTest {
    @Test
    fun `rate limit is explained without implying missing content`() {
        val message = BiliApiException(-799, "请求过于频繁").friendlyMessage()
        assertTrue(message.contains("请求过于频繁"))
        assertTrue(message.contains("稍后再试"))
    }
}
