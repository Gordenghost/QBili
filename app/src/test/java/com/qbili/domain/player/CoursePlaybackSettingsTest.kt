package com.qbili.domain.player

import org.junit.Assert.*
import org.junit.Test

class CoursePlaybackSettingsTest {
    @Test fun `speed choices and labels are bounded and do not depend on locale`() {
        assertEquals(listOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×"), coursePlaybackSpeeds.map(::formatCourseSpeed))
        coursePlaybackSpeeds.forEach { assertEquals(it, it.validCourseSpeed()) }
        for (speed in listOf(null, 0f, -1f, 3f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(1f, speed.validCourseSpeed())
        }
    }

    @Test fun `timer uses elapsed deadline rounds up and accounts for skipped ticks`() {
        val timer = CourseSleepTimer.start(10_000, 5)!!
        assertEquals(310_000L, timer.deadlineMs)
        assertEquals(300, timer.remainingSeconds(10_000))
        assertEquals(250, timer.remainingSeconds(60_000))
        assertEquals(1, timer.remainingSeconds(309_999))
        assertEquals(0, timer.remainingSeconds(310_000))
        assertEquals(0, timer.remainingSeconds(Long.MAX_VALUE))
        assertEquals(300, timer.remainingSeconds(0))
    }

    @Test fun `timer rejects invalid minutes negative clocks and deadline overflow`() {
        for (minutes in listOf(Int.MIN_VALUE, -1, 0, 7, 121, Int.MAX_VALUE)) assertNull(CourseSleepTimer.start(0, minutes))
        assertNull(CourseSleepTimer.start(-1, 5))
        assertNull(CourseSleepTimer.start(Long.MAX_VALUE, 5))
        assertEquals(7_200, CourseSleepTimer.start(0, 120)!!.remainingSeconds(0))
        assertEquals(0, CourseSleepTimer.start(0, 5)!!.remainingSeconds(-1))
        assertNotNull(CourseSleepTimer.start(Long.MAX_VALUE - 300_000, 5))
    }

    @Test fun `separate timer snapshots do not extend each other`() {
        val first = CourseSleepTimer.start(1_000, 5)!!
        val replacement = CourseSleepTimer.start(61_000, 15)!!
        assertEquals(0, first.remainingSeconds(301_000))
        assertEquals(660, replacement.remainingSeconds(301_000))
    }
}
