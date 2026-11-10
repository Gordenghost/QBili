package com.qbili.ui.screen.course

import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.model.DashResult
import com.qbili.domain.model.DashStream
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonPlayback
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class CourseSpeedViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val episode = CourseEpisode(790, 123, 1, "第一课", "", 80, "", "", true, true)
    private val detail = CourseDetail(61, "课程", "", "", 42, "讲师", "", "", emptyList(), "", "", "",
        listOf(episode, episode.copy(id = 791, cid = 124)), false)
    private fun playback(quality: Int = 80) = CoursePlayback(SeasonPlayback(PlayurlResult(dash = DashResult(video =
        listOf(80, 64).map { DashStream(id = it, url = "https://cdn.example/$it.mp4", codecid = 7) })), false, quality), "")

    @Test fun `stored speed loads and selection does not restart paused media`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val preferences = MutableStateFlow(1.25f)
            val applied = mutableListOf<Float>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality -> playback(quality) }, {}, scope,
                speeds = preferences, saveSpeed = { preferences.value = it }, applyPlaybackSpeed = applied::add)
            model.setTarget(CourseTarget(episodeId = 790))
            model.requestPlayback(false)
            val oldId = model.state.value.mediaId
            val oldStream = model.state.value.stream
            assertTrue(model.state.value.speedLoaded)
            assertEquals(1.25f, model.state.value.playbackSpeed)
            model.setPlaybackSpeed(1.5f)
            assertEquals(1.5f, preferences.value)
            assertEquals(1.5f, applied.last())
            assertEquals(oldId, model.state.value.mediaId)
            assertEquals(oldStream, model.state.value.stream)
            assertFalse(model.state.value.requested)
            assertFalse(model.state.value.savingSpeed)
        } finally { scope.cancel() }
    }

    @Test fun `selection waits for initial preferences and busy save ignores repeated selection`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val initial = CompletableDeferred<Float>()
            val save = CompletableDeferred<Unit>()
            var writes = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                speeds = flow { emit(initial.await()); emit(1f) }, saveSpeed = { writes++; save.await() })
            model.setPlaybackSpeed(1.5f)
            assertEquals(0, writes)
            initial.complete(1.25f)
            model.setPlaybackSpeed(1.5f)
            model.setPlaybackSpeed(2f)
            assertEquals(1, writes)
            assertEquals(1.5f, model.state.value.playbackSpeed)
            assertTrue(model.state.value.savingSpeed)
            save.complete(Unit)
            assertEquals(1.5f, model.state.value.playbackSpeed)
            assertFalse(model.state.value.savingSpeed)
        } finally { scope.cancel() }
    }

    @Test fun `flow emissions during save only update persisted fallback not optimistic selection`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val preferences = MutableStateFlow(1f)
            val save = CompletableDeferred<Unit>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                speeds = preferences, saveSpeed = { save.await() })
            model.setPlaybackSpeed(1.5f)
            preferences.value = 1.25f
            assertEquals(1.5f, model.state.value.playbackSpeed)
            save.complete(Unit)
            assertEquals(1.5f, model.state.value.playbackSpeed)
            preferences.value = 1.75f
            assertEquals(1.75f, model.state.value.playbackSpeed)
        } finally { scope.cancel() }
    }

    @Test fun `read failure defaults safely and write failure keeps current speed with explicit message`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                speeds = flow { throw IOException("read failed") }, saveSpeed = { throw IOException("write failed") })
            assertTrue(model.state.value.speedLoaded)
            assertEquals(1f, model.state.value.playbackSpeed)
            assertTrue(model.state.value.message!!.contains("读取失败"))
            model.consumeMessage()
            assertTrue(model.state.value.speedError!!.contains("读取失败"))
            model.setPlaybackSpeed(1.5f)
            assertEquals(1.5f, model.state.value.playbackSpeed)
            assertFalse(model.state.value.savingSpeed)
            assertTrue(model.state.value.message!!.contains("保存失败"))
            model.consumeMessage()
            assertTrue(model.state.value.speedError!!.contains("保存失败"))
        } finally { scope.cancel() }
    }

    @Test fun `boost ends at latest selected speed without resuming playback`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val applied = mutableListOf<Float>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                applyPlaybackSpeed = applied::add)
            model.setTarget(CourseTarget(episodeId = 790))
            model.requestPlayback(false)
            model.setSpeedBoosting(true)
            model.setPlaybackSpeed(1.5f)
            assertEquals(2f, applied.last())
            assertEquals(1.5f, model.state.value.playbackSpeed)
            model.setSpeedBoosting(false)
            assertEquals(1.5f, applied.last())
            assertFalse(model.state.value.requested)
        } finally { scope.cancel() }
    }

    @Test fun `quality lesson and lifecycle reset temporary boost but preserve stored speed`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val applied = mutableListOf<Float>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality -> playback(quality) }, {}, scope,
                applyPlaybackSpeed = applied::add)
            model.setTarget(CourseTarget(episodeId = 790))
            model.setPlaybackSpeed(1.75f)
            model.setSpeedBoosting(true)
            model.selectQuality(64)
            assertFalse(model.state.value.speedBoosting)
            assertEquals(1.75f, applied.last())
            model.setSpeedBoosting(true)
            model.selectAdjacentEpisode(1)
            assertFalse(model.state.value.speedBoosting)
            assertEquals(1.75f, model.state.value.playbackSpeed)
            model.setSpeedBoosting(true)
            model.onLeave()
            model.onEnter()
            assertEquals(1.75f, applied.last())
            assertFalse(model.state.value.requested)
            assertFalse(model.state.value.speedBoosting)
            model.close()
            val calls = applied.size
            model.setSpeedBoosting(false)
            model.setPlaybackSpeed(2f)
            assertEquals(calls, applied.size)
        } finally { scope.cancel() }
    }

    @Test fun `old non cancellable save cannot change new account or closed state`() = runBlocking {
        for (action in listOf("account", "close")) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val sessions = MutableStateFlow(session)
                val pending = CompletableDeferred<Unit>()
                val model = CourseViewModel(sessions, { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                    saveSpeed = { withContext(NonCancellable) { pending.await(); throw IOException("old save") } })
                model.setTarget(CourseTarget(episodeId = 790))
                model.setPlaybackSpeed(1.5f)
                if (action == "account") sessions.value = LoginCookies.EMPTY else model.close()
                pending.complete(Unit)
                assertFalse(model.state.value.savingSpeed)
                assertFalse(model.state.value.requested)
                assertNull(model.state.value.message)
                assertNull(model.state.value.speedError)
                assertEquals(1f, model.state.value.playbackSpeed)
            } finally { scope.cancel() }
        }
    }

    @Test fun `invalid speed or failed player adjustment does not persist or crash`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var writes = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                saveSpeed = { writes++ }, applyPlaybackSpeed = { if (it == 1.5f) error("player unavailable") })
            for (invalid in listOf(0f, 3f, Float.NaN, Float.POSITIVE_INFINITY)) model.setPlaybackSpeed(invalid)
            model.setPlaybackSpeed(1.5f)
            assertEquals(0, writes)
            assertEquals(1f, model.state.value.playbackSpeed)
            assertFalse(model.state.value.savingSpeed)
            assertTrue(model.state.value.message!!.contains("无法调整倍速"))
        } finally { scope.cancel() }
    }
}
