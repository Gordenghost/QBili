package com.qbili.domain.player

import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CoursePlaybackSnapshot
import com.qbili.domain.model.DashResult
import com.qbili.domain.model.DashStream
import com.qbili.domain.model.DurlResult
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonPlayback
import org.junit.Assert.*
import org.junit.Test

class CoursePlaybackControlsTest {
    private val episode = CourseEpisode(790, 123, 8, "第一课", "", 80, "", "", true, true)
    private val detail = CourseDetail(61, "课程", "", "", 42, "讲师", "", "", emptyList(), "", "", "",
        listOf(episode, episode.copy(id = 792, index = 3, playable = false), episode.copy(id = 791, index = 1)), false)

    @Test fun `adjacent follows catalogue order without skipping inaccessible lessons or wrapping`() {
        assertEquals(792L, detail.adjacentCourseEpisode(790, 1)?.id)
        assertFalse(detail.adjacentCourseEpisode(790, 1)!!.playable)
        assertEquals(792L, detail.adjacentCourseEpisode(791, -1)?.id)
        assertNull(detail.adjacentCourseEpisode(790, -1))
        assertNull(detail.adjacentCourseEpisode(791, 1))
        assertNull(detail.adjacentCourseEpisode(999, 1))
        assertNull(detail.adjacentCourseEpisode(790, 0))
        assertNull(detail.adjacentCourseEpisode(790, 2))
    }

    @Test fun `quality options use nonblank actual DASH tracks deduplicate and filter unsupported ids`() {
        val tracks = listOf(32, 80, 80, 64, 74, 999).map { DashStream(id = it, url = "https://cdn.example/$it", codecid = 7) } +
            DashStream(id = 120, url = "")
        val playback = CoursePlayback(SeasonPlayback(PlayurlResult(dash = DashResult(video = tracks)), false, 80), "")
        assertEquals(listOf(80, 74, 64, 32), playback.availableCourseQualities().map { it.qn })
    }

    @Test fun `DURL only offers current response quality and blank URLs offer nothing`() {
        val result = PlayurlResult(durl = listOf(DurlResult(1, 80_000, 0, "https://cdn.example/v.mp4", emptyList())))
        val playback = CoursePlayback(SeasonPlayback(result, true, 32), "")
        assertEquals(listOf(32), playback.availableCourseQualities().map { it.qn })
        assertTrue(playback.copy(playback = SeasonPlayback(result.copy(durl = emptyList()), true, 80)).availableCourseQualities().isEmpty())
        assertTrue(playback.copy(playback = SeasonPlayback(result, true, 999)).availableCourseQualities().isEmpty())
    }

    @Test fun `platform labels describe only actual qualities and selection shares the same label`() {
        val playback = CoursePlayback(SeasonPlayback(PlayurlResult(dash = DashResult(video = listOf(
            DashStream(id = 112, url = "https://cdn.example/112.mp4"), DashStream(id = 32, url = "https://cdn.example/32.mp4")
        ))), false, 112), "", qualityLabels = mapOf(112 to "1080P 高码率", 120 to "4K"))
        assertEquals(listOf(112, 32), playback.availableCourseQualities().map { it.qn })
        assertEquals("1080P 高码率", playback.selectCourseStream(112)?.label)
        assertEquals("画质 112", playback.copy(qualityLabels = emptyMap()).selectCourseStream(112)?.label)
    }

    @Test fun `quality position requires matching actually played complete-permission media and valid bounds`() {
        val snapshot = CoursePlaybackSnapshot("course:790:1", 30_000, 80_000, true)
        assertEquals(30_000L, snapshot.courseQualityPosition(snapshot.mediaId, true, true, 80_000))
        assertEquals(30_000L, snapshot.courseQualityPosition(snapshot.mediaId, true, true, 0))
        assertEquals(0L, snapshot.courseQualityPosition("course:790:2", true, true, 80_000))
        assertEquals(0L, snapshot.courseQualityPosition(snapshot.mediaId, false, true, 80_000))
        assertEquals(0L, snapshot.courseQualityPosition(snapshot.mediaId, true, false, 80_000))
        assertEquals(0L, snapshot.courseQualityPosition(snapshot.mediaId, true, true, 25_000))
        for (invalid in listOf(snapshot.copy(played = false), snapshot.copy(completed = true),
            snapshot.copy(positionMs = -1), snapshot.copy(positionMs = 80_000), snapshot.copy(durationMs = 0))) {
            assertEquals(0L, invalid.courseQualityPosition(snapshot.mediaId, true, true, 80_000))
        }
    }
}
