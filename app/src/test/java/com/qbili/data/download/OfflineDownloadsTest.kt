package com.qbili.data.download

import com.qbili.data.local.OfflineDownloadStore
import com.qbili.data.remote.api.OfflineVideoApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.repository.*
import com.qbili.domain.download.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineDownloadsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val session = LoginCookies("session", "csrf", 1)
    private val page = DownloadPage(3, 1, "P1", 60)
    private val video = DownloadVideo(2, "视频", "UP", listOf(page))
    private val option = DownloadOption(80, "1080P", 30280, "https://a.bilivideo.com/video", "https://a.bilivideo.com/audio")
    private fun source(sessions: MutableStateFlow<LoginCookies>): OfflineVideoRepository {
        val api = object : OfflineVideoApi {
            override suspend fun view(cookie: String, aid: Long) = wrappedOffline(offlineVideoFixture())
            override suspend fun playurl(cookie: String, aid: Long, cid: Long, quality: Int, fnval: Int, fnver: Int, fourk: Int) = wrappedOffline(offlinePlayFixture())
        }
        return OfflineVideoRepository(api, sessions, offlineTestJson)
    }
    private class FakeTransfer(private val wait: Boolean = false) : OfflineTrackTransfer {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        override fun cancel() { cancelled = true }
        override suspend fun download(url: String, file: File, track: DownloadTrack, allowed: () -> Boolean, progress: suspend (DownloadTrack) -> Unit) {
            check(allowed()); started.complete(Unit)
            if (wait) delay(60_000)
            file.writeBytes(ByteArray(10)); progress(DownloadTrack(10, 10, complete = true))
        }
    }
    @Test fun onlyServiceStartsWorkAndBothTracksMustComplete() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, FakeTransfer())
            val id = engine.enqueue(session, video, page, option)
            engine.start(0)
            assertEquals(DownloadStatus.QUEUED, engine.tasks.value.single().status)
            engine.start(engine.activateService())
            withTimeout(2000) { engine.tasks.first { it.single().status == DownloadStatus.COMPLETE } }
            assertEquals(10L, engine.playable(id).first.length())
        } finally { scope.cancel() }
    }
    @Test fun pauseAndDeleteBlockOldWorker() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session); val transfer = FakeTransfer(true)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, transfer)
            val id = engine.enqueue(session, video, page, option)
            engine.start(engine.activateService()); transfer.started.await(); engine.pause(id)
            assertEquals(DownloadStatus.PAUSED, engine.tasks.value.single().status)
            assertTrue(transfer.cancelled)
            engine.delete(id, 1)
            assertTrue(engine.tasks.value.isEmpty())
            assertEquals(0L, engine.usage(1))
        } finally { scope.cancel() }
    }
    @Test fun accountChangeStopsWorkAndKeepsAccountFilesSeparate() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session); val transfer = FakeTransfer(true)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, transfer)
            val id = engine.enqueue(session, video, page, option)
            engine.start(engine.activateService()); transfer.started.await()
            sessions.value = LoginCookies("other", "csrf", 2)
            withTimeout(2000) { engine.tasks.first { it.single().status == DownloadStatus.PAUSED } }
            try { engine.delete(id, 1); fail() } catch (_: IllegalStateException) { }
            try { engine.resume(id); fail() } catch (_: IllegalStateException) { }
            engine.enqueue(sessions.value, video, page, option); engine.clear(2)
            assertEquals(id, engine.tasks.value.single().id)
        } finally { scope.cancel() }
    }
    @Test fun noNetworkPausesWithoutAutomaticRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { false }, FakeTransfer())
            engine.enqueue(session, video, page, option)
            val token = engine.activateService(); engine.start(token)
            withTimeout(2000) { engine.tasks.first { it.single().status == DownloadStatus.PAUSED } }
            engine.start(token)
            assertEquals(DownloadStatus.PAUSED, engine.tasks.value.single().status)
        } finally { scope.cancel() }
    }
    @Test fun oldServiceCannotStopReplacementAndDuplicatesAreRejected() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, FakeTransfer())
            engine.enqueue(session, video, page, option)
            val old = engine.activateService(); engine.activateService(); engine.stopService(old)
            assertEquals(DownloadStatus.QUEUED, engine.tasks.value.single().status)
            try { engine.enqueue(session, video, page, option); fail() } catch (_: IllegalStateException) { }
        } finally { scope.cancel() }
    }
    @Test fun storageWriteFailureStopsQueueWithoutCrashing() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> failures.add(error) })
        try {
            val sessions = MutableStateFlow(session); val root = temporary.newFolder()
            val engine = OfflineDownloads(OfflineDownloadStore(root), sessions, source(sessions), scope, { true }, FakeTransfer())
            engine.enqueue(session, video, page, option)
            File(root, "index.tmp").mkdir()
            engine.start(engine.activateService())
            assertEquals(DownloadStatus.PAUSED, engine.tasks.value.single().status)
            assertNotNull(engine.notice.value)
            assertTrue(failures.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun brokenIndexDoesNotCrashWhenAccountChanges() = runBlocking {
        val failures = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined + CoroutineExceptionHandler { _, error -> failures.add(error) })
        try {
            val root = temporary.newFolder(); File(root, "index.json").writeText("broken")
            val sessions = MutableStateFlow(session)
            val engine = OfflineDownloads(OfflineDownloadStore(root), sessions, source(sessions), scope, { true }, FakeTransfer())
            sessions.value = LoginCookies.EMPTY
            assertFalse(engine.loaded.value)
            assertTrue(failures.isEmpty())
            assertEquals("broken", File(root, "index.json").readText())
        } finally { scope.cancel() }
    }
    @Test fun firstEnqueueWaitsForIndexRatherThanFailing() = runBlocking {
        val pendingWork = mutableListOf<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { pendingWork.add(block) }
        }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val sessions = MutableStateFlow(session)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, FakeTransfer())
            val pending = async(Dispatchers.Unconfined) { engine.enqueue(session, video, page, option) }
            assertFalse(pending.isCompleted)
            while (pendingWork.isNotEmpty()) pendingWork.removeAt(0).run()
            assertEquals(engine.tasks.value.single().id, pending.await())
            assertEquals(DownloadStatus.QUEUED, engine.tasks.value.single().status)
        } finally { scope.cancel(); while (pendingWork.isNotEmpty()) pendingWork.removeAt(0).run() }
    }
    @Test fun delayedAccountObserverDoesNotPauseNewAccountTask() = runBlocking {
        val work = mutableListOf<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) { work.add(block) }
        }
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val sessions = MutableStateFlow(session)
            val engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, FakeTransfer())
            while (work.isNotEmpty()) work.removeAt(0).run()
            val old = engine.enqueue(session, video, page, option)
            sessions.value = LoginCookies("other", "csrf", 2)
            val fresh = engine.enqueue(sessions.value, video, page, option)
            while (work.isNotEmpty()) work.removeAt(0).run()
            assertEquals(DownloadStatus.PAUSED, engine.tasks.value.first { it.id == old }.status)
            assertEquals(DownloadStatus.QUEUED, engine.tasks.value.first { it.id == fresh }.status)
        } finally { scope.cancel(); while (work.isNotEmpty()) work.removeAt(0).run() }
    }
    @Test fun oldServiceProgressCannotWriteAfterReplacement() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val sessions = MutableStateFlow(session)
            lateinit var engine: OfflineDownloads
            val transfer = object : OfflineTrackTransfer {
                override fun cancel() { }
                override suspend fun download(url: String, file: File, track: DownloadTrack, allowed: () -> Boolean, progress: suspend (DownloadTrack) -> Unit) {
                    engine.activateService()
                    file.writeBytes(ByteArray(10))
                    progress(DownloadTrack(10, 10, complete = true))
                }
            }
            engine = OfflineDownloads(OfflineDownloadStore(temporary.newFolder()), sessions, source(sessions), scope, { true }, transfer)
            engine.enqueue(session, video, page, option)
            engine.start(engine.activateService())
            assertEquals(DownloadStatus.PAUSED, engine.tasks.value.single().status)
            assertEquals(0L, engine.tasks.value.single().video.bytes)
            assertFalse(engine.tasks.value.single().video.complete)
        } finally { scope.cancel() }
    }
}
