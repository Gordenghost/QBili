package com.qbili.domain.model

import org.junit.Assert.*
import org.junit.Test

class CourseProgressTest {
    private val episode = CourseEpisode(790, 123, 1, "课时", "", 80, "", "", true, true)
    private val detail = CourseDetail(61, "课程", "", "", 42, "讲师", "", "", emptyList(), "", "", "", listOf(episode), false)
    private val progress = CourseProgress(42, 61, 790, 123, "课程", "", "讲师", "课时", 30_000, 80_000, 100)

    @Test fun `resume requires exact course episode cid permission and valid interval`() {
        assertEquals(30_000L, progress.resumePosition(detail))
        assertEquals(0L, progress.copy(seasonId = 62).resumePosition(detail))
        assertEquals(0L, progress.copy(episodeId = 791).resumePosition(detail))
        assertEquals(0L, progress.copy(cid = 124).resumePosition(detail))
        assertEquals(0L, progress.copy(completed = true).resumePosition(detail))
        assertEquals(0L, progress.copy(positionMs = 4_999).resumePosition(detail))
        assertEquals(0L, progress.copy(positionMs = 75_000).resumePosition(detail))
        assertEquals(0L, progress.resumePosition(detail.copy(episodes = listOf(episode.copy(playable = false)))))
        assertEquals(0L, progress.resumePosition(detail.copy(episodes = listOf(episode.copy(video = false)))))
        assertEquals(0L, progress.resumePosition(detail.copy(episodes = listOf(episode.copy(durationSeconds = 30)))))
        assertEquals(30_000L, progress.resumePosition(detail.copy(episodes = listOf(episode.copy(durationSeconds = 0)))))
    }

    @Test fun `invalid records never enter storage and last update wins across isolated accounts`() {
        listOf(progress.copy(accountMid = -1), progress.copy(seasonId = 0), progress.copy(cid = 0),
            progress.copy(episodeId = 0), progress.copy(durationMs = 0), progress.copy(updatedAt = 0),
            progress.copy(positionMs = -1), progress.copy(positionMs = 80_001)).forEach {
            assertFalse(it.valid)
            assertTrue(emptyList<CourseProgress>().withCourseProgress(it).isEmpty())
        }
        val entries = listOf(progress, progress.copy(accountMid = 0), progress.copy(accountMid = 43))
        assertEquals(3, entries.withCourseProgress(progress.copy(updatedAt = 99)).size)
        val updated = entries.withCourseProgress(progress.copy(updatedAt = 101, episodeId = 791))
        assertEquals(3, updated.size)
        assertEquals(791L, updated.first().episodeId)
        assertEquals(790L, updated.first { it.accountMid == 43L }.episodeId)
    }

    @Test fun `at most one hundred courses sorted newest first with unique playback generations`() {
        var entries = emptyList<CourseProgress>()
        repeat(110) { entries = entries.withCourseProgress(progress.copy(seasonId = it + 1L, updatedAt = it + 1L)) }
        assertEquals(100, entries.size)
        assertEquals(110L, entries.first().seasonId)
        assertEquals(11L, entries.last().seasonId)
        assertNotEquals(courseMediaId(790, 1), courseMediaId(790, 2))
        assertNotEquals(courseMediaId(790, 1), courseMediaId(791, 1))
    }
}
