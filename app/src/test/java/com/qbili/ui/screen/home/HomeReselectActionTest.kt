package com.qbili.ui.screen.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReselectActionTest {
    @Test
    fun `不在顶部点击首页只回顶`() {
        assertEquals(HomeReselectAction.SCROLL_TO_TOP, homeReselectAction(20, 0, false))
        assertEquals(HomeReselectAction.SCROLL_TO_TOP, homeReselectAction(20, 120, false))
    }

    @Test
    fun `首行有滚动偏移时也应回顶而非刷新`() {
        assertEquals(HomeReselectAction.SCROLL_TO_TOP, homeReselectAction(0, 1, false))
    }

    @Test
    fun `顶部点击首页刷新推荐`() {
        assertEquals(HomeReselectAction.REFRESH, homeReselectAction(0, 0, false))
    }

    @Test
    fun `回顶后再次点击才刷新`() {
        assertEquals(HomeReselectAction.SCROLL_TO_TOP, homeReselectAction(12, 48, false))
        assertEquals(HomeReselectAction.REFRESH, homeReselectAction(0, 0, false))
    }

    @Test
    fun `加载中不重复刷新但仍允许回顶`() {
        assertEquals(HomeReselectAction.NONE, homeReselectAction(0, 0, true))
        assertEquals(HomeReselectAction.SCROLL_TO_TOP, homeReselectAction(6, 0, true))
    }
}
