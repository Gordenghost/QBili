package com.qbili.data.download

import com.qbili.core.friendlyMessage
import com.qbili.data.local.OfflineDownloadStore
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.repository.OfflineVideoRepository
import com.qbili.domain.download.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class OfflineDownloads(
    val store: OfflineDownloadStore,
    private val sessions: StateFlow<LoginCookies>,
    private val source: OfflineVideoRepository,
    private val scope: CoroutineScope,
    private val networkAllowed: () -> Boolean,
    private val transfer: OfflineTrackTransfer = OfflineTransfer(),
) {
    private val mutex = Mutex()
    private val ready = CompletableDeferred<Unit>()
    private val _tasks = MutableStateFlow<List<OfflineDownload>>(emptyList())
    val tasks = _tasks.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded = _loaded.asStateFlow()
    @Volatile private var activeId: String? = null
    private var scheduler: Job? = null
    private var worker: Job? = null
    @Volatile private var workerSession: LoginCookies? = null
    @Volatile private var capturedSession = sessions.value
    @Volatile private var serviceGeneration = 0L
    @Volatile private var serviceEnabled = false

    init {
        scope.launch {
            try { mutex.withLock { _tasks.value = store.read(); _loaded.value = true }; ready.complete(Unit) }
            catch (error: CancellationException) { ready.cancel(error); throw error }
            catch (error: Exception) {
                val failure = IllegalStateException("离线缓存索引读取失败，原记录已保留并停止操作", error)
                ready.completeExceptionally(failure); _notice.value = failure.message
            }
        }
        scope.launch {
            sessions.collect { mutex.withLock { synchronizeSession() } }
        }
    }

    fun account(): Long = sessions.value.let { if (it.isLoggedIn) it.mid ?: 0 else 0 }
    fun showNotice(message: String) { _notice.value = message }
    fun consumeNotice() { _notice.value = null }

    suspend fun enqueue(session: LoginCookies, video: DownloadVideo, page: DownloadPage, option: DownloadOption): String {
        ready.await()
        return mutex.withLock {
        synchronizeSession()
        check(_loaded.value) { "缓存索引尚未就绪" }
        check(sessions.value == session) { "账号已变化，请重新选择" }
        check(video.pages.any { it == page } && option.quality > 0 && option.audioId > 0) { "缓存分 P 或画质无效" }
        check(_tasks.value.size < 200) { "最多保留 200 条缓存任务，请先删除旧任务" }
        val task = OfflineDownload(accountOf(session), video.aid, page.cid, option.quality, video.title, page.title,
            video.author, page.durationSeconds, option.label, option.audioId, System.currentTimeMillis())
        check(_tasks.value.none { it.id == task.id }) { "此分 P 的画质已在缓存列表中，请在列表中继续或删除" }
        commit(_tasks.value + task)
        task.id
        }
    }

    suspend fun resume(id: String) = mutex.withLock {
        synchronizeSession()
        check(_loaded.value) { "缓存索引尚未就绪" }
        val task = owned(id)
        check(task.status != DownloadStatus.COMPLETE) { "缓存已经完成" }
        check(task.status !in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED)) { "任务已经在下载队列中" }
        commit(_tasks.value.map { if (it.id == id) it.copy(status = DownloadStatus.QUEUED, error = null) else it })
    }

    suspend fun pause(id: String) {
        mutex.withLock {
            val task = owned(id)
            if (task.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                commit(_tasks.value.map { if (it.id == id) it.copy(status = DownloadStatus.PAUSED) else it })
        }
        if (activeId == id) { transfer.cancel(); worker?.cancelAndJoin() }
    }

    suspend fun pauseAll() {
        mutex.withLock {
            if (_loaded.value) try {
                commit(_tasks.value.map { if (it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                    it.copy(status = DownloadStatus.PAUSED) else it })
            } catch (error: Exception) { _notice.value = "暂停状态保存失败：${error.friendlyMessage()}" }
        }
        transfer.cancel()
        worker?.cancel()
    }

    suspend fun delete(id: String, account: Long) {
        check(account == account()) { "账号已变化，请重新操作" }
        pause(id)
        mutex.withLock {
            check(account == account()) { "账号已变化，请重新操作" }
            val task = owned(id)
            store.delete(task)
            commit(_tasks.value.filterNot { it.id == id })
        }
    }

    suspend fun clear(account: Long) {
        check(account == account()) { "账号已变化，请重新操作" }
        val ids = tasks.value.filter { it.accountMid == account }.map { it.id }
        for (id in ids) delete(id, account)
    }

    // 服务启动后才消费队列，应用重启和失败任务都不会自行恢复下载。
    @Synchronized fun activateService(): Long { serviceEnabled = true; return ++serviceGeneration }

    @Synchronized fun stopService(token: Long) {
        if (token != serviceGeneration) return
        serviceEnabled = false
        transfer.cancel()
        worker?.cancel()
        scope.launch {
            mutex.withLock {
                if (token == serviceGeneration && !serviceEnabled && _loaded.value) {
                    try { commit(_tasks.value.map { if (it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                        it.copy(status = DownloadStatus.PAUSED) else it }) }
                    catch (error: Exception) { _notice.value = "下载停止状态保存失败：${error.friendlyMessage()}" }
                }
            }
        }
    }

    @Synchronized fun start(token: Long) {
        if (!serviceEnabled || token != serviceGeneration || scheduler?.isActive == true) return
        scheduler = scope.launch {
            try { while (isActive && serviceEnabled && token == serviceGeneration) {
                val work = mutex.withLock {
                    if (!_loaded.value) return@withLock null
                    synchronizeSession()
                    val session = sessions.value
                    val task = _tasks.value.firstOrNull { it.accountMid == accountOf(session) && it.status == DownloadStatus.QUEUED }
                        ?: return@withLock null
                    commit(_tasks.value.map { if (it.id == task.id) it.copy(status = DownloadStatus.DOWNLOADING) else it })
                    workerSession = session
                    session to task
                } ?: break
                activeId = work.second.id
                worker = launch { download(work.first, work.second, token) }
                worker?.join()
                activeId = null
            } } catch (error: CancellationException) { throw error }
            catch (error: Exception) { _notice.value = "下载队列已停止：${error.friendlyMessage()}" }
            finally {
                synchronized(this@OfflineDownloads) { scheduler = null }
                if (serviceEnabled && tasks.value.any { it.accountMid == account() && it.status == DownloadStatus.QUEUED })
                    start(serviceGeneration)
            }
        }
    }

    private suspend fun download(session: LoginCookies, task: OfflineDownload, token: Long) {
        try {
            check(serviceEnabled && token == serviceGeneration) { "下载服务已变化" }
            check(networkAllowed()) { "未连接可用网络，请联网后手动继续" }
            val option = source.resolve(session, task)
            for (audio in listOf(false, true)) {
                val current = mutex.withLock { owned(task.id) }
                transfer.download(if (audio) option.audioUrl else option.videoUrl, store.media(task.id, audio),
                    if (audio) current.audio else current.video,
                    allowed = { serviceEnabled && token == serviceGeneration && sessions.value == session && networkAllowed() }) { progress ->
                    mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        check(serviceEnabled && token == serviceGeneration && sessions.value == session) { "账号或下载服务已变化" }
                        val latest = owned(task.id)
                        if (latest.status != DownloadStatus.DOWNLOADING) throw CancellationException()
                        commit(_tasks.value.map { if (it.id == task.id) {
                            if (audio) it.copy(audio = progress) else it.copy(video = progress)
                        } else it })
                    }
                }
            }
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                val latest = owned(task.id)
                check(serviceEnabled && token == serviceGeneration && sessions.value == session && latest.status == DownloadStatus.DOWNLOADING)
                store.playable(latest.copy(status = DownloadStatus.COMPLETE))
                commit(_tasks.value.map { if (it.id == task.id) it.copy(status = DownloadStatus.COMPLETE, error = null) else it })
            }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                try { mutex.withLock {
                    if (_tasks.value.any { it.id == task.id && it.status == DownloadStatus.DOWNLOADING }) {
                        val paused = error is CancellationException || !serviceEnabled || token != serviceGeneration || sessions.value != session || !networkAllowed()
                        commit(_tasks.value.map { if (it.id == task.id) it.copy(
                            status = if (paused) DownloadStatus.PAUSED else DownloadStatus.FAILED,
                            error = if (paused) "下载已暂停，请手动继续" else error.friendlyMessage()) else it })
                    }
                } } catch (failure: Exception) { _notice.value = "下载状态保存失败：${failure.friendlyMessage()}" }
            }
        }
    }

    suspend fun playable(id: String): Pair<java.io.File, java.io.File> = mutex.withLock { store.playable(owned(id)) }
    suspend fun usage(account: Long): Long = mutex.withLock { store.occupiedBytes(_tasks.value.filter { it.accountMid == account }) }
    private fun owned(id: String): OfflineDownload = _tasks.value.firstOrNull { it.id == id && it.accountMid == account() }
        ?: error("缓存不属于当前账号或已删除")
    private fun accountOf(session: LoginCookies): Long = if (session.isLoggedIn) session.mid ?: 0 else 0
    private fun synchronizeSession() {
        val session = sessions.value
        if (session == capturedSession) return
        capturedSession = session
        if (_loaded.value) try {
            commit(_tasks.value.map { if (it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                it.copy(status = DownloadStatus.PAUSED) else it })
        } catch (error: Exception) { _notice.value = "账号切换后的缓存状态保存失败" }
        if (workerSession != session) { transfer.cancel(); worker?.cancel() }
    }
    private fun commit(records: List<OfflineDownload>) {
        check(_loaded.value) { "缓存索引未加载，已停止写入" }
        try { store.write(records) }
        catch (error: Exception) {
            transfer.cancel(); worker?.cancel()
            _tasks.value = _tasks.value.map { if (it.status in listOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING))
                it.copy(status = DownloadStatus.PAUSED, error = "存储失败，下载已停止") else it }
            _notice.value = "缓存存储失败，请清理空间后重试：${error.friendlyMessage()}"
            throw error
        }
        _tasks.value = records
    }
}
