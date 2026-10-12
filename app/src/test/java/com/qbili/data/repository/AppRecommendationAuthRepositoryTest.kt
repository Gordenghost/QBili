package com.qbili.data.repository

import com.qbili.core.AppRecommendationAuthException
import com.qbili.data.local.AppAccessToken
import com.qbili.data.remote.api.AppAccountApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.AppTokenStatusDto
import com.qbili.data.remote.dto.BiliResponse
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppRecommendationAuthRepositoryTest {
    private val cookies = LoginCookies("session", "csrf", 42)
    private class Api(var result: BiliResponse<AppTokenStatusDto> = BiliResponse(data = AppTokenStatusDto(42, 3600))) : AppAccountApi {
        val requests = mutableListOf<String>()
        override suspend fun tokenInfo(accessKey: String): BiliResponse<AppTokenStatusDto> {
            requests += accessKey
            return result
        }
    }

    @Test
    fun `已登录没有移动端凭据必须提示而不是返回匿名key`() = runBlocking {
        val api = Api()
        val repository = AppRecommendationAuthRepository(api, { cookies }, { null })
        try { repository.accessKey(); fail("不能降级游客") } catch (error: AppRecommendationAuthException) {
            assertTrue(error.message!!.contains("短信登录"))
        }
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `未登录不使用残留移动端凭据`() = runBlocking {
        val api = Api()
        val repository = AppRecommendationAuthRepository(api, { LoginCookies.EMPTY }, { AppAccessToken("old", 42) })
        assertNull(repository.accessKey())
        assertTrue(api.requests.isEmpty())
    }

    @Test
    fun `旧版无mid凭据必须服务端验证同一账号并缓存到期再校验`() = runBlocking {
        val api = Api()
        var now = 1000L
        val repository = AppRecommendationAuthRepository(api, { cookies }, { AppAccessToken("legacy") }, { now })
        assertEquals("legacy", repository.accessKey())
        assertEquals("legacy", repository.accessKey())
        assertEquals(listOf("legacy"), api.requests)
        now += 5 * 60_000
        assertEquals("legacy", repository.accessKey())
        assertEquals(listOf("legacy", "legacy"), api.requests)
    }

    @Test
    fun `错误账号过期和无效凭据都不能伪装匿名推荐`() = runBlocking {
        val api = Api()
        val badTokens = listOf(AppAccessToken("wrong", 43), AppAccessToken("expired", 42, 10))
        badTokens.forEach { token ->
            val repository = AppRecommendationAuthRepository(api, { cookies }, { token }, { 100 })
            try { repository.accessKey(); fail("必须拒绝") } catch (_: AppRecommendationAuthException) { }
        }
        assertTrue(api.requests.isEmpty())
        listOf(
            BiliResponse<AppTokenStatusDto>(code = -101, message = "账号未登录"),
            BiliResponse(data = AppTokenStatusDto(43, 3600)),
            BiliResponse(data = AppTokenStatusDto(42, 0)),
            BiliResponse(data = AppTokenStatusDto(0)),
        ).forEach { response ->
            api.result = response
            val repository = AppRecommendationAuthRepository(api, { cookies }, { AppAccessToken("key", 42) })
            try { repository.accessKey(); fail("必须拒绝") } catch (_: AppRecommendationAuthException) { }
        }
    }

    @Test
    fun `校验期间更换账号不能向推荐接口返回旧token`() = runBlocking {
        var session = cookies
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val api = object : AppAccountApi {
            override suspend fun tokenInfo(accessKey: String): BiliResponse<AppTokenStatusDto> {
                started.complete(Unit)
                gate.await()
                return BiliResponse(data = AppTokenStatusDto(42, 3600))
            }
        }
        val repository = AppRecommendationAuthRepository(api, { session }, { AppAccessToken("key") })
        val request = async { runCatching { repository.accessKey() } }
        started.await()
        session = LoginCookies("other", "csrf", 99)
        gate.complete(Unit)
        assertTrue(request.await().exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `换token或同账号换Cookie需要重新校验`() = runBlocking {
        val api = Api()
        var session = cookies
        var token = AppAccessToken("first", 42)
        val repository = AppRecommendationAuthRepository(api, { session }, { token })
        repository.accessKey()
        token = AppAccessToken("second", 42)
        repository.accessKey()
        session = cookies.copy(sessData = "new-session")
        repository.accessKey()
        assertEquals(listOf("first", "second", "second"), api.requests)
        assertFalse(token.toString().contains("second"))
    }
}
