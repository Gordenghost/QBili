package com.qbili.data.repository

import com.qbili.data.remote.api.HistoryApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.HistoryDataDto
import com.qbili.data.remote.dto.HistoryItemDto
import com.qbili.data.remote.dto.HistoryResourceDto
import com.qbili.data.remote.dto.HistoryWriteResponse
import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonDetailDto
import com.qbili.domain.model.HistoryCursor
import com.qbili.domain.model.VideoProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HistoryRepositoryTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val item = HistoryItemDto(kid = 99, title = "视频", cover = "//i0.hdslb.com/test.jpg",
        history = HistoryResourceDto(1, "BV1", 20, 2, "第二部分", "archive"), viewAt = 100, progress = 32, duration = 60)
    private class Api : HistoryApi {
        var response = BiliResponse(data = HistoryDataDto(emptyList()))
        var write = HistoryWriteResponse(0)
        var hook: suspend () -> Unit = {}
        val requests = mutableListOf<HistoryCursor>()
        val headers = mutableListOf<String>()
        var deleted: String? = null
        var playedTime: Long? = null
        var clearCalls = 0
        var heartbeatHook: suspend () -> Unit = {}
        val actions = mutableListOf<String>()
        override suspend fun list(cookies: String, max: Long, viewAt: Long, business: String, type: String, pageSize: Int): BiliResponse<HistoryDataDto> {
            headers += cookies
            assertEquals("all", type)
            assertEquals(20, pageSize)
            requests += HistoryCursor(max, viewAt, business)
            hook()
            return response
        }
        override suspend fun delete(cookies: String, kid: String, csrf: String, jsonp: String): HistoryWriteResponse {
            actions += "delete"
            headers += cookies
            assertEquals("csrf", csrf)
            assertEquals("jsonp", jsonp)
            deleted = kid
            return write
        }
        override suspend fun clear(cookies: String, csrf: String, jsonp: String): HistoryWriteResponse {
            clearCalls++
            assertEquals("csrf", csrf)
            return write
        }
        override suspend fun heartbeat(cookies: String, bvid: String, cid: Long, playedTime: Long, type: Int, csrf: String): HistoryWriteResponse {
            actions += "report"
            heartbeatHook()
            assertEquals(3, type)
            assertEquals(20L, cid)
            assertEquals("BV1", bvid)
            assertEquals("csrf", csrf)
            this.playedTime = playedTime
            return write
        }
        override suspend fun episodeSeason(episodeId: Long) = PgcResponse(result = SeasonDetailDto(seasonId = 10))
    }

    private suspend fun expectFailure(block: suspend () -> Unit) {
        try { block(); fail("应拒绝无效响应或身份") } catch (error: Exception) { assertTrue(error.message != null) }
    }

    @Test
    fun `真实结构解析分P秒级进度及Kid删除键`() = runBlocking {
        val json = Json { ignoreUnknownKeys = true }
        val api = Api()
        api.response = json.decodeFromString<BiliResponse<HistoryDataDto>>("""{"code":0,"data":{"list":[{"kid":99,"title":"视频","cover":"//i0.hdslb.com/test.jpg","history":{"oid":1,"bvid":"BV1","cid":20,"page":2,"part":"第二部分","business":"archive"},"view_at":100,"progress":32,"duration":60}],"cursor":{"max":1,"view_at":100,"business":"archive"}}}""")
        val repository = HistoryRepository(api, MutableStateFlow(session))
        val page = repository.page(session)
        val video = page.items.single()
        assertEquals(32_000L, video.resume.positionMs)
        assertEquals(2, video.resume.page)
        assertEquals("https://i0.hdslb.com/test.jpg", video.cover)
        assertTrue(page.hasMore)
        repository.delete(session, video)
        assertEquals("archive_99", api.deleted)
        assertTrue(api.headers.all { it == "SESSDATA=session; bili_jct=csrf; DedeUserID=42" })
        repository.clear(session)
        assertEquals(1, api.clearCalls)
    }

    @Test
    fun `短页继续分页直到空页避免漏掉历史`() = runBlocking {
        val api = Api()
        api.response = BiliResponse(data = HistoryDataDto(listOf(item)))
        val repository = HistoryRepository(api, MutableStateFlow(session))
        val first = repository.page(session)
        assertEquals(HistoryCursor(1, 100, "archive"), first.cursor)
        assertTrue(first.hasMore)
        api.response = BiliResponse(data = HistoryDataDto(emptyList()))
        val second = repository.page(session, first.cursor)
        assertFalse(second.hasMore)
        assertEquals(listOf(HistoryCursor(), first.cursor), api.requests)
    }

    @Test
    fun `缺数据无效资源重复游标和风控均不当成功`() = runBlocking {
        val api = Api()
        val repository = HistoryRepository(api, MutableStateFlow(session))
        api.response = BiliResponse(data = HistoryDataDto())
        expectFailure { repository.page(session) }
        api.response = BiliResponse(data = HistoryDataDto(listOf(HistoryItemDto())))
        expectFailure { repository.page(session) }
        api.response = BiliResponse(data = HistoryDataDto(listOf(item)))
        expectFailure { repository.page(session, HistoryCursor(1, 100, "archive")) }
        api.response = BiliResponse(code = -101, message = "未登录")
        expectFailure { repository.page(session) }
        api.write = Json.decodeFromString("""{"code":0,"result":{"v_voucher":"risk"}}""")
        expectFailure { repository.clear(session) }
        assertEquals(1, api.clearCalls)
    }

    @Test
    fun `切换账号后丢弃旧响应且不发出旧账号写请求`() = runBlocking {
        val api = Api()
        val sessions = MutableStateFlow(session)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        api.hook = { started.complete(Unit); finish.await() }
        val repository = HistoryRepository(api, sessions)
        val request = async { expectFailure { repository.page(session) } }
        started.await()
        sessions.value = LoginCookies("new", "newcsrf", 43)
        finish.complete(Unit)
        request.await()
        expectFailure { repository.clear(session) }
        assertEquals(0, api.clearCalls)
        expectFailure { repository.page(LoginCookies.EMPTY) }
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `上传使用当前账号真实分P进度并以负一标记看完`() = runBlocking {
        val api = Api()
        val repository = HistoryRepository(api, MutableStateFlow(session))
        val progress = VideoProgress(42, 1, "BV1", 20, 2, "分P", "标题", "", "", 32_999, 60_000, 100)
        repository.report(session, progress)
        assertEquals(32L, api.playedTime)
        repository.report(session, progress.copy(completed = true))
        assertEquals(-1L, api.playedTime)
        expectFailure { repository.report(session, progress.copy(accountMid = 43)) }
        assertEquals(-1L, api.playedTime)
    }

    @Test
    fun `删除等待已有上传完成并阻止排队的旧进度重新上传`() = runBlocking {
        val api = Api()
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var saved: VideoProgress? = VideoProgress(42, 1, "BV1", 20, 2, "分P", "标题", "", "", 32_000, 60_000, 100)
        val progress = requireNotNull(saved)
        api.heartbeatHook = { started.complete(Unit); finish.await() }
        val repository = HistoryRepository(api, MutableStateFlow(session), reportAllowed = { it == saved },
            removeLocal = { mid, aid -> assertEquals(42L, mid); assertEquals(1L, aid); saved = null })
        val reporting = async { repository.report(session, progress) }
        started.await()
        api.response = BiliResponse(data = HistoryDataDto(listOf(item)))
        val record = repository.page(session).items.single()
        val deleting = async { repository.delete(session, record) }
        assertEquals(listOf("report"), api.actions)
        finish.complete(Unit)
        reporting.await()
        deleting.await()
        repository.report(session, progress)
        assertEquals(listOf("report", "delete"), api.actions)
        assertEquals(null, saved)
    }
}
