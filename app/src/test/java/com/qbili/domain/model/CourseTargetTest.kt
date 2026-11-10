package com.qbili.domain.model

import com.qbili.ui.navigation.Route
import org.junit.Assert.*
import org.junit.Test

class CourseTargetTest {
    @Test
    fun `course season and lesson ids resolve separately and build canonical links`() {
        assertEquals(CourseTarget(seasonId = 61), parseCourseTarget(" ss61 "))
        assertEquals(CourseTarget(episodeId = 790), parseCourseTarget("ep790"))
        assertEquals(CourseTarget(seasonId = 61), parseCourseTarget("https://www.bilibili.com/cheese/play/ss61?from=share"))
        assertEquals(CourseTarget(episodeId = 790), parseCourseTarget("//m.bilibili.com/cheese/play/ep790/"))
        assertEquals("course/ss/61", Route.buildCourse(CourseTarget(seasonId = 61)))
        assertEquals("course/ep/790", Route.buildCourse(CourseTarget(episodeId = 790)))
        assertEquals("https://www.bilibili.com/cheese/play/ep790", CourseTarget(episodeId = 790).url)
        assertFalse(CourseTarget().valid)
        assertFalse(CourseTarget(61, 790).valid)
    }

    @Test
    fun `rejects pgc video short link spoofed domain protocols credentials encoded paths and overflow`() {
        listOf("61", "ss0", "ep-1", "ss99999999999999999999999", "ss61\n", "SS61",
            "https://www.bilibili.com/bangumi/play/ss61", "https://www.bilibili.com/video/BV1xx411c7mD",
            "https://b23.tv/abc", "https://www.bilibili.com.evil/cheese/play/ss61",
            "https://evil@www.bilibili.com/cheese/play/ss61", "ftp://www.bilibili.com/cheese/play/ss61",
            "https://www.bilibili.com:81/cheese/play/ss61", "https://www.bilibili.com/cheese/play/%73s61",
            "https://www.bilibili.com/cheese/play/ss61/extra", "bilibili://cheese/season/61")
            .forEach { assertNull(it, parseCourseTarget(it)) }
    }

    @Test
    fun `history uses explicit course episode or trusted course link never deletion kid oid or cid`() {
        val base = WatchHistoryItem(999, "cheese", 42, "", 123, 1, "", "课", "", "", 100, 0, 0, false)
        assertEquals(HistoryOpenTarget.Unsupported, base.openTarget())
        assertEquals(HistoryOpenTarget.Course(CourseTarget(episodeId = 790)), base.copy(episodeId = 790).openTarget())
        assertEquals(HistoryOpenTarget.Course(CourseTarget(seasonId = 61)), base.copy(sourceUri = "https://www.bilibili.com/cheese/play/ss61").openTarget())
        assertEquals(HistoryOpenTarget.Course(CourseTarget(episodeId = 790)), base.copy(business = "future", sourceUri = "https://www.bilibili.com/cheese/play/ep790").openTarget())
        assertEquals("cheese_999", base.copy(episodeId = 790).deleteKey)
        assertTrue(HistoryContentType.COURSE.matches(base))
        assertFalse(HistoryContentType.OTHER.matches(base))
        assertEquals("课程", base.copy(episodeId = 790).contentLabel)
    }
}
