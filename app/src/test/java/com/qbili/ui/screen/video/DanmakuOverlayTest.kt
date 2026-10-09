package com.qbili.ui.screen.video

import org.junit.Assert.assertEquals
import org.junit.Test

class DanmakuOverlayTest {
    @Test
    fun `inline player uses smaller danmaku than fullscreen`() {
        assertEquals(15f, danmakuFontSizeSp(25, 1f, fullscreen = false), 0.001f)
        assertEquals(25f, danmakuFontSizeSp(25, 1f, fullscreen = true), 0.001f)
    }

    @Test
    fun `font setting affects both inline and fullscreen playback`() {
        assertEquals(22.5f, danmakuFontSizeSp(25, 1.5f, fullscreen = false), 0.001f)
        assertEquals(37.5f, danmakuFontSizeSp(25, 1.5f, fullscreen = true), 0.001f)
    }
}
