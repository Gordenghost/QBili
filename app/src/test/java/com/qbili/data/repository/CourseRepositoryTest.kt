package com.qbili.data.repository

import com.qbili.data.remote.api.CourseApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.CourseResponse
import com.qbili.domain.model.CourseTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CourseRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val session = LoginCookies("session", "csrf", 42)
    private class Api : CourseApi {
        val requests = mutableListOf<List<Any?>>()
        var hook: () -> Unit = {}
        override suspend fun detail(cookies: String, seasonId: Long?, episodeId: Long?): CourseResponse {
            requests += listOf(cookies, seasonId, episodeId)
            hook()
            return Json.decodeFromString("""{"code":0,"data":{"season_id":61,"episodes":[{"id":790,"cid":123,"from":"pugv","play_way":1,"playable":true}]}}""")
        }
        override suspend fun playurl(cookies: String, episodeId: Long, cid: Long, quality: Int, fnval: Int, fnver: Int, fourk: Int): CourseResponse {
            requests += listOf(cookies, episodeId, cid, quality, fnval, fnver, fourk)
            hook()
            return Json.decodeFromString("""{"code":0,"data":{"quality":32,"is_preview":1,"durl":[{"order":1,"url":"https://cdn.example/video.mp4"}]}}""")
        }
    }

    @Test
    fun `explicit cookie season episode queries and guest do not mix identities`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        val repository = CourseRepository(api, sessions, json)
        val episode = repository.detail(session, CourseTarget(seasonId = 61)).episodes.single()
        repository.playurl(session, episode, 80)
        sessions.value = LoginCookies.EMPTY
        repository.detail(LoginCookies.EMPTY, CourseTarget(episodeId = 790))
        assertEquals(listOf("SESSDATA=session; bili_jct=csrf; DedeUserID=42", 61L, null), api.requests[0])
        assertEquals(listOf("SESSDATA=session; bili_jct=csrf; DedeUserID=42", 790L, 123L, 80, 4048, 0, 1), api.requests[1])
        assertEquals(listOf("", null, 790L), api.requests[2])
    }

    @Test
    fun `reject wrong resource unsupported lesson cookie injection and stale account`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        val repository = CourseRepository(api, sessions, json)
        try { repository.detail(session, CourseTarget(seasonId = 62)); fail() } catch (_: IllegalStateException) { }
        try { repository.detail(session, CourseTarget(episodeId = 791)); fail() } catch (_: IllegalStateException) { }
        val episode = repository.detail(session, CourseTarget(seasonId = 61)).episodes.single()
        val size = api.requests.size
        for (invalid in listOf(episode.copy(playable = false), episode.copy(video = false), episode.copy(cid = 0))) {
            try { repository.playurl(session, invalid, 80); fail() } catch (_: IllegalArgumentException) { }
        }
        val injected = session.copy(sessData = "session;inject=1")
        sessions.value = injected
        try { repository.detail(injected, CourseTarget(seasonId = 61)); fail() } catch (_: IllegalArgumentException) { }
        try { repository.detail(session, CourseTarget(seasonId = 61)); fail() } catch (_: IllegalStateException) { }
        assertEquals(size, api.requests.size)
    }

    @Test
    fun `account change during request rejects old returned catalogue`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        api.hook = { sessions.value = LoginCookies.EMPTY }
        try { CourseRepository(api, sessions, json).detail(session, CourseTarget(seasonId = 61)); fail() }
        catch (_: IllegalStateException) { }
    }
}
