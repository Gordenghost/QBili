package com.qbili.data.repository

import com.qbili.data.remote.api.RelatedVideoApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.RelatedVideoResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class RelatedVideoRepositoryTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val json = Json { ignoreUnknownKeys = true }
    private class Api : RelatedVideoApi {
        val requests = mutableListOf<Triple<String, String?, Long?>>()
        var hook: () -> Unit = {}
        override suspend fun related(cookies: String, bvid: String?, aid: Long?): RelatedVideoResponse {
            requests += Triple(cookies, bvid, aid)
            hook()
            return Json.decodeFromString("""{"code":0,"data":[]}""")
        }
    }

    @Test
    fun `登录使用固定Cookie游客不带旧账号且BV与AV二选一`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        val repository = RelatedVideoRepository(api, sessions, json)
        repository.load(session, 2, "BV1xx411c7mD")
        sessions.value = LoginCookies.EMPTY
        repository.load(LoginCookies.EMPTY, 2, "")
        assertEquals(Triple("SESSDATA=session; bili_jct=csrf; DedeUserID=42", "BV1xx411c7mD", null), api.requests[0])
        assertEquals(Triple("", null, 2L), api.requests[1])
    }

    @Test
    fun `旧账号Cookie注入和无效视频拒绝请求`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        val repository = RelatedVideoRepository(api, sessions, json)
        try { repository.load(session.copy(mid = 43), 2, ""); fail("旧会话应失效") } catch (_: IllegalStateException) { }
        val invalid = session.copy(sessData = "session; injected=1")
        sessions.value = invalid
        try { repository.load(invalid, 2, ""); fail("禁止Cookie注入") } catch (_: IllegalArgumentException) { }
        try { repository.load(invalid, 0, ""); fail("拒绝无效AV") } catch (_: IllegalArgumentException) { }
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `响应期间切账号拒绝旧推荐结果`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        api.hook = { sessions.value = session.copy(mid = 43) }
        try { RelatedVideoRepository(api, sessions, json).load(session, 2, ""); fail("旧结果不得显示") }
        catch (_: IllegalStateException) { }
        assertEquals(1, api.requests.size)
    }
}
