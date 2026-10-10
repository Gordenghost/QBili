package com.qbili.domain.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonPlaybackIntentTest {
    @Test
    fun `加载中单击暂停后播放地址返回也不能重新播放`() {
        val intent = SeasonPlaybackIntent()
        intent.setRequested(true)
        assertTrue(intent.shouldPlay)
        intent.toggle()
        assertFalse(intent.requested)
        assertFalse(intent.shouldPlay)
        intent.toggle()
        assertTrue(intent.shouldPlay)
    }

    @Test
    fun `离开页面或切到后台后旧请求不能启动播放`() {
        val intent = SeasonPlaybackIntent()
        intent.setRequested(true)
        intent.leave()
        assertFalse(intent.requested)
        assertFalse(intent.shouldPlay)
        intent.setRequested(true)
        assertFalse(intent.shouldPlay)
    }

    @Test
    fun `从评论或登录返回不自动播放但用户可以手动继续`() {
        val intent = SeasonPlaybackIntent()
        intent.setRequested(true)
        intent.leave()
        intent.enter()
        assertFalse(intent.shouldPlay)
        intent.toggle()
        assertTrue(intent.shouldPlay)
    }

    @Test
    fun `画质切换保留暂停和播放意图`() {
        val intent = SeasonPlaybackIntent()
        intent.setRequested(false)
        assertFalse(intent.shouldPlay)
        intent.setRequested(true)
        assertTrue(intent.shouldPlay)
    }
}
