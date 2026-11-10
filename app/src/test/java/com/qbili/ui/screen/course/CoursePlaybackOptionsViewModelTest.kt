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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class CoursePlaybackOptionsViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val episode = CourseEpisode(790, 123, 1, "第一课", "", 80, "", "", true, true)
    private val detail = CourseDetail(61, "课程", "", "", 42, "讲师", "", "", emptyList(), "", "", "",
        listOf(episode, episode.copy(id = 791, cid = 124, index = 2)), false)
    private fun playback(quality: Int = 80, preview: Boolean = false, progressAllowed: Boolean = !preview,
        tracks: List<Int> = listOf(80, 64, 32)) = CoursePlayback(SeasonPlayback(PlayurlResult(
        dash = DashResult(video = tracks.map { DashStream(id = it, url = "https://cdn.example/$quality/$it.mp4", codecid = 7) }),
        durationMillis = 80_000), preview, quality), "", progressAllowed)

    @Test fun `same or unavailable quality cannot send a request`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var requests = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, _ -> requests++; playback() }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            model.selectQuality(80)
            model.selectQuality(120)
            assertEquals(1, requests)
            assertTrue(model.state.value.message!!.contains("没有此清晰度"))
            assertFalse(model.state.value.changingQuality)
        } finally { scope.cancel() }
    }

    @Test fun `quality change preserves paused position saves old progress and gives source a new media identity`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            lateinit var model: CourseViewModel
            var stops = 0
            val requests = mutableListOf<Int>()
            val saved = mutableListOf<CourseProgress>()
            model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { captured, _, quality ->
                assertEquals(session, captured); requests += quality; playback(quality)
            }, { stops++ }, scope, saveProgress = saved::add,
                playbackSnapshot = { CoursePlaybackSnapshot(model.state.value.mediaId, 30_000, 80_000, true) })
            model.setTarget(CourseTarget(episodeId = 790))
            val oldId = model.state.value.mediaId
            val oldStops = stops
            model.requestPlayback(false)
            model.selectQuality(64)
            assertEquals(listOf(80, 64), requests)
            assertEquals(64, model.state.value.stream?.quality)
            assertEquals(790L, model.state.value.episode?.id)
            assertEquals(30_000L, model.state.value.startPositionMs)
            assertNotEquals(oldId, model.state.value.mediaId)
            assertEquals(oldStops + 1, stops)
            assertFalse(model.state.value.requested)
            assertFalse(model.state.value.changingQuality)
            assertEquals(30_000L, saved.single().positionMs)
        } finally { scope.cancel() }
    }

    @Test fun `pending quality keeps old playback running and commits latest position and pause intent`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<CoursePlayback>()
            lateinit var model: CourseViewModel
            var position = 10_000L
            var stops = 0
            var requests = 0
            model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality ->
                requests++; if (quality == 64) pending.await() else playback()
            }, { stops++ }, scope, playbackSnapshot = { CoursePlaybackSnapshot(model.state.value.mediaId, position, 80_000, true) })
            model.setTarget(CourseTarget(episodeId = 790))
            val oldStream = model.state.value.stream
            val oldStops = stops
            model.selectQuality(64)
            model.selectQuality(32)
            assertTrue(model.state.value.changingQuality)
            assertEquals(oldStream, model.state.value.stream)
            assertEquals(oldStops, stops)
            assertTrue(model.state.value.requested)
            assertEquals(2, requests)
            position = 25_000
            model.requestPlayback(false)
            pending.complete(playback(64))
            assertEquals(25_000L, model.state.value.startPositionMs)
            assertFalse(model.state.value.requested)
        } finally { scope.cancel() }
    }

    @Test fun `successful quality preserves active play intent and preference for manual next lesson`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val requests = mutableListOf<Pair<Long, Int>>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, lesson, quality ->
                requests += lesson.id to quality; playback(quality)
            }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            model.selectQuality(64)
            assertTrue(model.state.value.requested)
            model.selectAdjacentEpisode(1)
            assertEquals(listOf(790L to 80, 790L to 64, 791L to 64), requests)
            assertEquals(0L, model.state.value.startPositionMs)
            model.selectAdjacentEpisode(1)
            assertEquals(3, requests.size)
            model.selectAdjacentEpisode(-1)
            assertEquals(790L, model.state.value.episode?.id)
        } finally { scope.cancel() }
    }

    @Test fun `failed quality keeps original source and needs explicit retry without poisoning play errors`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var failure = true
            var requests = 0
            var stops = 0
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality ->
                requests++; if (quality == 64 && failure) error("网络失败") else playback(quality)
            }, { stops++ }, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            val oldState = model.state.value
            val oldStops = stops
            model.selectQuality(64)
            assertEquals(oldState.stream, model.state.value.stream)
            assertEquals(oldState.mediaId, model.state.value.mediaId)
            assertEquals(oldStops, stops)
            assertTrue(model.state.value.requested)
            assertNull(model.state.value.playError)
            assertTrue(model.state.value.message!!.contains("保留原画质"))
            assertFalse(model.state.value.changingQuality)
            assertEquals(2, requests)
            model.consumeMessage()
            failure = false
            model.selectQuality(64)
            assertEquals(64, model.state.value.stream?.quality)
            assertEquals(3, requests)
        } finally { scope.cancel() }
    }

    @Test fun `response fallback is not mislabeled as successful requested quality`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality ->
                if (quality == 64) playback(32, tracks = listOf(32)) else playback()
            }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            val oldId = model.state.value.mediaId
            model.selectQuality(64)
            assertEquals(80, model.state.value.stream?.quality)
            assertEquals(oldId, model.state.value.mediaId)
            assertTrue(model.state.value.message!!.contains("未返回所选画质"))
        } finally { scope.cancel() }
    }

    @Test fun `leave cancels even non cancellable quality response and returning stays paused`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<CoursePlayback>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality ->
                if (quality == 64) withContext(NonCancellable) { pending.await() } else playback()
            }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            val oldId = model.state.value.mediaId
            model.selectQuality(64)
            model.onLeave()
            model.onEnter()
            pending.complete(playback(64))
            assertEquals(80, model.state.value.stream?.quality)
            assertEquals(oldId, model.state.value.mediaId)
            assertFalse(model.state.value.requested)
            assertFalse(model.state.value.changingQuality)
        } finally { scope.cancel() }
    }

    @Test fun `lesson change invalidates old quality response and starts new lesson from zero`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<CoursePlayback>()
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, lesson, quality ->
                if (lesson.id == 790L && quality == 64) withContext(NonCancellable) { pending.await() } else playback()
            }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            model.selectQuality(64)
            model.selectAdjacentEpisode(1)
            val newId = model.state.value.mediaId
            pending.complete(playback(64))
            assertEquals(791L, model.state.value.episode?.id)
            assertEquals(80, model.state.value.stream?.quality)
            assertEquals(newId, model.state.value.mediaId)
            assertEquals(0L, model.state.value.startPositionMs)
            assertFalse(model.state.value.changingQuality)
        } finally { scope.cancel() }
    }

    @Test fun `account change refresh new target and close each invalidate pending quality`() = runBlocking {
        for (action in listOf("account", "refresh", "target", "close")) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val sessions = MutableStateFlow(session)
                val pending = CompletableDeferred<CoursePlayback>()
                val model = CourseViewModel(sessions, { _, target -> detail.copy(id = target.seasonId.takeIf { it > 0 } ?: 61) },
                    { _, _, quality -> if (quality == 64) withContext(NonCancellable) { pending.await() } else playback() }, {}, scope)
                model.setTarget(CourseTarget(episodeId = 790))
                model.selectQuality(64)
                when (action) {
                    "account" -> sessions.value = LoginCookies.EMPTY
                    "refresh" -> model.refresh()
                    "target" -> model.setTarget(CourseTarget(seasonId = 62))
                    "close" -> model.close()
                }
                pending.complete(playback(64))
                assertNull(action, model.state.value.stream)
                assertFalse(action, model.state.value.requested)
                assertFalse(action, model.state.value.changingQuality)
            } finally { scope.cancel() }
        }
    }

    @Test fun `preview and unknown permission quality changes start at zero and keep full records`() = runBlocking {
        for (newPlayback in listOf(playback(64, preview = true), playback(64, progressAllowed = false))) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                lateinit var model: CourseViewModel
                val saved = mutableListOf<CourseProgress>()
                model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality ->
                    if (quality == 64) newPlayback else playback()
                }, {}, scope, saveProgress = saved::add,
                    playbackSnapshot = { CoursePlaybackSnapshot(model.state.value.mediaId, 20_000, 80_000, true) })
                model.setTarget(CourseTarget(episodeId = 790))
                model.selectQuality(64)
                assertEquals(0L, model.state.value.startPositionMs)
                assertFalse(model.state.value.progressAllowed)
                assertTrue(model.state.value.message!!.contains("从头播放"))
                model.persistPlayback()
                assertEquals(20_000L, saved.single().positionMs)
            } finally { scope.cancel() }
        }
    }

    @Test fun `old preview cannot transfer position into newly allowed full lesson`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            lateinit var model: CourseViewModel
            model = CourseViewModel(MutableStateFlow(session), { _, _ -> detail }, { _, _, quality ->
                playback(quality, preview = quality == 80)
            }, {}, scope, playbackSnapshot = { CoursePlaybackSnapshot(model.state.value.mediaId, 20_000, 80_000, true) })
            model.setTarget(CourseTarget(episodeId = 790))
            model.selectQuality(64)
            assertTrue(model.state.value.progressAllowed)
            assertEquals(0L, model.state.value.startPositionMs)
        } finally { scope.cancel() }
    }

    @Test fun `adjacent inaccessible lesson leaves current source intact and does not skip`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var requests = 0
            val locked = detail.copy(episodes = listOf(episode, episode.copy(id = 791, playable = false, label = "需购买"), episode.copy(id = 792)))
            val model = CourseViewModel(MutableStateFlow(session), { _, _ -> locked }, { _, _, _ -> requests++; playback() }, {}, scope)
            model.setTarget(CourseTarget(episodeId = 790))
            val oldId = model.state.value.mediaId
            model.selectAdjacentEpisode(-1)
            model.selectAdjacentEpisode(1)
            assertEquals(1, requests)
            assertEquals(oldId, model.state.value.mediaId)
            assertEquals(790L, model.state.value.episode?.id)
            assertEquals("需购买", model.state.value.message)
        } finally { scope.cancel() }
    }
}
