package com.qbili.ui.screen.video

import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.domain.model.DanmakuPost
import com.qbili.domain.model.DanmakuTarget
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

class DanmakuComposerViewModelTest {
    private val target = DanmakuTarget(2, "BV1xx411c7mD", 62131, 60_000)
    private val session = LoginCookies("session", "csrf", 42)

    @Test
    fun `发送锁阻止重复提交成功后清草稿并限制冷却`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<Long>()
            val requests = mutableListOf<DanmakuPost>()
            var time = 10_000L
            val viewModel = DanmakuComposerViewModel(MutableStateFlow(session), { _, post ->
                requests += post; pending.await()
            }, scope, { time })
            viewModel.setTarget(target); viewModel.open(12_000); viewModel.edit("内容")
            viewModel.send(); viewModel.send(); viewModel.edit("忙碌时的修改"); viewModel.close()
            assertTrue(viewModel.uiState.value.busy)
            assertTrue(viewModel.uiState.value.visible)
            assertEquals("内容", viewModel.uiState.value.draft)
            assertEquals(1, requests.size)
            pending.complete(42)
            assertFalse(viewModel.uiState.value.busy)
            assertFalse(viewModel.uiState.value.visible)
            assertEquals("", viewModel.uiState.value.draft)
            assertNotNull(viewModel.uiState.value.notice)
            viewModel.open(20_000); viewModel.edit("新弹幕"); viewModel.send()
            assertEquals(1, requests.size)
            assertNotNull(viewModel.uiState.value.error)
            time += 5_000; viewModel.send()
            assertEquals(2, requests.size)
            assertEquals(12_000L, requests.first().progressMillis)
        } finally { scope.cancel() }
    }

    @Test
    fun `失败保留草稿不自动重试关闭重开保留当前分P草稿`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val viewModel = DanmakuComposerViewModel(MutableStateFlow(session), { _, _ ->
                calls++; throw IllegalStateException("网络超时，请勿立即重复发送")
            }, scope)
            viewModel.setTarget(target); viewModel.open(12_000); viewModel.edit("草稿"); viewModel.send()
            assertEquals(1, calls)
            assertEquals("草稿", viewModel.uiState.value.draft)
            assertNotNull(viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.busy)
            viewModel.close(); viewModel.open(30_000)
            assertEquals("草稿", viewModel.uiState.value.draft)
            assertEquals(30_000L, viewModel.uiState.value.progressMillis)
        } finally { scope.cancel() }
    }

    @Test
    fun `切分P后不合作的旧成功响应不能清空新草稿`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val pending = CompletableDeferred<Long>()
            val viewModel = DanmakuComposerViewModel(MutableStateFlow(session), { _, _ ->
                withContext(NonCancellable) { pending.await() }
            }, scope)
            viewModel.setTarget(target); viewModel.open(0); viewModel.edit("旧草稿"); viewModel.send()
            viewModel.setTarget(target.copy(cid = 88)); viewModel.open(0); viewModel.edit("新草稿")
            pending.complete(42)
            assertEquals("新草稿", viewModel.uiState.value.draft)
            assertTrue(viewModel.uiState.value.visible)
            assertNull(viewModel.uiState.value.notice)
            assertFalse(viewModel.uiState.value.busy)
        } finally { scope.cancel() }
    }

    @Test
    fun `账号切换清除旧草稿和请求未登录提示而不打开输入框`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val pending = CompletableDeferred<Long>()
            val viewModel = DanmakuComposerViewModel(sessions, { _, _ ->
                withContext(NonCancellable) { pending.await() }
            }, scope)
            viewModel.setTarget(target); viewModel.open(0); viewModel.edit("旧账号"); viewModel.send()
            sessions.value = session.copy(mid = 43)
            viewModel.open(0); viewModel.edit("新账号")
            pending.complete(42)
            assertEquals("新账号", viewModel.uiState.value.draft)
            assertNull(viewModel.uiState.value.notice)
            sessions.value = LoginCookies.EMPTY
            assertEquals("", viewModel.uiState.value.draft)
            viewModel.open(0)
            assertFalse(viewModel.uiState.value.visible)
            assertNotNull(viewModel.uiState.value.notice)
        } finally { scope.cancel() }
    }

    @Test
    fun `空白超长非法弹幕不调用网络`() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var calls = 0
            val viewModel = DanmakuComposerViewModel(MutableStateFlow(session), { _, _ -> calls++; 42 }, scope)
            viewModel.setTarget(target); viewModel.open(0)
            for (draft in listOf("", "x".repeat(101), "a\nb")) {
                viewModel.edit(draft); viewModel.send(); assertNotNull(viewModel.uiState.value.error)
            }
            assertEquals(0, calls)
        } finally { scope.cancel() }
    }
}
