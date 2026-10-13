package com.qbili.data.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.qbili.domain.model.ReaderImageFormat
import com.qbili.domain.model.readerImageFormat
import com.qbili.domain.model.readerImageUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.UUID

data class DownloadedReaderImage(val file: File, val format: ReaderImageFormat) {
    val name: String get() = "QBili_${file.nameWithoutExtension}.${format.extension}"
}

class ReaderImageRepository(context: Context, client: OkHttpClient) {
    private val appContext = context.applicationContext
    private val downloader = ReaderImageDownloader(appContext.cacheDir, client)

    suspend fun download(rawUrl: String) = downloader.download(rawUrl)

    suspend fun save(image: DownloadedReaderImage, destination: Uri? = null): Unit = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val ownsDestination = destination == null
        val uri = destination ?: run {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "请先选择图片保存位置" }
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, image.name)
                put(MediaStore.Images.Media.MIME_TYPE, image.format.mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/QBili")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法创建相册图片")
        }
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                image.file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法写入图片")
            currentCoroutineContext().ensureActive()
            if (ownsDestination) {
                val updated = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                if (updated <= 0) throw IOException("相册图片发布失败")
            }
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}

class ReaderImageDownloader(private val cacheDirectory: File, client: OkHttpClient) {
    private val imageClient = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).build()

    suspend fun download(rawUrl: String): DownloadedReaderImage = withContext(Dispatchers.IO) {
        val url = requireNotNull(readerImageUrl(rawUrl)) { "仅支持 B站图床图片" }
        val file = File.createTempFile("reader_${UUID.randomUUID()}_", ".image", cacheDirectory)
        try {
            imageClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("图片下载失败：HTTP ${response.code}")
                val body = response.body ?: throw IOException("图片内容为空")
                if (body.contentLength() > MAX_BYTES) throw IOException("图片超过 50MB，暂不支持保存")
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw IOException("图片超过 50MB，暂不支持保存")
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val header = ByteArray(32)
                file.inputStream().use { it.read(header) }
                val format = readerImageFormat(header) ?: throw IOException("返回内容不是支持的图片格式")
                currentCoroutineContext().ensureActive()
                DownloadedReaderImage(file, format)
            }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
    }

    private companion object { const val MAX_BYTES = 50L * 1024 * 1024 }
}
