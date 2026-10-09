package com.qbili.core

import android.content.Context
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量文件日志。
 *
 * 为什么不用第三方日志库：只需要给「短信登录」这类无法连 adb 排查的流程
 * 留一份可导出的文本，几十行足够。同步追加写入、量极小，
 * synchronized 保证多线程交错时每一行都完整不串。
 *
 * 文件在应用私有目录 filesDir/logs/ 下，卸载即清除；超过 [MAX_BYTES]
 * 直接清空重来，避免无限增长。用户可在「我的」页面把文件分享出去。
 */
object QBiliLog {

    private const val MAX_BYTES = 512L * 1024

    private var logFile: File? = null
    private val lock = Any()
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA)

    /** Application.onCreate 里调用一次 */
    fun init(context: Context, sessionHeader: String) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        val file = File(dir, "qbili.log")
        if (file.exists() && file.length() > MAX_BYTES) file.delete()
        logFile = file
        i("App", "===== $sessionHeader =====")
    }

    fun i(tag: String, message: String) = write("I", tag, message)

    fun w(tag: String, message: String, error: Throwable? = null) =
        write("W", tag, buildString {
            append(message)
            if (error != null) {
                append(" | ").append(error::class.java.simpleName)
                append(": ").append(error.message ?: "")
                if (error is UnknownHostException) append("（网络不可用）")
            }
        })

    /**
     * @return 可分享的 content:// Uri；无日志文件时返回 null
     */
    fun shareUri(context: Context):android.net.Uri? {
        val file = logFile ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    private fun write(level: String, tag: String, message: String) {
        // 同步镜像到 logcat，连上电脑时两边都能看
        Log.println(if (level == "W") Log.WARN else Log.INFO, tag, message)

        val line = "${timeFormat.format(Date())} $level/$tag: $message\n"
        synchronized(lock) {
            runCatching { logFile?.appendText(line) }
                .onFailure { Log.w("QBiliLog", "写日志失败", it) }
        }
    }
}
