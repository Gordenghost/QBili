package com.qbili.ui.util

/**
 * B 站图床支持在 URL 后拼接处理参数，按需取缩略图能显著减少流量。
 * 例：`https://i0.hdslb.com/bfs/archive/xxx.jpg@480w_300h_1c.webp`
 */
fun String.biliThumbnail(width: Int = 480, height: Int = 300): String {
    if (isBlank()) return ""
    val normalized = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> "https://" + removePrefix("http://")
        else -> this
    }
    // 已带处理参数的不再追加
    if (normalized.contains('@')) return normalized
    return "$normalized@${width}w_${height}h_1c.webp"
}

/** 头像等正方形图片 */
fun String.biliAvatar(@Suppress("UNUSED_PARAMETER") size: Int = 96): String {
    if (isBlank()) return ""
    val normalized = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> "https://" + removePrefix("http://")
        else -> this
    }
    // 用户头像可能是动态装扮图；图床不一定支持封面图的裁切参数，直接请求原图。
    return normalized
}
