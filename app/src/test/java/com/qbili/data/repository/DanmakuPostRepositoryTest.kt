package com.qbili.data.repository

import com.qbili.data.remote.api.DanmakuPostApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.DanmakuPostResponse
import com.qbili.domain.model.DanmakuMode
import com.qbili.domain.model.DanmakuPost
import com.qbili.domain.model.DanmakuTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DanmakuPostRepositoryTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val post = DanmakuPost(DanmakuTarget(2, "BV1xx411c7mD", 62131, 60_000), "你好 + &", 12_345,
        DanmakuMode.BOTTOM, 0xFFD700)

    private class Api : DanmakuPostApi {
        var calls = 0
        var hook: () -> Unit = {}
        override suspend fun send(cookies: String, webLocation: String, queryCsrf: String, type: Int,
            cid: Long, bvid: String, message: String, progress: Long, color: Int, fontSize: Int, pool: Int,
            mode: Int, nonce: Long, csrf: String): DanmakuPostResponse {
            calls++
            assertEquals("SESSDATA=session; bili_jct=csrf; DedeUserID=42", cookies)
            assertEquals("1315873", webLocation)
            assertEquals("csrf", queryCsrf)
            assertEquals(queryCsrf, csrf)
            assertEquals(1, type)
            assertEquals(62131L, cid)
            assertEquals("BV1xx411c7mD", bvid)
            assertEquals("你好 + &", message)
            assertEquals(12_345L, progress)
            assertEquals(0xFFD700, color)
            assertEquals(25, fontSize)
            assertEquals(0, pool)
            assertEquals(4, mode)
            assertEquals(123456L, nonce)
            hook()
            return DanmakuPostResponse(0, data = buildJsonObject { put("dmid", 77) })
        }
    }

    @Test
    fun `固定视频正文账号和CSRF发送请求`() = runBlocking {
        val api = Api()
        assertEquals(77L, DanmakuPostRepository(api, MutableStateFlow(session)) { 123456 }.send(session, post))
        assertEquals(1, api.calls)
    }

    @Test
    fun `登录改变无CSRF或Cookie注入时禁止发送`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        val repository = DanmakuPostRepository(api, sessions) { 123456 }
        for (invalid in listOf(LoginCookies.EMPTY, session.copy(biliJct = null), session.copy(mid = 43),
            session.copy(sessData = "session; injected=1"))) {
            if (invalid.mid != 43L) sessions.value = invalid
            try { repository.send(invalid, post); fail("不应发送") } catch (_: IllegalStateException) {
            } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, api.calls)
    }

    @Test
    fun `响应返回前账号改变不影响新账号`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        api.hook = { sessions.value = session.copy(mid = 43) }
        try { DanmakuPostRepository(api, sessions) { 123456 }.send(session, post); fail("旧账号响应应失效") }
        catch (_: IllegalStateException) { }
        assertEquals(1, api.calls)
    }
}
