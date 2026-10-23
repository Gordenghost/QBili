package com.qbili.domain.player

import org.junit.Assert.*
import org.junit.Test

class VideoPageNavigationTest {
    @Test
    fun `上一P下一P严格遵守索引首末不循环`() {
        assertNull(adjacentVideoPage(0, 3, -1))
        assertEquals(1, adjacentVideoPage(0, 3, 1))
        assertEquals(0, adjacentVideoPage(1, 3, -1))
        assertEquals(2, adjacentVideoPage(1, 3, 1))
        assertNull(adjacentVideoPage(2, 3, 1))
        assertNull(adjacentVideoPage(0, 1, 1))
    }

    @Test
    fun `非法索引数量和跳跃方向不能导航`() {
        assertNull(adjacentVideoPage(-1, 3, 1))
        assertNull(adjacentVideoPage(3, 3, -1))
        assertNull(adjacentVideoPage(0, 0, 1))
        assertNull(adjacentVideoPage(0, 3, 0))
        assertNull(adjacentVideoPage(0, 3, 2))
    }
}
