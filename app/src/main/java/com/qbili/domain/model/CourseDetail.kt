package com.qbili.domain.model

import java.net.URI

data class CourseTarget(val seasonId: Long = 0, val episodeId: Long = 0) {
    val valid: Boolean get() = (seasonId > 0 && episodeId == 0L) || (episodeId > 0 && seasonId == 0L)
    val url: String get() = "https://www.bilibili.com/cheese/play/" +
        if (episodeId > 0) "ep$episodeId" else "ss$seasonId"
}

fun parseCourseTarget(input: String): CourseTarget? {
    if (input.any { it == '\r' || it == '\n' }) return null
    val raw = input.trim()
    fun fromToken(token: String): CourseTarget? {
        val match = Regex("(ss|ep)([1-9][0-9]*)").matchEntire(token) ?: return null
        val id = match.groupValues[2].toLongOrNull() ?: return null
        return if (match.groupValues[1] == "ss") CourseTarget(seasonId = id) else CourseTarget(episodeId = id)
    }
    fromToken(raw)?.let { return it }
    val uri = runCatching { URI(if (raw.startsWith("//")) "https:$raw" else raw) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme !in setOf("https", "http") || uri.rawUserInfo != null ||
        uri.port !in setOf(-1, if (scheme == "https") 443 else 80) ||
        uri.host?.lowercase() !in setOf("www.bilibili.com", "bilibili.com", "m.bilibili.com")) return null
    val token = Regex("/cheese/play/((?:ss|ep)[1-9][0-9]*)/?").matchEntire(uri.rawPath.orEmpty())
        ?.groupValues?.get(1) ?: return null
    return fromToken(token)
}

data class CourseEpisode(
    val id: Long,
    val cid: Long,
    val index: Int,
    val title: String,
    val cover: String,
    val durationSeconds: Int,
    val label: String,
    val previewHint: String,
    val playable: Boolean,
    val video: Boolean,
) {
    val accessHint: String get() = when {
        !video -> "直播／其他课程课时暂不支持"
        cid <= 0 -> "课时尚未提供播放信息"
        label.isNotBlank() -> label
        !playable -> "需登录或购买后查看，具体权限以平台为准"
        else -> "播放权限以平台为准"
    }
}

data class CourseDetail(
    val id: Long,
    val title: String,
    val cover: String,
    val subtitle: String,
    val authorMid: Long,
    val authorName: String,
    val authorAvatar: String,
    val description: String,
    val images: List<String>,
    val releaseInfo: String,
    val price: String,
    val access: String,
    val episodes: List<CourseEpisode>,
    val catalogueIncomplete: Boolean,
)

data class CoursePlayback(val playback: SeasonPlayback, val previewHint: String,
    val progressAllowed: Boolean = !playback.preview)
