package com.qbili.domain.model

import com.qbili.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendationFilterGroupTest {
    @Test
    fun `三个管理入口只显示各自的屏蔽词并使用不同路由`() {
        val filters = RecommendationFilters(titleKeywords = setOf("标题词"),
            tagKeywords = setOf("标签词"), blockedChannels = setOf("游戏"))
        assertEquals(setOf("标题词"), RecommendationFilterGroup.TITLE.keywords(filters))
        assertEquals(setOf("标签词"), RecommendationFilterGroup.TAG.keywords(filters))
        assertEquals(setOf("游戏"), RecommendationFilterGroup.CHANNEL.keywords(filters))
        assertEquals(3, RecommendationFilterGroup.entries.map(Route::buildPushFilter).distinct().size)
        assertEquals("settings/push/title", Route.buildPushFilter(RecommendationFilterGroup.TITLE))
    }
}
