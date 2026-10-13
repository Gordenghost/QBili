package com.qbili.ui.theme

import androidx.compose.ui.graphics.Color
import com.qbili.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `清爽白的卡片及主容器均为白色不随系统变暗`() {
        val scheme = qbiliColorScheme(ThemeMode.LIGHT, systemDark = true)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color.White, scheme.surfaceContainer)
        assertEquals(Color.White, scheme.surfaceContainerLowest)
        assertEquals(Color(0xFFF5F5F5), scheme.background)
        assertEquals(Color.Transparent, scheme.surfaceTint)
        assertEquals(BiliPink, scheme.primary)
        assertEquals(BiliBlue, scheme.secondary)
    }

    @Test
    fun `手动深色与系统深色相同且系统浅色使用清爽白`() {
        val dark = qbiliColorScheme(ThemeMode.DARK, systemDark = false)
        assertEquals(dark, qbiliColorScheme(ThemeMode.SYSTEM, systemDark = true))
        val light = qbiliColorScheme(ThemeMode.LIGHT, systemDark = false)
        assertEquals(light, qbiliColorScheme(ThemeMode.SYSTEM, systemDark = false))
        assertNotEquals(light.surface, dark.surface)
    }
}
