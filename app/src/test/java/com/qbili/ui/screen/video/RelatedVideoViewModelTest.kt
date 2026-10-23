package com.qbili.ui.screen.video

import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.VideoItem
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

class RelatedVideoViewModelTest {
    private val session = LoginCookies("session", "csrf", 42)
    private val target = RelatedVideoTarget(2, "BV1xx411c7mD")
    private fun video(aid: Long) = VideoItem(aid, "", title = "视频$aid", cover = "")

    @Test
    fun `一次加载相同目标不重复刷新失败保留旧列表重试同一视频`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var fails = false
            val targets = mutableListOf<Long>()
            val viewModel = RelatedVideoViewModel(MutableStateFlow(session), { _, aid, _ ->
                targets += aid
                if (fails) throw IllegalStateException("失败")
                listOf(video(3))
            }, scope)
            viewModel.setTarget(target); viewModel.setTarget(target)
            assertEquals(listOf(2L), targets)
            assertEquals(3L, viewModel.uiState.value.videos.single().aid)
            fails = true; viewModel.refresh()
            assertEquals(3L, viewModel.uiState.value.videos.single().aid)
            assertNotNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.loading)
            fails = false; viewModel.refresh()
            assertEquals(listOf(2L, 2L, 2L), targets)
            assertNull(viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.loaded)
        } finally { scope.cancel() }
    }

    @Test
    fun `视频切换不合作旧请求不能覆盖新列表退出清空`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<List<VideoItem>>()
            val viewModel = RelatedVideoViewModel(MutableStateFlow(session), { _, aid, _ ->
                if (aid == 2L) withContext(NonCancellable) { pending.await() } else listOf(video(5))
            }, scope)
            viewModel.setTarget(target)
            viewModel.setTarget(RelatedVideoTarget(4, ""))
            pending.complete(listOf(video(3)))
            assertEquals(5L, viewModel.uiState.value.videos.single().aid)
            viewModel.setTarget(null)
            assertTrue(viewModel.uiState.value.videos.isEmpty())
            assertNull(viewModel.uiState.value.target)
        } finally { scope.cancel() }
    }

    @Test
    fun `账号切换清空旧列表并重新加载同一视频`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val pending = CompletableDeferred<List<VideoItem>>()
            val accounts = mutableListOf<Long?>()
            val viewModel = RelatedVideoViewModel(sessions, { cookies, _, _ ->
                accounts += cookies.mid
                if (cookies.mid == 42L) withContext(NonCancellable) { pending.await() } else listOf(video(5))
            }, scope)
            viewModel.setTarget(target)
            sessions.value = session.copy(mid = 43)
            pending.complete(listOf(video(3)))
            assertEquals(listOf(42L, 43L), accounts)
            assertEquals(5L, viewModel.uiState.value.videos.single().aid)
        } finally { scope.cancel() }
    }

    @Test
    fun `正在加载重复刷新不发请求失败不自动重试`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val pending = CompletableDeferred<List<VideoItem>>()
            val viewModel = RelatedVideoViewModel(MutableStateFlow(session), { _, _, _ -> calls++; pending.await() }, scope)
            viewModel.setTarget(target); viewModel.refresh()
            assertEquals(1, calls)
            pending.completeExceptionally(IllegalStateException("失败"))
            assertEquals(1, calls)
            assertNotNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.loading)
        } finally { scope.cancel() }
    }

    @Test
    fun `真实空结果已完成但不伪装成加载失败`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val viewModel = RelatedVideoViewModel(MutableStateFlow(LoginCookies.EMPTY), { _, _, _ -> emptyList() }, scope)
            viewModel.setTarget(target)
            assertTrue(viewModel.uiState.value.loaded)
            assertTrue(viewModel.uiState.value.videos.isEmpty())
            assertNull(viewModel.uiState.value.error)
        } finally { scope.cancel() }
    }
}
