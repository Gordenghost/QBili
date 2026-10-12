package com.qbili.data.repository

import com.qbili.data.remote.api.AccountRelationApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.AccountBlacklistDto
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.BlacklistUserDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AccountBlacklistRepositoryTest {
    private val cookies = LoginCookies("session", "csrf", 42)
    private class Api(var responses: Map<Int, BiliResponse<AccountBlacklistDto>>) : AccountRelationApi {
        val requests = mutableListOf<Int>()
        override suspend fun blacklist(page: Int, pageSize: Int, version: Int): BiliResponse<AccountBlacklistDto> {
            assertEquals(50, pageSize)
            assertEquals(0, version)
            requests += page
            return requireNotNull(responses[page])
        }
    }

    @Test
    fun `完整分页读取并缓存下拉强制刷新可更新`() = runBlocking {
        val api = Api(mapOf(
            1 to BiliResponse(data = AccountBlacklistDto((1L..50).map(::BlacklistUserDto), 51)),
            2 to BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(99)), 51)),
        ))
        val sessions = MutableStateFlow(cookies)
        val repository = AccountBlacklistRepository(sessions, api)
        assertEquals((1L..50).toSet() + 99, repository.blockedAuthors())
        assertEquals((1L..50).toSet() + 99, repository.authors.first())
        repository.blockedAuthors()
        assertEquals(listOf(1, 2), api.requests)
        api.responses = mapOf(1 to BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(77)), 1)))
        assertEquals(setOf(77L), repository.blockedAuthors(forceRefresh = true))
        assertEquals(listOf(1, 2, 1), api.requests)
    }

    @Test
    fun `不同账号不共享黑名单退出后不返回旧名单`() = runBlocking {
        val api = Api(mapOf(1 to BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(9)), 1))))
        val sessions = MutableStateFlow(cookies)
        val repository = AccountBlacklistRepository(sessions, api)
        assertEquals(setOf(9L), repository.blockedAuthors())
        sessions.value = LoginCookies("new", "csrf", 43)
        assertEquals(emptySet<Long>(), repository.authors.first())
        api.responses = mapOf(1 to BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(88)), 1)))
        assertEquals(setOf(88L), repository.blockedAuthors())
        sessions.value = LoginCookies.EMPTY
        assertEquals(emptySet<Long>(), repository.blockedAuthors())
        assertEquals(emptySet<Long>(), repository.authors.first())
    }

    @Test
    fun `本机新增官方拉黑更新缓存但不重新拉取推荐`() = runBlocking {
        val api = Api(mapOf(1 to BiliResponse(data = AccountBlacklistDto(emptyList(), 0))))
        val repository = AccountBlacklistRepository(MutableStateFlow(cookies), api)
        repository.blockedAuthors()
        repository.recordBlocked(7)
        assertEquals(setOf(7L), repository.authors.first())
        assertEquals(setOf(7L), repository.blockedAuthors())
        assertEquals(listOf(1), api.requests)
    }

    @Test
    fun `错误缺失数据和重复分页不能作为同步完成`() = runBlocking {
        val responses = listOf(
            BiliResponse<AccountBlacklistDto>(code = -101, message = "未登录"),
            BiliResponse(data = AccountBlacklistDto()),
            BiliResponse(data = AccountBlacklistDto(null, 1)),
            BiliResponse(data = AccountBlacklistDto(emptyList(), 1)),
            BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(0)), 1)),
        )
        responses.forEach { response ->
            val repository = AccountBlacklistRepository(MutableStateFlow(cookies), Api(mapOf(1 to response)))
            try { repository.blockedAuthors(); fail("必须失败") } catch (_: Exception) { }
            assertTrue(repository.authors.first().isEmpty())
        }
        val api = Api((1..2).associateWith { BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(7)), 2)) })
        try {
            AccountBlacklistRepository(MutableStateFlow(cookies), api).blockedAuthors()
            fail("不能接受重复页")
        } catch (_: IllegalStateException) { }
    }

    @Test
    fun `同步中更换账号不发布旧名单`() = runBlocking {
        val sessions = MutableStateFlow(cookies)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val api = object : AccountRelationApi {
            override suspend fun blacklist(page: Int, pageSize: Int, version: Int): BiliResponse<AccountBlacklistDto> {
                started.complete(Unit)
                gate.await()
                return BiliResponse(data = AccountBlacklistDto(listOf(BlacklistUserDto(7)), 1))
            }
        }
        val repository = AccountBlacklistRepository(sessions, api)
        val request = async { runCatching { repository.blockedAuthors() } }
        started.await()
        sessions.value = LoginCookies.EMPTY
        gate.complete(Unit)
        assertTrue(request.await().isFailure)
        assertTrue(repository.authors.first().isEmpty())
    }

    @Test
    fun `官方响应正确解析长UID`() {
        val data = Json { ignoreUnknownKeys = true }.decodeFromString<AccountBlacklistDto>(
            """{"list":[{"mid":4000000000,"uname":"UP"}],"total":1}""")
        assertEquals(4_000_000_000L, data.list!!.single().mid)
    }
}
