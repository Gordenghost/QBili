package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicTextTest {
    @Test
    fun `行内图片占位标记单独成行`() {
        assertEquals("上文\n[图片]\n下文", formatDynamicText("上文[图片]下文"))
        assertEquals("[图片]\n正文", formatDynamicText("【图片】正文"))
    }

    @Test
    fun `已有分行的标记不重复插入空行且不替换普通图片文字`() {
        assertEquals("第一行\n[图片]\n图片不错", formatDynamicText("第一行\n[图片]\n图片不错"))
    }
}
