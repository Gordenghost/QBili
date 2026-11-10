package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DanmakuPostTest {
    private val target = DanmakuTarget(2, "BV1xx411c7mD", 62131, 60_000)

    @Test
    fun `正文原样保留特殊字符且时间限制在当前分P`() {
        val request = DanmakuPost(target, "  你好 & + % ' ! 😄  ", 100_000).validated()
        assertEquals("你好 & + % ' ! 😄", request.message)
        assertEquals(60_000L, request.progressMillis)
        assertEquals(0L, DanmakuPost(target, "测试", -5).validated().progressMillis)
    }

    @Test
    fun `字符计数支持表情且拒绝无效输入类型颜色或分P`() {
        assertEquals("😄".repeat(100), DanmakuPost(target, "😄".repeat(100), 0).validated().message)
        val invalid = listOf(
            DanmakuPost(target, "😄".repeat(101), 0), DanmakuPost(target, " \t ", 0),
            DanmakuPost(target, "第一行\n第二行", 0), DanmakuPost(target, "控制\u0000字符", 0),
            DanmakuPost(target, "内容", 0, DanmakuMode.ADVANCED),
            DanmakuPost(target, "内容", 0, color = 0x1000000),
            DanmakuPost(target.copy(cid = 0), "内容", 0),
            DanmakuPost(target.copy(durationMillis = 0), "内容", 0),
            DanmakuPost(target.copy(bvid = "av2"), "内容", 0),
        )
        invalid.forEach { request ->
            try { request.validated(); fail("必须拒绝无效弹幕") } catch (_: IllegalArgumentException) { }
        }
    }
}
