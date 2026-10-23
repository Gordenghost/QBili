package com.qbili.ui.screen.course

import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.model.DurlResult
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonPlayback
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class CourseViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val episode = CourseEpisode(790, 123, 1, "第一课", "", 76, "全集试看", "试看提示", true, true)
    private fun detail(id: Long = 61) = CourseDetail(id, "课程$id", "", "", 42, "讲师", "", "", emptyList(), "", "", "", listOf(episode, episode.copy(id = 791)), false)
    private fun playback() = CoursePlayback(SeasonPlayback(PlayurlResult(durl = listOf(
        DurlResult(order = 1, length = 76_000, size = 0, url = "https://cdn.example/v.mp4", backupUrls = emptyList())
    )), false, 32), "试看提示")

    @Test
    fun `season only loads catalogue and explicit ep plays once`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var loads = 0
            var plays = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> loads++; detail() },
                { _, _, _ -> plays++; playback() }, {}, scope)
            model.setTarget(CourseTarget(seasonId = 61))
            assertNull(model.state.value.stream)
            assertEquals(0, plays)
            model.setTarget(CourseTarget(episodeId = 790))
            model.setTarget(CourseTarget(episodeId = 790))
            assertEquals(2, loads)
            assertEquals(1, plays)
            assertTrue(model.state.value.requested)
            assertNotNull(model.state.value.stream)
        } finally { scope.cancel() }
    }

    @Test
    fun `non cancellable old course cannot replace new catalogue and close clears playback`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<CourseDetail>()
            var stops = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, target ->
                if (target.seasonId == 61L) withContext(NonCancellable) { pending.await() } else detail(62)
            }, { _, _, _ -> playback() }, { stops++ }, scope)
            model.setTarget(CourseTarget(seasonId = 61))
            model.setTarget(CourseTarget(seasonId = 62))
            pending.complete(detail())
            assertEquals(62L, model.state.value.detail?.id)
            model.selectEpisode(790)
            model.close()
            model.selectEpisode(791)
            assertNull(model.state.value.stream)
            assertFalse(model.state.value.requested)
            assertTrue(stops >= 3)
        } finally { scope.cancel() }
    }

    @Test
    fun `account change cancels old stream and reloads details without autoplay`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val pending = CompletableDeferred<CoursePlayback>()
            var loads = 0
            val model = CourseViewModel(sessions, { _, _ -> loads++; detail() },
                { _, _, _ -> withContext(NonCancellable) { pending.await() } }, {}, scope)
            model.setTarget(CourseTarget(seasonId = 61))
            model.selectEpisode(790)
            sessions.value = LoginCookies.EMPTY
            pending.complete(playback())
            assertEquals(2, loads)
            assertNull(model.state.value.stream)
            assertNull(model.state.value.episode)
            assertFalse(model.state.value.requested)
        } finally { scope.cancel() }
    }

    @Test
    fun `leaving while stream loads keeps playback paused even after return`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<CoursePlayback>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail() },
                { _, _, _ -> pending.await() }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            model.onLeave()
            model.onEnter()
            pending.complete(playback())
            assertNotNull(model.state.value.stream)
            assertFalse(model.state.value.requested)
        } finally { scope.cancel() }
    }

    @Test
    fun `refresh failure retains details but stops old playback with explicit retry`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var fails = false
            val model = CourseViewModel(MutableStateFlow(session), { _, _ ->
                if (fails) error("failure") else detail()
            }, { _, _, _ -> playback() }, {}, scope)
            model.setTarget(CourseTarget(seasonId = 61))
            model.selectEpisode(790)
            fails = true
            model.refresh()
            assertNotNull(model.state.value.detail)
            assertNotNull(model.state.value.error)
            assertNull(model.state.value.stream)
            assertFalse(model.state.value.requested)
            fails = false
            model.refresh()
            assertNull(model.state.value.error)
            assertNull(model.state.value.stream)
        } finally { scope.cancel() }
    }

    @Test
    fun `play errors do not auto retry and busy click does not duplicate requests`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<CoursePlayback>()
            var requests = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail() },
                { _, _, _ -> requests++; pending.await() }, {}, scope)
            model.setTarget(CourseTarget(seasonId = 61))
            model.selectEpisode(790)
            model.selectEpisode(791)
            assertEquals(1, requests)
            pending.completeExceptionally(IllegalStateException("权限不足"))
            assertNotNull(model.state.value.playError)
            assertFalse(model.state.value.switching)
            assertFalse(model.state.value.requested)
            assertEquals(1, requests)
        } finally { scope.cancel() }
    }
}
