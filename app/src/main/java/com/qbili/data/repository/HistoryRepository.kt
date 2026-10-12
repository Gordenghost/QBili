package com.qbili.data.repository

import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.data.remote.api.HistoryApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.HistoryCursor
import com.qbili.domain.model.HistoryPage
import com.qbili.domain.model.VideoProgress
import com.qbili.domain.model.WatchHistoryItem
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HistoryRepository(private val api: HistoryApi, private val sessions: StateFlow<LoginCookies>,
    private val deviceCookies: () -> Map<String, String> = { emptyMap() },
    private val reportAllowed: suspend (VideoProgress) -> Boolean = { true },
    private val removeLocal: suspend (Long, Long) -> Unit = { _, _ -> },
    private val clearLocal: suspend (Long) -> Unit = {},
) {
    private val writes = Mutex()
    suspend fun seasonIdForEpisode(episodeId: Long): Long {
        require(episodeId > 0)
        return api.episodeSeason(episodeId).requireResult().seasonId.also { require(it > 0) { "未找到对应番剧" } }
    }
    suspend fun page(session: LoginCookies, cursor: HistoryCursor = HistoryCursor()): HistoryPage {
        validate(session)
        val data = api.list(session.header(), cursor.max, cursor.viewAt, cursor.business, "all", 20).requireData()
        validate(session)
        val raw = requireNotNull(data.list) { "观看历史返回不完整，可能触发风控，请重试" }
        val items = raw.map { item ->
            val resource = requireNotNull(item.history) { "观看历史缺少资源信息" }
            val id = item.kid.takeIf { it > 0 } ?: resource.oid
            require(id > 0 && resource.business.isNotBlank()) { "观看历史资源无效" }
            WatchHistoryItem(
                id = id, business = resource.business, aid = resource.oid, bvid = resource.bvid,
                cid = resource.cid, page = resource.page.coerceAtLeast(1), part = resource.part,
                title = stripHtml(item.title), cover = normalizeUrl(item.cover.ifBlank { item.covers.firstOrNull().orEmpty() }),
                author = item.authorName, viewedAt = item.viewAt, positionMs = item.progress.coerceAtLeast(0) * 1000,
                durationSeconds = item.duration.coerceAtLeast(0), completed = item.progress == -1L,
                episodeId = resource.epid,
            )
        }
        val next = data.cursor?.let { HistoryCursor(it.max, it.viewAt, it.business) }
            ?: raw.lastOrNull()?.let { HistoryCursor(it.history?.oid ?: 0, it.viewAt, it.history?.business.orEmpty()) }
            ?: HistoryCursor()
        if (raw.isNotEmpty()) require(next != cursor && next.viewAt > 0) { "观看历史分页游标无效，请刷新" }
        return HistoryPage(items, next, raw.isNotEmpty())
    }

    suspend fun delete(session: LoginCookies, item: WatchHistoryItem) = writes.withLock {
        validate(session)
        api.delete(session.header(), item.deleteKey, requireNotNull(session.biliJct), "jsonp").requireSuccess()
        validate(session)
        if (item.business == "archive") removeLocal(requireNotNull(session.mid), item.aid)
    }

    suspend fun clear(session: LoginCookies) = writes.withLock {
        validate(session)
        api.clear(session.header(), requireNotNull(session.biliJct), "jsonp").requireSuccess()
        validate(session)
        clearLocal(requireNotNull(session.mid))
    }

    suspend fun report(session: LoginCookies, progress: VideoProgress) = writes.withLock {
        validate(session)
        require(progress.accountMid == session.mid && progress.bvid.isNotBlank() && progress.cid > 0)
        if (!reportAllowed(progress)) return@withLock
        api.heartbeat(session.header(), progress.bvid, progress.cid,
            if (progress.completed) -1 else progress.positionMs / 1000, 3, requireNotNull(session.biliJct)).requireSuccess()
    }

    private fun validate(session: LoginCookies) {
        check(session.isLoggedIn && sessions.value == session) { "登录状态已变化，请重新打开观看历史" }
    }

    private fun LoginCookies.header(): String {
        val values = listOf("SESSDATA" to sessData, "bili_jct" to biliJct, "DedeUserID" to mid?.toString()) +
            deviceCookies().filterKeys { it in setOf("buvid3", "buvid4") }.toList()
        require(values.all { (_, value) -> value == null || value.none { it == '\r' || it == '\n' || it == ';' } })
        return values.mapNotNull { (name, value) -> value?.let { "$name=$it" } }.joinToString("; ")
    }
}
