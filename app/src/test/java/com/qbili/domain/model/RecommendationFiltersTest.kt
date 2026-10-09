package com.qbili.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationFiltersTest {
    @Test
    fun `视频UP和频道规则会阻止推荐且不误伤其他条目`() {
        val filters = RecommendationFilters(
            hiddenVideos = setOf("BVhidden"), blockedAuthors = setOf(42),
            blockedChannels = setOf("游戏"),
        )
        fun video(bvid: String, mid: Long, channel: String) = VideoItem(
            aid = 1, bvid = bvid, title = "正常标题", cover = "",
            authorMid = mid, channel = channel,
        )
        assertTrue(filters.blocksVideo(video("BVhidden", 1, "生活")))
        assertTrue(filters.blocksVideo(video("BVsecond", 42, "生活")))
        assertTrue(filters.blocksVideo(video("BVthird", 1, "游戏")))
        assertFalse(filters.blocksVideo(video("BVfourth", 1, "生活")))
    }
    @Test
    fun `标题关键词大小写不敏感且忽略空规则`() {
        val filters = RecommendationFilters(titleKeywords = setOf("Game", "猫", "   "))

        assertTrue(filters.blocksTitle("独立 game 试玩"))
        assertTrue(filters.blocksTitle("小猫咪日常"))
        assertFalse(filters.blocksTitle("户外徒步"))
    }

    @Test
    fun `Tag 规则只匹配标签名称的子串`() {
        val filters = RecommendationFilters(tagKeywords = setOf("音 乐", "VLOG"))

        assertTrue(filters.needsTagLookup)
        assertTrue(filters.blocksTags(listOf("旅游", "城市Vlog")))
        assertTrue(filters.blocksTags(listOf("流行音 乐")))
        assertFalse(filters.blocksTags(listOf("旅行", "数码科技")))
        assertFalse(filters.blocksTitle("城市Vlog"))
    }

    @Test
    fun `未启用 Tag 规则时不用查询标签`() {
        val filters = RecommendationFilters(titleKeywords = setOf("广告"))

        assertFalse(filters.needsTagLookup)
        assertFalse(filters.blocksTags(listOf("广告")))
    }
}
