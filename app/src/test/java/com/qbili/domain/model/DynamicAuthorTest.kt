package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicAuthorTest {
    @Test
    fun `关注列表与动态作者合并按UID去重而非按同名去重`() {
        val first = DynamicAuthor(42, "同名", "face")
        val second = DynamicAuthor(43, "同名", "other")
        val result = listOf(first).mergeDynamicAuthors(listOf(first.copy(face = "updated"), second,
            DynamicAuthor(0, "无效", "")))
        assertEquals(listOf(first, second), result)
    }

    @Test
    fun `搜索支持昵称忽略大小写和精确UID清空搜索恢复全部`() {
        val authors = listOf(DynamicAuthor(42, "Test UP", ""), DynamicAuthor(142, "另一位", ""))
        assertEquals(listOf(authors.first()), authors.searchDynamicAuthors(" test "))
        assertEquals(listOf(authors.first()), authors.searchDynamicAuthors("42"))
        assertEquals(authors, authors.searchDynamicAuthors("  "))
        assertEquals(emptyList<DynamicAuthor>(), authors.searchDynamicAuthors("找不到"))
    }
}
