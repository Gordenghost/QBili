package com.qbili.domain.model

enum class SeasonRankingCategory(val seasonType: Int, val label: String, val legacyRankEndpoint: Boolean) {
    ANIME(1, "番剧", true), GUOCHUANG(4, "国产动画", false), MOVIE(2, "电影", false),
    TV(5, "电视剧", false), DOCUMENTARY(3, "纪录片", false), VARIETY(7, "综艺", false),
}

enum class SeasonRankingPeriod(val days: Int, val label: String) {
    THREE_DAYS(3, "近3日"), WEEK(7, "近7日"),
}

data class SeasonRankingItem(
    val seasonId: Long,
    val rank: Int,
    val title: String,
    val cover: String,
    val progress: String,
    val badge: String,
    val score: Double?,
    val viewCount: Long?,
    val followCount: Long?,
)

fun validSeasonRating(score: Double?): Double? = score?.takeIf { it.isFinite() && it > 0.0 && it <= 10.0 }
