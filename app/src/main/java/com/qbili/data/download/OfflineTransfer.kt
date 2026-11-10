package com.qbili.data.download

import com.qbili.data.remote.ApiConstants
import com.qbili.domain.download.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

interface OfflineTrackTransfer {
    fun cancel()
    suspend fun download(url: String, file: File, track: DownloadTrack,
        allowed: () -> Boolean, progress: suspend (DownloadTrack) -> Unit)
}

class OfflineTransfer(private val client: OkHttpClient = OkHttpClient.Builder()
    .cookieJar(okhttp3.CookieJar.NO_COOKIES).retryOnConnectionFailure(false)
    .followRedirects(false).followSslRedirects(false)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()) : OfflineTrackTransfer {
    @Volatile private var call: Call? = null
    override fun cancel() { call?.cancel() }

    override suspend fun download(url: String, file: File, track: DownloadTrack,
        allowed: () -> Boolean, progress: suspend (DownloadTrack) -> Unit) {
        require(trustedDownloadUrl(url)) { "缓存地址不是可信 B 站视频地址" }
        if (track.complete && track.total > 0 && file.length() == track.total) return
        val offset = file.length().takeIf { it > 0 && it < track.total && strongDownloadEtag(track.etag).isNotEmpty() } ?: 0
        val request = Request.Builder().url(url).header("User-Agent", ApiConstants.USER_AGENT)
            .header("Referer", ApiConstants.WEB_REFERER).header("Accept-Encoding", "identity")
        if (offset > 0) request.header("Range", "bytes=$offset-").header("If-Range", track.etag)
        val active = client.newCall(request.build())
        call = active
        try {
            currentCoroutineContext().ensureActive()
            check(allowed()) { "账号或网络条件已变化，下载已暂停" }
            active.execute().use { response ->
                check(response.code == 200 || response.code == 206) { "视频下载失败（HTTP ${response.code}），请手动重试" }
                val body = response.body ?: error("下载响应为空")
                val range = try { downloadRange(response.code, offset, track.total, track.etag,
                    body.contentLength(), response.header("Content-Range"), response.header("ETag")) }
                catch (error: IllegalArgumentException) {
                    if (response.code == 206) progress(track.copy(etag = "", complete = false))
                    throw error
                }
                check(range.total <= 20L * 1024 * 1024 * 1024) { "单轨道缓存超过 20 GB，暂不支持" }
                val start = if (range.append) offset else 0L
                check(file.parentFile!!.usableSpace > range.total - start + 32L * 1024 * 1024) { "可用空间不足，请清理缓存后重试" }
                var bytes = start
                val prefix = ByteArray(8)
                var prefixCount = if (start == 0L) 0 else 8
                val etag = strongDownloadEtag(response.header("ETag"))
                progress(DownloadTrack(bytes, range.total, etag))
                FileOutputStream(file, range.append).use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var lastUpdate = System.nanoTime()
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            check(allowed()) { "账号或网络条件已变化，下载已暂停" }
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (prefixCount < 8) {
                                val copied = minOf(8 - prefixCount, count)
                                buffer.copyInto(prefix, prefixCount, 0, copied)
                                prefixCount += copied
                                if (prefixCount == 8) check(String(prefix, 4, 4, Charsets.US_ASCII) in listOf("ftyp", "styp")) {
                                    "下载内容不是标准 MP4 视频轨道"
                                }
                            }
                            check(count.toLong() <= range.total - bytes) { "服务器返回了多余文件内容" }
                            output.write(buffer, 0, count)
                            bytes += count
                            if (System.nanoTime() - lastUpdate >= 1_000_000_000) {
                                progress(DownloadTrack(bytes, range.total, etag))
                                lastUpdate = System.nanoTime()
                            }
                        }
                    }
                    output.fd.sync()
                }
                currentCoroutineContext().ensureActive()
                check(bytes == range.total && prefixCount == 8) { "缓存文件未下载完整，请手动继续" }
                progress(DownloadTrack(bytes, range.total, etag, complete = true))
            }
        } finally { if (call === active) call = null }
    }
}
