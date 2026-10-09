package com.qbili.domain.model

/**
 * 视频卡片的统一模型。首页推荐、搜索结果、排行榜、收藏夹、稍后再看
 * 全部映射到这里，UI 层只认这一个类型。
 */
data class VideoItem(
    val aid: Long,
    val bvid: String,
    val cid: Long? = null,
    val title: String,
    val cover: String,
    val durationSeconds: Int = 0,
    val authorMid: Long = 0,
    val authorName: String = "",
    val authorFace: String = "",
    val channel: String = "",
    val viewCount: Long = 0,
    val danmakuCount: Long = 0,
    val pubDate: Long = 0,
    /** 推荐理由，例如「已关注」「1万点赞」，无则为 null */
    val recommendReason: String? = null,
) {
    val key: String get() = if (bvid.isNotEmpty()) bvid else "av$aid"
}
