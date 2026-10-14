package com.qbili.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.material3.contentColorFor
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import com.qbili.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `同为白色的内容容器不因主色容器匹配而变成红字`() {
        listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT).forEach { mode ->
            val scheme = qbiliColorScheme(mode, systemDark = false)
            assertEquals(scheme.onSurface, scheme.contentColorFor(Color.White))
            assertEquals(scheme.onSurface, scheme.onPrimaryContainer)
            assertEquals(scheme.onSurface, scheme.onSecondaryContainer)
            assertEquals(scheme.onSurface, scheme.onTertiaryContainer)
            assertNotEquals(scheme.primary, scheme.contentColorFor(scheme.surface))
        }
    }

    @Test
    fun `跟随系统浅色时所有聚焦容器均亮于背景且保持白色`() {
        val scheme = qbiliColorScheme(ThemeMode.SYSTEM, systemDark = false)
        listOf(scheme.surface, scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
            scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
            scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer).forEach { container ->
            assertEquals(Color.White, container)
            assertTrue(container.luminance() > scheme.background.luminance())
        }
        assertNotEquals(Color.White, scheme.surfaceVariant)
    }

    @Test
    fun `清爽白的卡片及主容器均为白色不随系统变暗`() {
        val scheme = qbiliColorScheme(ThemeMode.LIGHT, systemDark = true)
        assertEquals(Color.White, scheme.surface)
        assertEquals(Color.White, scheme.surfaceContainer)
        assertEquals(Color.White, scheme.surfaceContainerLowest)
        assertEquals(Color.White, scheme.surfaceContainerLow)
        assertEquals(Color.White, scheme.surfaceContainerHigh)
        assertEquals(Color.White, scheme.surfaceContainerHighest)
        assertEquals(Color.White, scheme.primaryContainer)
        assertEquals(Color.White, scheme.secondaryContainer)
        assertEquals(Color.White, scheme.tertiaryContainer)
        assertEquals(Color(0xFFF5F5F5), scheme.background)
        assertEquals(Color.Transparent, scheme.surfaceTint)
        assertEquals(BiliPink, scheme.primary)
        assertEquals(BiliBlue, scheme.secondary)
        assertTrue(scheme.surface.luminance() > scheme.background.luminance())
        assertTrue(scheme.onSurface.luminance() < scheme.surface.luminance())
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
