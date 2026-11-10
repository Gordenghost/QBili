package com.qbili.ui.screen.history

import com.qbili.domain.model.HistoryContentType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryUiStateTest {
    private val account = HistoryViewModel.UiState(local = false, loggedIn = true, hasMore = true)

    @Test
    fun `未筛选的账号列表允许自动加载空白关键词不算筛选`() {
        assertTrue(account.autoLoadEnabled)
        assertFalse(account.copy(query = " \t ").filtering)
        assertTrue(account.copy(query = " \t ").autoLoadEnabled)
    }

    @Test
    fun `输入关键词或选择类型后关闭自动翻页即使没有匹配结果`() {
        val searched = account.copy(query = "没有匹配结果")
        assertTrue(searched.items.isEmpty())
        assertTrue(searched.filtering)
        assertFalse(searched.autoLoadEnabled)
        HistoryContentType.entries.filter { it != HistoryContentType.ALL }.forEach { type ->
            assertTrue(account.copy(contentType = type).filtering)
            assertFalse(account.copy(contentType = type).autoLoadEnabled)
        }
    }

    @Test
    fun `本机记录加载中操作中无更多记录和错误状态禁止自动加载`() {
        assertFalse(account.copy(local = true).autoLoadEnabled)
        assertFalse(account.copy(loading = true).autoLoadEnabled)
        assertFalse(account.copy(busy = true).autoLoadEnabled)
        assertFalse(account.copy(hasMore = false).autoLoadEnabled)
        assertFalse(account.copy(error = "网络失败").autoLoadEnabled)
    }

    @Test
    fun `筛选重置或手动重试成功后恢复普通列表自动加载`() {
        val filtered = account.copy(query = "UP", contentType = HistoryContentType.VIDEO)
        assertTrue(filtered.copy(query = "", contentType = HistoryContentType.ALL).autoLoadEnabled)
        assertTrue(account.copy(error = "失败").copy(error = null).autoLoadEnabled)
    }
}
