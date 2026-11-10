package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RankingContentTest {
    private val issues = listOf(
        WeeklyIssue(394, "2026第394期 10.02 - 10.08", "被解救的杰哥"),
        WeeklyIssue(393, "2026第393期 09.25 - 10.01", "Kotlin 分享"),
    )

    @Test
    fun `选期按期数日期主题多关键词搜索并保持顺序`() {
        assertEquals(issues, filterWeeklyIssues(issues, " \t "))
        assertEquals(listOf(issues[0]), filterWeeklyIssues(issues, "394 杰哥"))
        assertEquals(listOf(issues[0]), filterWeeklyIssues(issues, "10.02"))
        assertEquals(listOf(issues[1]), filterWeeklyIssues(issues, "kotlin 2026"))
        assertEquals(emptyList<WeeklyIssue>(), filterWeeklyIssues(issues, "394 Kotlin"))
    }

    @Test
    fun `投稿分区标识唯一不混用番剧影视排行参数`() {
        assertEquals(RankingCategory.entries.size, RankingCategory.entries.map { it.rid }.distinct().size)
        assertEquals(0, RankingCategory.ALL.rid)
        assertEquals(4, RankingCategory.GAME.rid)
        assertEquals(181, RankingCategory.FILM.rid)
    }
}
