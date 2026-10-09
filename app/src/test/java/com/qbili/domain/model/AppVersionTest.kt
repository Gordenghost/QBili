package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `版本支持 GitHub 标签且规范化为三段数字`() {
        assertEquals(AppVersion(0, 2, 235), AppVersion.parse("v0.2.235"))
        assertEquals(AppVersion(1, 0, 9), AppVersion.parse(" V1.0.009 "))
        assertEquals("0.2.235", AppVersion.parse("0.2.235")?.name)
    }

    @Test
    fun `版本按照数字比较而不是字符串排序`() {
        assertTrue(AppVersion(0, 2, 100) > AppVersion(0, 2, 99))
        assertTrue(AppVersion(0, 10, 1) > AppVersion(0, 9, 999))
        assertTrue(AppVersion(1, 0, 1) > AppVersion(0, 99, 999))
        assertEquals(0, AppVersion(0, 2, 235).compareTo(AppVersion(0, 2, 235)))
    }

    @Test
    fun `不接受预发布标签无效版本或数字溢出`() {
        listOf("", "latest", "0.2", "0x2x235", "0.2.-1", "0.2.235-beta", "1.2.3.4",
            "v0.2.9999999999999999999999").forEach { value ->
            assertNull(value, AppVersion.parse(value))
        }
    }
}
