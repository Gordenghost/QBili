package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.core.QBiliLog
import com.qbili.data.local.AccessTokenStore
import com.qbili.data.remote.api.AppInteractionApi
import com.qbili.data.remote.api.InteractionApi
import com.qbili.data.remote.dto.requireSuccess
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.FavFolder
import com.qbili.domain.model.VideoInteraction
import com.qbili.domain.model.WatchLaterItem

/**
 * 视频互动：点赞、点踩、投币、收藏、稍后再看、关注、转发计数。
 *
 * 除点踩外全部走 Web 端点（Cookie + csrf），所以不论用户用哪种方式登录都能用。
 * 点踩没有 Web 版接口，只能用 App 端点 + access_key，缺 key 时明确拒绝而不是静默失败。
 */
class InteractionRepository(
    private val api: InteractionApi,
    private val appApi: AppInteractionApi,
    private val accessTokenStore: AccessTokenStore,
) {

    /** 点踩需要移动端登录才能拿到的 access_key */
    val canDislike: Boolean get() = accessTokenStore.hasAccessKey

    suspend fun loadState(aid: Long, bvid: String): VideoInteraction {
        val response = api.relation(aid = aid.takeIf { it > 0 }, bvid = bvid.ifBlank { null })
        val data = response.data
        if (!response.isSuccess || data == null) {
            // 未登录时接口返回 -101，这不是错误，只是没有互动状态可显示
            QBiliLog.i(TAG, "互动状态不可用: code=${response.code} ${response.errorMessage}")
            return VideoInteraction()
        }
        QBiliLog.i(
            TAG,
            "互动状态: like=${data.like} dislike=${data.dislike} fav=${data.favorite} " +
                "coin=${data.coins} attention=${data.attention}",
        )
        return VideoInteraction(
            liked = data.like,
            disliked = data.dislike,
            favorited = data.favorite,
            coinCount = data.coins,
            following = data.attention,
        )
    }

    suspend fun setLike(bvid: String, like: Boolean) {
        api.like(bvid = bvid, like = likeRequestValue(like)).requireSuccess()
    }

    /**
     * 点踩。仅在移动端登录（有 access_key）时可用。
     */
    suspend fun setDislike(aid: Long, dislike: Boolean) {
        val key = accessTokenStore.accessKey ?: throw BiliApiException(
            -1,
            "点踩只有 B 站移动端接口支持，需要用短信或密码登录（扫码/Cookie 登录拿不到所需凭据）",
        )
        appApi.dislike(accessKey = key, aid = aid, dislike = dislikeRequestValue(dislike)).requireSuccess()
    }

    /**
     * @param count 1 或 2 枚
     * @param alsoLike 同时点赞
     */
    suspend fun addCoin(bvid: String, count: Int, alsoLike: Boolean) {
        require(count in 1..2) { "投币数只能是 1 或 2，收到 $count" }
        api.addCoin(
            bvid = bvid,
            multiply = count,
            selectLike = if (alsoLike) 1 else 0,
        ).requireSuccess()
    }

    /** 收藏夹列表，含「该视频是否已在其中」 */
    suspend fun favFolders(upMid: Long, aid: Long): List<FavFolder> {
        val list = api.favFolders(upMid = upMid, type = FAV_TYPE_VIDEO, rid = aid).requireData().list
        return list.orEmpty().map { dto ->
            FavFolder(
                id = dto.id.takeIf { it > 0 } ?: dto.fid,
                title = dto.title,
                mediaCount = dto.mediaCount,
                isPrivate = dto.attr and 1 == 1,
                containsVideo = dto.containsCurrentVideo,
            )
        }
    }

    /**
     * 提交收藏变更。加入与移除在**同一个请求**里完成——
     * 分两次发会出现「先删后加失败」导致收藏丢失的中间态。
     */
    suspend fun updateFavorites(aid: Long, addIds: List<Long>, removeIds: List<Long>) {
        if (addIds.isEmpty() && removeIds.isEmpty()) return
        api.dealFavorite(
            rid = aid,
            type = FAV_TYPE_VIDEO,
            addMediaIds = addIds.joinToString(","),
            delMediaIds = removeIds.joinToString(","),
        ).requireSuccess()
    }

    suspend fun addToWatchLater(bvid: String) {
        api.addToView(bvid).requireSuccess()
    }

    suspend fun watchLaterList(): List<WatchLaterItem> = api.watchLaterList().requireData().list
        .filter { it.aid > 0 }
        .map { it.toModel() }

    suspend fun isInWatchLater(aid: Long): Boolean = watchLaterList().any { it.video.aid == aid }

    suspend fun removeFromWatchLater(aid: Long) {
        api.removeFromToView(aid).requireSuccess()
    }

    suspend fun setFollow(mid: Long, follow: Boolean) {
        api.modifyRelation(
            fid = mid,
            act = if (follow) ACT_FOLLOW else ACT_UNFOLLOW,
            reSrc = RE_SRC_VIDEO_PAGE,
        ).requireSuccess()
    }

    suspend fun blockUser(mid: Long) {
        require(mid > 0)
        api.modifyRelation(fid = mid, act = 5, reSrc = RE_SRC_VIDEO_PAGE).requireSuccess()
    }

    /** 分享计数，纯统计，失败无所谓 */
    suspend fun reportShare(bvid: String) {
        runCatching { api.addShare(bvid) }
            .onFailure { QBiliLog.i(TAG, "分享计数上报失败（不影响分享）: ${it.message}") }
    }

    private companion object {
        const val TAG = "InteractionRepository"
        const val FAV_TYPE_VIDEO = 2
        const val ACT_FOLLOW = 1
        const val ACT_UNFOLLOW = 2
        const val RE_SRC_VIDEO_PAGE = 14
    }
}

internal fun likeRequestValue(shouldLike: Boolean): Int = if (shouldLike) 1 else 2

internal fun dislikeRequestValue(shouldDislike: Boolean): Int = if (shouldDislike) 0 else 1
