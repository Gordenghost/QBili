package com.qbili.data.repository

import com.qbili.data.remote.api.AccountRelationApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.dto.requireData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AccountBlacklistRepository(
    private val sessions: StateFlow<LoginCookies>,
    private val api: AccountRelationApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private class Snapshot(val session: LoginCookies, val authors: Set<Long>, val loadedAt: Long)
    private val snapshot = MutableStateFlow<Snapshot?>(null)
    private val mutex = Mutex()
    val authors = combine(sessions, snapshot) { cookies, cached ->
        if (cookies.isLoggedIn && cookies == cached?.session) cached.authors else emptySet()
    }.distinctUntilChanged()

    suspend fun blockedAuthors(forceRefresh: Boolean = false): Set<Long> = mutex.withLock {
        val cookies = sessions.value
        if (!cookies.isLoggedIn) return@withLock emptySet()
        snapshot.value?.takeIf { !forceRefresh && it.session == cookies && now() - it.loadedAt < 5 * 60_000 }
            ?.let { return@withLock it.authors }
        val authors = mutableSetOf<Long>()
        var page = 1
        do {
            val data = api.blacklist(page, 50, 0).requireData()
            val total = requireNotNull(data.total) { "黑名单未返回总数，请重新加载" }
            require(total >= 0) { "黑名单总数无效" }
            val users = data.list ?: if (total == 0) emptyList() else error("黑名单未返回用户列表")
            require(users.all { it.mid > 0 }) { "黑名单用户数据不完整" }
            val previousSize = authors.size
            authors += users.map { it.mid }
            check(sessions.value == cookies) { "登录账号已变化，请重新加载黑名单" }
            if (authors.size >= total) break
            check(authors.size > previousSize) { "黑名单分页不完整，请重新加载" }
            page++
        } while (true)
        snapshot.value = Snapshot(cookies, authors.toSet(), now())
        authors.toSet()
    }

    fun recordBlocked(mid: Long) {
        if (mid <= 0) return
        val cookies = sessions.value
        val cached = snapshot.value ?: return
        if (cached.session == cookies) snapshot.value = Snapshot(cookies, cached.authors + mid, cached.loadedAt)
    }
}
