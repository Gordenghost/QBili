package com.qbili.core

import java.util.Locale
import java.util.concurrent.TimeUnit

/** 播放量 / 点赞数等计数格式化：12345 -> "1.2万"，123456789 -> "1.2亿" */
fun formatCount(count: Long?): String {
    val n = count ?: return "0"
    return when {
        n < 0 -> "0"
        n < 10_000 -> n.toString()
        n < 100_000_000 -> "${trimZero(n / 10_000.0)}万"
        else -> "${trimZero(n / 100_000_000.0)}亿"
    }
}

/** 1.0 -> "1"，1.2 -> "1.2" */
private fun trimZero(value: Double): String =
    String.format(Locale.CHINA, "%.1f", value).removeSuffix(".0")

/** 秒数 -> "12:34" 或 "1:02:03" */
fun formatDuration(seconds: Int?): String {
    val s = seconds ?: return "00:00"
    if (s <= 0) return "00:00"
    val h = TimeUnit.SECONDS.toHours(s.toLong())
    val m = TimeUnit.SECONDS.toMinutes(s.toLong()) % 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.CHINA, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.CHINA, "%02d:%02d", m, sec)
    }
}

/** 毫秒时间戳 -> "12:34" 播放进度用 */
fun formatPosition(millis: Long): String = formatDuration((millis / 1000).toInt())

/** 秒级时间戳 -> 相对时间描述 */
fun formatRelativeTime(epochSeconds: Long?, nowSeconds: Long = System.currentTimeMillis() / 1000): String {
    val ts = epochSeconds ?: return ""
    if (ts <= 0) return ""
    val diff = nowSeconds - ts
    return when {
        diff < 60 -> "刚刚"
        diff < 3600 -> "${diff / 60}分钟前"
        diff < 86_400 -> "${diff / 3600}小时前"
        diff < 86_400 * 30 -> "${diff / 86_400}天前"
        diff < 86_400 * 365 -> "${diff / (86_400 * 30)}个月前"
        else -> "${diff / (86_400 * 365)}年前"
    }
}
