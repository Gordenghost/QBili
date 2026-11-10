package com.qbili.domain.download

import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, FAILED, COMPLETE }

@Serializable
data class DownloadTrack(
    val bytes: Long = 0,
    val total: Long = 0,
    val etag: String = "",
    val complete: Boolean = false,
)

@Serializable
data class OfflineDownload(
    val accountMid: Long,
    val aid: Long,
    val cid: Long,
    val quality: Int,
    val title: String,
    val part: String,
    val author: String,
    val durationSeconds: Int,
    val qualityLabel: String,
    val audioId: Int,
    val createdAt: Long,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val video: DownloadTrack = DownloadTrack(),
    val audio: DownloadTrack = DownloadTrack(),
    val error: String? = null,
) {
    val id: String get() = "${accountMid}_${aid}_${cid}_$quality"
    val bytes: Long get() = video.bytes + audio.bytes
    val total: Long get() = if (video.total > 0 && audio.total > 0) video.total + audio.total else 0
    val valid: Boolean get() = accountMid >= 0 && aid > 0 && cid > 0 && quality > 0 && audioId > 0 &&
        durationSeconds > 0 && video.bytes >= 0 && audio.bytes >= 0 && video.total >= video.bytes && audio.total >= audio.bytes &&
        video.total <= 20L * 1024 * 1024 * 1024 && audio.total <= 20L * 1024 * 1024 * 1024 &&
        (!video.complete || video.total > 0 && video.bytes == video.total) &&
        (!audio.complete || audio.total > 0 && audio.bytes == audio.total) &&
        (status != DownloadStatus.COMPLETE || video.complete && audio.complete)
}

data class DownloadOption(val quality: Int, val label: String, val audioId: Int, val videoUrl: String, val audioUrl: String)
data class DownloadPage(val cid: Long, val number: Int, val title: String, val durationSeconds: Int)
data class DownloadVideo(val aid: Long, val title: String, val author: String, val pages: List<DownloadPage>)

fun trustedDownloadUrl(raw: String): Boolean = runCatching {
    val uri = URI(raw)
    val host = uri.host?.lowercase().orEmpty()
    uri.scheme.equals("https", true) && uri.rawUserInfo == null && uri.port in listOf(-1, 443) &&
        (host == "bilivideo.com" || host.endsWith(".bilivideo.com") ||
            host == "bilivideo.cn" || host.endsWith(".bilivideo.cn"))
}.getOrDefault(false)

fun strongDownloadEtag(raw: String?): String = raw?.takeIf {
    it.length in 2..256 && it.startsWith('"') && it.endsWith('"') && it.none { char -> char == '\r' || char == '\n' }
}.orEmpty()

data class DownloadRange(val append: Boolean, val total: Long)

// 续传只接受服务器确认的相同实体，200 响应表示重新下载。
fun downloadRange(code: Int, offset: Long, oldTotal: Long, oldEtag: String,
    contentLength: Long, contentRange: String?, etag: String?): DownloadRange {
    require(contentLength > 0) { "下载响应缺少有效文件长度" }
    if (code == 200) return DownloadRange(false, contentLength)
    require(code == 206 && offset > 0 && oldEtag.isNotEmpty() && strongDownloadEtag(etag) == oldEtag) {
        "服务器未确认相同文件，暂停后请重试"
    }
    val match = requireNotNull(Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(contentRange.orEmpty())) { "下载续传范围无效" }
    val start = requireNotNull(match.groupValues[1].toLongOrNull()) { "下载续传范围无效" }
    val end = requireNotNull(match.groupValues[2].toLongOrNull()) { "下载续传范围无效" }
    val total = requireNotNull(match.groupValues[3].toLongOrNull()) { "下载续传范围无效" }
    require(start == offset && end >= start && end < total && end - start + 1 == contentLength &&
        total == oldTotal && end == total - 1) { "下载续传范围与原文件不一致" }
    return DownloadRange(true, total)
}

fun formatDownloadBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024 * 1024 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024))
    else -> "${bytes / 1024} KB"
}
