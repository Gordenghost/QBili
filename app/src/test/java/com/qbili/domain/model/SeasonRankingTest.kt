package com.qbili.domain.model

import com.qbili.data.remote.dto.SeasonRankingEntryDto
import com.qbili.data.remote.dto.SeasonRankingEpisodeDto
import com.qbili.data.remote.dto.SeasonRankingStatDto
import com.qbili.data.remote.dto.SeasonRatingDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeasonRankingTest {
    @Test
    fun `正片类别使用seasonType而不是投稿rid周期仅三日与七日`() {
        assertEquals(listOf(1, 4, 2, 5, 3, 7), SeasonRankingCategory.entries.map { it.seasonType })
        assertEquals(listOf(1), SeasonRankingCategory.entries.filter { it.legacyRankEndpoint }.map { it.seasonType })
        assertEquals(listOf(3, 7), SeasonRankingPeriod.entries.map { it.days })
    }

    @Test
    fun `评分缺失零负数溢出及非有限值均不伪造评分`() {
        listOf(null, 0.0, -1.0, 10.1, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertNull(validSeasonRating(it)) }
        assertEquals(9.5, validSeasonRating(9.5)!!, 0.0)
        assertEquals(10.0, validSeasonRating(10.0)!!, 0.0)
    }

    @Test
    fun `缺失资料不伪造会员评分播放量排版与封面规范化`() {
        val basic = SeasonRankingEntryDto(seasonId = 42).toRankingItem(7)
        assertEquals(7, basic.rank)
        assertEquals("番剧／影视", basic.title)
        assertEquals("", basic.badge)
        assertNull(basic.score)
        assertNull(basic.viewCount)
        val full = SeasonRankingEntryDto(seasonId = 42, rank = 2, title = "<em>标题</em>",
            cover = "//i0.hdslb.com/a.jpg", indexShow = "旧进度", badge = "会员",
            newEpisode = SeasonRankingEpisodeDto(indexShow = "全12集"), rating = SeasonRatingDto(9.8),
            stat = SeasonRankingStatDto(view = -1, follow = 0)).toRankingItem(1)
        assertEquals("标题", full.title)
        assertEquals("https://i0.hdslb.com/a.jpg", full.cover)
        assertEquals(2, full.rank)
        assertEquals("全12集", full.progress)
        assertEquals("会员", full.badge)
        assertEquals(9.8, full.score!!, 0.0)
        assertNull(full.viewCount)
        assertEquals(0L, full.followCount)
    }

    @Test
    fun `更新说明按非空字段回退不受空newEp遮蔽`() {
        val dto = SeasonRankingEntryDto(indexShow = "更新至8集", newEpisode = SeasonRankingEpisodeDto())
        assertEquals("更新至8集", dto.toRankingItem(1).progress)
        assertEquals("全10集", dto.copy(newEpisode = SeasonRankingEpisodeDto(description = "全10集")).toRankingItem(1).progress)
    }
}
