package com.qbili.data.local

import com.qbili.domain.download.DownloadStatus
import com.qbili.domain.download.OfflineDownload
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class OfflineDownloadStore(private val root: File) {
    private val json = Json { ignoreUnknownKeys = true }
    private val index: File get() = File(root, "index.json")

    init { check(root.isDirectory || root.mkdirs()) { "无法创建离线缓存目录" } }

    fun read(): List<OfflineDownload> {
        if (!index.exists()) return emptyList()
        val records = json.decodeFromString<List<OfflineDownload>>(index.readText())
        check(records.size <= 200 && records.all { it.valid } && records.map { it.id }.distinct().size == records.size) {
            "离线缓存索引损坏，已停止自动操作"
        }
        return records.map { record ->
            if (record.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED))
                record.copy(status = DownloadStatus.PAUSED, error = "应用重新启动，请手动继续下载") else record
        }
    }

    fun write(records: List<OfflineDownload>) {
        require(records.size <= 200 && records.all { it.valid })
        val temporary = File(root, "index.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(json.encodeToString(records).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        Files.move(temporary.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun media(id: String, audio: Boolean): File {
        require(id.matches(Regex("[0-9]+_[0-9]+_[0-9]+_[0-9]+"))) { "缓存标识无效" }
        val directory = File(root, id)
        check(directory.canonicalFile.parentFile == root.canonicalFile) { "缓存目录越界" }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建视频缓存目录" }
        val file = File(directory, if (audio) "audio.media" else "video.media")
        check(file.canonicalFile.parentFile == directory.canonicalFile) { "缓存文件越界" }
        return file
    }

    fun playable(record: OfflineDownload): Pair<File, File> {
        check(record.status == DownloadStatus.COMPLETE && record.video.complete && record.audio.complete) { "缓存尚未完成" }
        val video = media(record.id, false)
        val audio = media(record.id, true)
        check(record.video.total > 0 && record.audio.total > 0 && video.length() == record.video.total &&
            audio.length() == record.audio.total) { "缓存文件缺失或不完整，请删除后重新缓存" }
        return video to audio
    }

    fun delete(record: OfflineDownload) {
        val video = media(record.id, false)
        val audio = media(record.id, true)
        check(!video.exists() || video.delete()) { "视频缓存删除失败" }
        check(!audio.exists() || audio.delete()) { "音频缓存删除失败" }
        check(video.parentFile?.delete() == true) { "缓存目录删除失败" }
    }

    fun occupiedBytes(records: List<OfflineDownload>): Long = records.sumOf {
        media(it.id, false).length() + media(it.id, true).length()
    }

    val availableBytes: Long get() = root.usableSpace
}
