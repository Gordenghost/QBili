package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageCardTest {
    @Test
    fun `精确站内链接识别视频专栏图文直播番剧和主页`() {
        assertEquals(MessageOpenTarget.Video("BV16v411e7CW"),
            messageLinkTarget("https://www.bilibili.com/video/BV16v411e7CW"))
        assertEquals(MessageOpenTarget.Video("av42"), messageLinkTarget("https://m.bilibili.com/video/av42/"))
        assertEquals(MessageOpenTarget.Article(42), messageLinkTarget("https://bilibili.com/read/cv42"))
        assertEquals(MessageOpenTarget.Opus("12345678901234567890"),
            messageLinkTarget("https://www.bilibili.com/opus/12345678901234567890"))
        assertEquals(MessageOpenTarget.Live(42), messageLinkTarget("https://m.live.bilibili.com/42/"))
        assertEquals(MessageOpenTarget.Season(42), messageLinkTarget("https://www.bilibili.com/bangumi/play/ss42"))
        assertEquals(MessageOpenTarget.Space(42), messageLinkTarget("https://space.bilibili.com/42"))
    }

    @Test
    fun `标准端口协议相对查询片段不影响路径识别`() {
        assertEquals(MessageOpenTarget.Video("av42"), messageLinkTarget("//www.bilibili.com/video/av42?from=im#reply"))
        assertEquals(MessageOpenTarget.Live(42), messageLinkTarget("http://live.bilibili.com:80/42"))
        assertEquals(MessageOpenTarget.Space(42), messageLinkTarget("https://space.bilibili.com:443/42/?from=im"))
        assertEquals(MessageOpenTarget.Article(42), messageLinkTarget(" HTTPS://WWW.BILIBILI.COM/read/cv42 "))
    }

    @Test
    fun `拒绝外域用户信息危险协议非标准端口和控制字符`() {
        listOf(
            "https://evil.test/video/av42", "https://www.bilibili.com.evil.test/video/av42",
            "https://www.bilibili.com@evil.test/video/av42", "https://user@www.bilibili.com/video/av42",
            "https://www.bilibili.com:444/video/av42", "http://www.bilibili.com:443/video/av42",
            "javascript:alert(1)", "bilibili://video/42", "intent://video/42", "file:///video/av42",
            "https://www.bilibili.com/video/av42\n", "\thttps://www.bilibili.com/video/av42",
            "https://www.bilibili.com/video/av42\u0000", "https://b23.tv/abc",
        ).forEach { assertNull(it, messageLinkTarget(it)) }
    }

    @Test
    fun `拒绝无效ID路径注入未知业务和错误子域名`() {
        listOf(
            "https://www.bilibili.com/video/av0", "https://www.bilibili.com/video/av-42",
            "https://www.bilibili.com/video/BVbad", "https://www.bilibili.com/video/av999999999999999999999999",
            "https://www.bilibili.com/video/av42/more", "https://www.bilibili.com/video/av%34%32",
            "https://www.bilibili.com/video/av42/../av43", "https://live.bilibili.com/read/cv42",
            "https://www.bilibili.com/42", "https://space.bilibili.com/video/av42",
            "https://www.bilibili.com/bangumi/play/ep42", "https://www.bilibili.com/cheese/play/ep42",
            "https://t.bilibili.com/42", "https://www.bilibili.com/opus/" + "1".repeat(31),
            "https://www.bilibili.com/video/av42?" + "a".repeat(4096), "not a url",
        ).forEach { assertNull(it, messageLinkTarget(it)) }
    }
}
