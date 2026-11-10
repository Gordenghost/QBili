package com.qbili.ui.screen.course

import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class CourseProgressViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val episode = CourseEpisode(790, 123, 1, "第一课", "", 80, "", "", true, true)
    private val detail = CourseDetail(61, "课程", "", "", 42, "讲师", "", "", emptyList(), "", "", "", listOf(episode, episode.copy(id = 791)), false)
    private val progress = CourseProgress(42, 61, 790, 123, "课程", "", "讲师", "第一课", 30_000, 80_000, 100)
    private fun playback(preview: Boolean = false) = CoursePlayback(SeasonPlayback(PlayurlResult(durl = listOf(
        DurlResult(1, 80_000, 0, "https://cdn.example/v.mp4", emptyList())
    )), preview, 32), "")

    @Test fun `catalogue offers resume without autoplay and explicit button starts remembered episode`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var requests = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> requests++; playback() }, {}, scope,
                progressEntries = MutableStateFlow(listOf(progress)))
            model.setTarget(CourseTarget(seasonId = 61))
            assertEquals(0, requests)
            assertFalse(model.state.value.requested)
            assertTrue(model.state.value.canResume)
            model.resumeLast()
            assertEquals(1, requests)
            assertEquals(790L, model.state.value.episode?.id)
            assertEquals(30_000L, model.state.value.startPositionMs)
            assertFalse(model.state.value.canResume)
        } finally { scope.cancel() }
    }

    @Test fun `explicit ep starts from zero and resume seeks current stream without another request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var requests = 0
            var seek: Pair<String, Long>? = null
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> requests++; playback() }, {}, scope,
                progressEntries = MutableStateFlow(listOf(progress)), seekPlayback = { id, position -> seek = id to position; true })
            model.setTarget(CourseTarget(episodeId = 790))
            assertEquals(0L, model.state.value.startPositionMs)
            val id = model.state.value.mediaId
            model.resumeLast()
            assertEquals(id to 30_000L, seek)
            assertEquals(1, requests)
            model.resumeLast()
            assertEquals(1, requests)
        } finally { scope.cancel() }
    }

    @Test fun `late record load offers resume once and subsequent saves do not move or restore button`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<List<CourseProgress>>()
            val entries = kotlinx.coroutines.flow.flow { emit(pending.await()); emit(listOf(progress.copy(positionMs = 40_000))) }
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope, progressEntries = entries)
            model.setTarget(CourseTarget(seasonId = 61))
            assertFalse(model.state.value.canResume)
            pending.complete(listOf(progress))
            assertEquals(30_000L, model.state.value.resumePositionMs)
            model.dismissResume()
            assertFalse(model.state.value.canResume)
        } finally { scope.cancel() }
    }

    @Test fun `preview resume starts at zero and never writes over full lesson`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val saved = mutableListOf<CourseProgress>()
            lateinit var model: CourseViewModel
            model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback(true) }, {}, scope,
                progressEntries = MutableStateFlow(listOf(progress)), saveProgress = { saved += it }, playbackSnapshot = {
                    CoursePlaybackSnapshot(model.state.value.mediaId, 15_000, 80_000, true)
                })
            model.setTarget(CourseTarget(seasonId = 61))
            model.resumeLast()
            assertTrue(model.state.value.preview)
            assertEquals(0L, model.state.value.startPositionMs)
            assertFalse(model.state.value.canResume)
            assertNotNull(model.state.value.message)
            model.persistPlayback()
            model.onLeave()
            model.close()
            assertTrue(saved.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun `actual playback saved on leave switch and close but stale media unplayed and invalid duration ignored`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val saved = mutableListOf<CourseProgress>()
            var snapshot: CoursePlaybackSnapshot? = null
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                saveProgress = { saved += it }, playbackSnapshot = { snapshot }, clock = { 200 })
            model.setTarget(CourseTarget(episodeId = 790))
            val id = model.state.value.mediaId
            listOf(CoursePlaybackSnapshot("other", 30_000, 80_000, true),
                CoursePlaybackSnapshot(id, 30_000, 80_000, false),
                CoursePlaybackSnapshot(id, 30_000, -1, true), CoursePlaybackSnapshot(id, 0, 80_000, true)).forEach {
                snapshot = it; model.persistPlayback()
            }
            assertTrue(saved.isEmpty())
            snapshot = CoursePlaybackSnapshot(id, 30_000, 80_000, true)
            model.onLeave()
            assertEquals(progress.copy(updatedAt = 200), saved.single())
            model.onEnter()
            assertFalse(model.state.value.requested)
            model.selectEpisode(791)
            val size = saved.size
            model.persistPlayback()
            assertEquals(size, saved.size)
            snapshot = CoursePlaybackSnapshot(model.state.value.mediaId, 80_000, 80_000, true, true)
            model.close()
            assertTrue(saved.last().completed)
            assertEquals(791L, saved.last().episodeId)
            val count = saved.size
            model.close(); model.persistPlayback()
            assertEquals(count, saved.size)
        } finally { scope.cancel() }
    }

    @Test fun `account changes never save old media as new user or leak resume records`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val saved = mutableListOf<CourseProgress>()
            var snapshot: CoursePlaybackSnapshot? = null
            val model = CourseViewModel(sessions, { _, _ -> detail }, { _, _, _ -> playback() }, {}, scope,
                progressEntries = MutableStateFlow(listOf(progress, progress.copy(accountMid = 0, positionMs = 10_000))),
                saveProgress = { saved += it }, playbackSnapshot = { snapshot })
            model.setTarget(CourseTarget(episodeId = 790))
            snapshot = CoursePlaybackSnapshot(model.state.value.mediaId, 50_000, 80_000, true)
            sessions.value = LoginCookies.EMPTY
            assertTrue(saved.isEmpty())
            assertEquals(0L, model.state.value.progress?.accountMid)
            assertEquals(10_000L, model.state.value.resumePositionMs)
            assertNull(model.state.value.stream)
            model.resumeLast()
            model.persistPlayback()
            assertTrue(saved.isEmpty())
            sessions.value = LoginCookies("new", "csrf", 43)
            assertFalse(model.state.value.canResume)
            assertNull(model.state.value.progress)
        } finally { scope.cancel() }
    }

    @Test fun `failed resume preserves offer and leaving during load prevents autoplay`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var fail = true
            val pending = CompletableDeferred<CoursePlayback>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ ->
                if (fail) throw IllegalStateException("权限不足") else pending.await()
            }, {}, scope, progressEntries = MutableStateFlow(listOf(progress)))
            model.setTarget(CourseTarget(seasonId = 61))
            model.resumeLast()
            assertNotNull(model.state.value.playError)
            assertTrue(model.state.value.canResume)
            fail = false
            model.resumeLast()
            model.onLeave(); model.onEnter()
            pending.complete(playback())
            assertEquals(30_000L, model.state.value.startPositionMs)
            assertFalse(model.state.value.requested)
        } finally { scope.cancel() }
    }

    @Test fun `removed cid revoked permission or completed lesson never offers resume`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var catalogue = detail.copy(episodes = listOf(episode.copy(playable = false)))
            val records = MutableStateFlow(listOf(progress))
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> catalogue }, { _, _, _ -> playback() }, {}, scope,
                progressEntries = records)
            model.setTarget(CourseTarget(seasonId = 61))
            assertFalse(model.state.value.canResume)
            catalogue = detail.copy(episodes = listOf(episode.copy(cid = 124)))
            model.refresh()
            assertFalse(model.state.value.canResume)
            catalogue = detail
            records.value = listOf(progress.copy(completed = true))
            model.refresh()
            assertFalse(model.state.value.canResume)
        } finally { scope.cancel() }
    }
}
