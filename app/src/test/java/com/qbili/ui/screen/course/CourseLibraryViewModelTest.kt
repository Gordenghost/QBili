package com.qbili.ui.screen.course

import com.qbili.data.local.CourseProgressStorage
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.CourseProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class CourseLibraryViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val progress = CourseProgress(42, 61, 790, 123, "绘画课程", "", "老师", "基础第一课", 30_000, 80_000, 100)
    private class Store : CourseProgressStorage {
        override val entries = MutableStateFlow(emptyList<CourseProgress>())
        val actions = mutableListOf<Pair<Long, Long>>()
        var pending: CompletableDeferred<Unit>? = null
        var fail = false
        override suspend fun save(progress: CourseProgress) { entries.value += progress }
        override suspend fun delete(accountMid: Long, seasonId: Long) { remove(accountMid, seasonId) }
        override suspend fun clear(accountMid: Long) { remove(accountMid, 0) }
        private suspend fun remove(accountMid: Long, seasonId: Long) {
            actions += accountMid to seasonId
            pending?.let { withContext(NonCancellable) { it.await() } }
            if (fail) throw IllegalStateException("IO")
            entries.value = entries.value.filterNot { it.accountMid == accountMid && (seasonId == 0L || it.seasonId == seasonId) }
        }
    }

    @Test fun `search matches all words titles teachers lessons and explicit ids`() {
        assertEquals(listOf(progress), filterCourseRecords(listOf(progress), "绘画 老师 SS61 EP790"))
        assertEquals(listOf(progress), filterCourseRecords(listOf(progress), "  基础 \n 第一课  "))
        assertTrue(filterCourseRecords(listOf(progress), "绘画 不存在").isEmpty())
    }

    @Test fun `account and anonymous libraries isolated with query reset and stale clear rejected`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val store = Store()
            store.entries.value = listOf(progress, progress.copy(accountMid = 43), progress.copy(accountMid = 0))
            val model = CourseLibraryViewModel(sessions, store, scope)
            assertEquals(listOf(progress), model.state.value.entries)
            model.search("绘画")
            sessions.value = LoginCookies.EMPTY
            assertEquals("", model.state.value.query)
            assertEquals(0L, model.state.value.entries.single().accountMid)
            model.clear(42)
            assertTrue(store.actions.isEmpty())
            model.delete(0, 61)
            assertTrue(model.state.value.entries.isEmpty())
            assertEquals(2, store.entries.value.size)
        } finally { scope.cancel() }
    }

    @Test fun `clear removes all current account records even while searching and failure remains retryable`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = Store()
            store.entries.value = listOf(progress, progress.copy(seasonId = 62, title = "数学"), progress.copy(accountMid = 43))
            val model = CourseLibraryViewModel(MutableStateFlow(session), store, scope)
            model.search("绘画")
            assertEquals(1, model.state.value.filtered.size)
            store.fail = true
            model.clear(42)
            assertEquals(2, model.state.value.entries.size)
            assertFalse(model.state.value.busy)
            assertNotNull(model.state.value.message)
            store.fail = false
            model.clear(42)
            assertTrue(model.state.value.entries.isEmpty())
            assertEquals(43L, store.entries.value.single().accountMid)
        } finally { scope.cancel() }
    }

    @Test fun `non cancellable old deletion cannot change new account busy state or messages`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val store = Store()
            store.entries.value = listOf(progress, progress.copy(accountMid = 43))
            val pending = CompletableDeferred<Unit>()
            store.pending = pending
            val model = CourseLibraryViewModel(sessions, store, scope)
            model.clear(42)
            assertTrue(model.state.value.busy)
            model.clear(42)
            assertEquals(1, store.actions.size)
            sessions.value = LoginCookies("new", "csrf", 43)
            pending.complete(Unit)
            assertEquals(43L, model.state.value.accountMid)
            assertEquals(1, model.state.value.entries.size)
            assertFalse(model.state.value.busy)
            assertNull(model.state.value.message)
            assertEquals(listOf(42L to 0L), store.actions)
        } finally { scope.cancel() }
    }

    @Test fun `read failure is explicit and retry reloads current account`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var failed = true
            val store = object : CourseProgressStorage {
                override val entries = kotlinx.coroutines.flow.flow {
                    if (failed) throw java.io.IOException("read failed")
                    emit(listOf(progress, progress.copy(accountMid = 43)))
                }
                override suspend fun save(progress: CourseProgress) = Unit
                override suspend fun delete(accountMid: Long, seasonId: Long) = Unit
                override suspend fun clear(accountMid: Long) = Unit
            }
            val model = CourseLibraryViewModel(MutableStateFlow(session), store, scope)
            yield()
            assertNotNull(model.state.value.error)
            assertFalse(model.state.value.loading)
            failed = false
            model.retry()
            yield()
            assertNull(model.state.value.error)
            assertEquals(listOf(progress), model.state.value.entries)
        } finally { scope.cancel() }
    }
}
