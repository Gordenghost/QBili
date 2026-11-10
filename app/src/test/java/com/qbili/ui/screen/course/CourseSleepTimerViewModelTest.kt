package com.qbili.ui.screen.course

import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CoursePlaybackSnapshot
import com.qbili.domain.model.CourseProgress
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.model.DashResult
import com.qbili.domain.model.DashStream
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonPlayback
import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class CourseSleepTimerViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val episode = CourseEpisode(790, 123, 1, "第一课", "", 80, "", "", true, true)
    private val detail = CourseDetail(61, "课程", "", "", 42, "讲师", "", "", emptyList(), "", "", "",
        listOf(episode, episode.copy(id = 791, cid = 124)), false)
    private fun playback(quality: Int = 80) = CoursePlayback(SeasonPlayback(PlayurlResult(dash = DashResult(video =
        listOf(80, 64).map { DashStream(id = it, url = "https://cdn.example/$it.mp4", codecid = 7) })), false, quality), "")

    private inner class Harness(loader: suspend (LoginCookies, CourseEpisode, Int) -> CoursePlayback = { _, _, quality -> playback(quality) },
        wait: (suspend () -> Unit)? = null, records: List<CourseProgress> = emptyList()) : Closeable {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val sessions = MutableStateFlow(session)
        val ticks = Channel<Unit>()
        var now = 1_000L
        var pauses = 0
        var seeks = 0
        val saved = mutableListOf<CourseProgress>()
        val model: CourseViewModel = CourseViewModel(sessions, { _, target -> detail.copy(id = target.seasonId.takeIf { it > 0 } ?: 61) }, loader, {}, scope,
            progressEntries = MutableStateFlow(records), saveProgress = saved::add,
            playbackSnapshot = { CoursePlaybackSnapshot(model.state.value.mediaId, 20_000, 80_000, true) },
            seekPlayback = { _, _ -> seeks++; true }, pausePlayback = { pauses++ }, timerClock = { now },
            waitForTimerTick = wait ?: { ticks.receive() })

        init { model.setTarget(CourseTarget(episodeId = 790)) }
        suspend fun tickAfter(milliseconds: Long) { now += milliseconds; ticks.send(Unit) }
        override fun close() { model.close(); scope.cancel(); ticks.cancel() }
    }

    @Test fun `expiry pauses once saves progress and preserves media identity`() = runBlocking {
        Harness().use { harness ->
            val id = harness.model.state.value.mediaId
            harness.model.setSleepTimer(5)
            assertEquals(300, harness.model.state.value.sleepRemainingSeconds)
            harness.tickAfter(300_000)
            assertEquals(1, harness.pauses)
            assertEquals(id, harness.model.state.value.mediaId)
            assertEquals(0, harness.model.state.value.sleepRemainingSeconds)
            assertFalse(harness.model.state.value.requested)
            assertEquals(20_000L, harness.saved.single().positionMs)
            assertTrue(harness.model.state.value.sleepTimerExpired)
            assertTrue(harness.model.state.value.message!!.contains("定时暂停"))
            harness.model.requestPlayback(true)
            assertTrue(harness.model.state.value.requested)
            assertFalse(harness.model.state.value.sleepTimerExpired)
            assertEquals(1, harness.pauses)
        }
    }

    @Test fun `elapsed countdown includes pause buffering and skipped ticks independent of speed`() = runBlocking {
        Harness().use { harness ->
            harness.model.setSleepTimer(5)
            harness.model.requestPlayback(false)
            harness.model.setBuffering(true)
            harness.model.setPlaybackSpeed(2f)
            harness.tickAfter(50_000)
            assertEquals(250, harness.model.state.value.sleepRemainingSeconds)
            assertEquals(0, harness.pauses)
            harness.tickAfter(250_000)
            assertEquals(1, harness.pauses)
            assertFalse(harness.model.state.value.requested)
        }
    }

    @Test fun `replacement cancels previous deadline and invalid choice preserves current timer`() = runBlocking {
        Harness().use { harness ->
            harness.model.setSleepTimer(5)
            harness.now += 60_000
            harness.model.setSleepTimer(15)
            harness.model.setSleepTimer(7)
            assertEquals(15, harness.model.state.value.sleepTimerMinutes)
            harness.tickAfter(240_000)
            assertEquals(660, harness.model.state.value.sleepRemainingSeconds)
            assertEquals(0, harness.pauses)
            harness.model.cancelSleepTimer()
            harness.now += 900_000
            harness.model.requestPlayback(true)
            assertEquals(0, harness.model.state.value.sleepRemainingSeconds)
            assertEquals(0, harness.model.state.value.sleepTimerMinutes)
            assertFalse(harness.model.state.value.sleepTimerExpired)
            assertEquals(0, harness.pauses)
        }
    }

    @Test fun `manual lesson and quality switches keep the same countdown`() = runBlocking {
        Harness().use { harness ->
            harness.model.setSleepTimer(5)
            harness.now += 60_000
            harness.model.selectAdjacentEpisode(1)
            harness.model.selectQuality(64)
            harness.tickAfter(0)
            assertEquals(240, harness.model.state.value.sleepRemainingSeconds)
            assertEquals(791L, harness.model.state.value.episode?.id)
            assertEquals(64, harness.model.state.value.stream?.quality)
            harness.tickAfter(240_000)
            assertFalse(harness.model.state.value.requested)
            assertEquals(1, harness.pauses)
        }
    }

    @Test fun `expiry during pending initial stream cannot autoplay late response`() = runBlocking {
        val pending = CompletableDeferred<CoursePlayback>()
        Harness(loader = { _, _, _ -> pending.await() }).use { harness ->
            assertTrue(harness.model.state.value.switching)
            harness.model.setSleepTimer(5)
            harness.tickAfter(300_000)
            pending.complete(playback())
            assertNotNull(harness.model.state.value.stream)
            assertFalse(harness.model.state.value.switching)
            assertFalse(harness.model.state.value.requested)
            assertEquals(1, harness.pauses)
        }
    }

    @Test fun `deadline is checked before late stream commit even without a timer tick`() = runBlocking {
        val pending = CompletableDeferred<CoursePlayback>()
        Harness(loader = { _, _, _ -> pending.await() }).use { harness ->
            harness.model.setSleepTimer(5)
            harness.now += 300_000
            pending.complete(playback())
            assertNotNull(harness.model.state.value.stream)
            assertFalse(harness.model.state.value.requested)
            assertEquals(0, harness.model.state.value.sleepRemainingSeconds)
            assertEquals(1, harness.pauses)
        }
    }

    @Test fun `deadline wins over pending quality commit without a timer tick`() = runBlocking {
        val pending = CompletableDeferred<CoursePlayback>()
        Harness(loader = { _, _, quality -> if (quality == 64) pending.await() else playback() }).use { harness ->
            harness.model.setSleepTimer(5)
            harness.model.selectQuality(64)
            harness.now += 300_000
            pending.complete(playback(64))
            assertEquals(64, harness.model.state.value.stream?.quality)
            assertFalse(harness.model.state.value.requested)
            assertEquals(1, harness.pauses)
        }
    }

    @Test fun `deadline wins over explicit play request and lesson switch until user plays again`() = runBlocking {
        for (action in listOf("play", "lesson")) Harness().use { harness ->
            harness.model.setSleepTimer(5)
            harness.now += 300_000
            if (action == "play") harness.model.requestPlayback(true) else harness.model.selectAdjacentEpisode(1)
            assertFalse(action, harness.model.state.value.requested)
            assertEquals(1, harness.pauses)
            harness.model.requestPlayback(true)
            assertTrue(harness.model.state.value.requested)
            assertEquals(1, harness.pauses)
        }
    }

    @Test fun `expired timer prevents remembered progress seek from starting playback`() = runBlocking {
        val progress = CourseProgress(42, 61, 790, 123, "课程", "", "讲师", "第一课", 30_000, 80_000, 100)
        Harness(records = listOf(progress)).use { harness ->
            assertTrue(harness.model.state.value.canResume)
            harness.model.setSleepTimer(5)
            harness.now += 300_000
            harness.model.resumeLast()
            assertEquals(0, harness.seeks)
            assertFalse(harness.model.state.value.requested)
        }
    }

    @Test fun `leaving refresh target account and close cancel timers without restoring playback`() = runBlocking {
        for (action in listOf("leave", "refresh", "target", "account", "close")) Harness().use { harness ->
            harness.model.setSleepTimer(5)
            when (action) {
                "leave" -> { harness.model.onLeave(); harness.model.onEnter() }
                "refresh" -> harness.model.refresh()
                "target" -> harness.model.setTarget(CourseTarget(seasonId = 62))
                "account" -> harness.sessions.value = LoginCookies.EMPTY
                "close" -> harness.model.close()
            }
            assertEquals(action, 0, harness.model.state.value.sleepRemainingSeconds)
            assertFalse(action, harness.model.state.value.requested)
            assertEquals(action, 0, harness.pauses)
        }
    }

    @Test fun `non cancellable old wait cannot pause or clear replacement timer after returning`() = runBlocking {
        val pending = CompletableDeferred<Unit>()
        lateinit var harness: Harness
        var waits = 0
        harness = Harness(wait = {
            if (waits++ == 0) withContext(NonCancellable) { pending.await() } else harness.ticks.receive()
        })
        harness.use {
            harness.model.setSleepTimer(5)
            harness.model.onLeave()
            harness.model.onEnter()
            harness.now += 300_000
            harness.model.setSleepTimer(15)
            pending.complete(Unit)
            assertEquals(900, harness.model.state.value.sleepRemainingSeconds)
            assertFalse(harness.model.state.value.sleepTimerExpired)
            assertEquals(0, harness.pauses)
            harness.tickAfter(900_000)
            assertEquals(1, harness.pauses)
        }
    }
}
