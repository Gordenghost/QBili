package com.qbili.ui.navigation

import com.qbili.domain.model.RecommendationFilterGroup

/** 全局路由表。参数化路由统一用 `buildXxx()` 构造，避免手拼字符串出错。 */
object Route {
    const val MAIN = "main"
    const val SEARCH = "search"

    private const val VIDEO_BASE = "video"
    const val ARG_BVID = "bvid"
    const val VIDEO = "$VIDEO_BASE/{$ARG_BVID}"
    fun buildVideo(bvid: String) = "$VIDEO_BASE/$bvid"

    private const val COMMENTS_BASE = "comments"
    const val ARG_COMMENT_TYPE = "type"
    const val ARG_COMMENT_OID = "oid"
    const val COMMENTS = "$COMMENTS_BASE/{$ARG_COMMENT_TYPE}/{$ARG_COMMENT_OID}"
    fun buildComments(aid: Long) = buildResourceComments(1, aid)
    fun buildResourceComments(type: Int, oid: Long) = "$COMMENTS_BASE/$type/$oid"

    private const val SPACE_BASE = "space"
    const val ARG_MID = "mid"
    const val SPACE = "$SPACE_BASE/{$ARG_MID}"
    fun buildSpace(mid: Long) = "$SPACE_BASE/$mid"

    private const val OPUS_BASE = "opus"
    const val ARG_OPUS_ID = "opusId"
    const val OPUS = "$OPUS_BASE/{$ARG_OPUS_ID}"
    fun buildOpus(id: String) = "$OPUS_BASE/$id"

    private const val LIVE_BASE = "live"
    const val ARG_ROOM_ID = "roomId"
    const val LIVE = "$LIVE_BASE/{$ARG_ROOM_ID}"
    fun buildLive(roomId: Long) = "$LIVE_BASE/$roomId"

    private const val SEASON_BASE = "season"
    const val ARG_SEASON_ID = "seasonId"
    const val SEASON = "$SEASON_BASE/{$ARG_SEASON_ID}"
    fun buildSeason(seasonId: Long) = "$SEASON_BASE/$seasonId"

    private const val ARTICLE_BASE = "article"
    const val ARG_CVID = "cvid"
    const val ARTICLE = "$ARTICLE_BASE/{$ARG_CVID}"
    fun buildArticle(cvid: Long) = "$ARTICLE_BASE/$cvid"

    const val LOGIN = "login"
    const val MESSAGES = "messages"
    const val FAVORITES = "favorites"
    const val WATCH_LATER = "watchLater"
    const val SETTINGS = "settings"
    const val ABOUT = "settings/about"
    const val PUSH_SETTINGS = "settings/push"
    fun buildPushFilter(group: RecommendationFilterGroup) = "$PUSH_SETTINGS/${group.key}"
}

/** 主页底部导航的四个 Tab */
object TabRoute {
    const val HOME = "tab/home"
    const val RANKING = "tab/ranking"
    const val DYNAMIC = "tab/dynamic"
    const val PROFILE = "tab/profile"
}
