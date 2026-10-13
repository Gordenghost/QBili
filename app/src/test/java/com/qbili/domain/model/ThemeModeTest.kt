package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeModeTest {
    @Test
    fun `未设置或未知主题回退跟随系统`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey("future"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(""))
        ThemeMode.entries.forEach { option -> assertEquals(option, ThemeMode.fromKey(option.key)) }
    }

    @Test
    fun `跟随系统自动切换而手动主题不受系统影响`() {
        assertFalse(ThemeMode.SYSTEM.isDark(false))
        assertTrue(ThemeMode.SYSTEM.isDark(true))
        assertFalse(ThemeMode.LIGHT.isDark(false))
        assertFalse(ThemeMode.LIGHT.isDark(true))
        assertTrue(ThemeMode.DARK.isDark(false))
        assertTrue(ThemeMode.DARK.isDark(true))
    }
}
