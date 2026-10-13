package com.qbili.domain.model

import com.qbili.core.normalizeUrl
import java.net.URI

fun readerImageUrl(raw: String): String? = runCatching {
    val normalized = normalizeUrl(raw)
    val uri = URI(normalized)
    val host = uri.host?.lowercase() ?: return null
    normalized.takeIf { uri.scheme == "https" && uri.userInfo == null &&
        (host.endsWith(".hdslb.com") || host.endsWith(".biliimg.com")) }
}.getOrNull()

data class ReaderImageFormat(val mimeType: String, val extension: String)

fun readerImageFormat(header: ByteArray): ReaderImageFormat? {
    fun begins(vararg bytes: Int) = header.size >= bytes.size && bytes.indices.all { (header[it].toInt() and 255) == bytes[it] }
    return when {
        begins(0xff, 0xd8, 0xff) -> ReaderImageFormat("image/jpeg", "jpg")
        begins(137, 80, 78, 71, 13, 10, 26, 10) -> ReaderImageFormat("image/png", "png")
        begins(71, 73, 70, 56, 55, 97) || begins(71, 73, 70, 56, 57, 97) -> ReaderImageFormat("image/gif", "gif")
        header.size >= 12 && String(header.copyOfRange(0, 4), Charsets.US_ASCII) == "RIFF" &&
            String(header.copyOfRange(8, 12), Charsets.US_ASCII) == "WEBP" -> ReaderImageFormat("image/webp", "webp")
        header.size >= 16 && String(header.copyOfRange(4, 8), Charsets.US_ASCII) == "ftyp" &&
            String(header.copyOfRange(8, 12), Charsets.US_ASCII) in setOf("avif", "avis") -> ReaderImageFormat("image/avif", "avif")
        else -> null
    }
}

fun readerImagePreviewLink(index: Int) = "https://qbili.invalid/reader-image/$index"

fun readerImagePreviewIndex(link: String, count: Int): Int? = runCatching {
    val uri = URI(link)
    if (uri.scheme != "https" || uri.host != "qbili.invalid" || uri.userInfo != null || uri.port != -1 ||
        uri.rawQuery != null || uri.rawFragment != null) return null
    Regex("^/reader-image/(\\d+)$").matchEntire(uri.path)?.groupValues?.get(1)?.toIntOrNull()
        ?.takeIf { it in 0 until count }
}.getOrNull()
