package com.qbili.domain.player

val coursePlaybackSpeeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
val courseSleepMinutes = listOf(5, 15, 30, 45, 60, 90, 120)

fun Float?.validCourseSpeed(): Float = takeIf { it in coursePlaybackSpeeds } ?: 1f
fun formatCourseSpeed(speed: Float): String = "${speed.validCourseSpeed().toString().removeSuffix(".0")}×"

class CourseSleepTimer private constructor(val deadlineMs: Long, val minutes: Int) {
    fun remainingSeconds(nowMs: Long): Int {
        if (nowMs < 0 || nowMs >= deadlineMs) return 0
        val remaining = (deadlineMs - nowMs).coerceAtMost(minutes * 60_000L)
        return ((remaining + 999) / 1_000).toInt()
    }

    companion object {
        fun start(nowMs: Long, minutes: Int): CourseSleepTimer? {
            if (minutes !in courseSleepMinutes || nowMs < 0 || nowMs > Long.MAX_VALUE - minutes * 60_000L) return null
            return CourseSleepTimer(nowMs + minutes * 60_000L, minutes)
        }
    }
}
