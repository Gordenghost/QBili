package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.data.remote.api.PlayurlApi
import com.qbili.data.remote.api.VideoApi
import com.qbili.data.remote.dto.VideoViewDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.VideoDetail
import com.qbili.domain.model.VideoPage

/**
 * 视频详情与播放地址。
 *
 * 播放链路上的一切都以 **cid** 为索引（playurl、弹幕都不认 aid/bvid），
 * 而 cid 只能从详情接口拿，所以详情是播放的必要前置。
 */
class VideoRepository(
    private val videoApi: VideoApi,
    private val playurlApi: PlayurlApi,
) {

    suspend fun detail(videoId: String): VideoDetail {
        val id = VideoId.parse(videoId)
            ?: throw BiliApiException(-1, "无法识别的视频标识：$videoId")

        val dto = videoApi.view(bvid = id.bvid, aid = id.aid).requireData()
        if (dto.cid == 0L) throw BiliApiException(-1, "详情接口未返回 cid，无法播放")
        return dto.toDetail()
    }

    /**
     * 取播放地址。
     *
     * @param qn 期望画质；服务端可能给不到（未登录上限 480P），
     *           实际画质由 StreamSelector 按真实轨道决定
     */
    suspend fun playurl(
        cid: Long,
        videoId: String,
        qn: Int,
    ): PlayurlResult {
        val id = VideoId.parse(videoId)
        val data = playurlApi.getPlayurl(
            cid = cid,
            bvid = id?.bvid,
            aid = id?.aid,
            qn = qn,
            fnval = FNVAL_DASH_ALL,
            fnver = 0,
            fourk = 1,
        ).requireData()
        return PlayurlResult.fromDto(data)
    }

    private companion object {
        /** dash + 4K + 杜比 + HDR 全开 */
        const val FNVAL_DASH_ALL = 4048
    }
}

/**
 * 视频标识。路由里传过来的可能是 `BV1xx…`，也可能是 `av114514`——
 * 移动端搜索结果只给 aid，不给 bvid。
 */
data class VideoId(val bvid: String?, val aid: Long?) {
    companion object {
        fun parse(raw: String): VideoId? {
            val text = raw.trim()
            if (text.isEmpty()) return null
            return when {
                text.startsWith("BV", ignoreCase = true) -> VideoId(bvid = text, aid = null)
                text.startsWith("av", ignoreCase = true) ->
                    text.drop(2).toLongOrNull()?.takeIf { it > 0 }?.let { VideoId(null, it) }
                else -> text.toLongOrNull()?.takeIf { it > 0 }?.let { VideoId(null, it) }
            }
        }
    }
}

private fun VideoViewDto.toDetail(): VideoDetail {
    // 单 P 视频的 pages 也有一项；为空时用顶层 cid 兜底，保证一定有可播放的分 P
    val resolvedPages = pages.takeIf { it.isNotEmpty() }?.map { page ->
        VideoPage(
            cid = page.cid,
            index = page.page,
            title = page.part.ifBlank { "P${page.page}" },
            durationSeconds = page.duration,
        )
    } ?: listOf(VideoPage(cid = cid, index = 1, title = "正片", durationSeconds = duration))

    return VideoDetail(
        aid = aid,
        bvid = bvid,
        title = stripHtml(title).ifBlank { "（无标题）" },
        description = desc,
        cover = normalizeUrl(pic),
        durationSeconds = duration,
        pubDate = pubdate,
        authorMid = owner?.mid ?: 0,
        authorName = owner?.name.orEmpty(),
        authorFace = normalizeUrl(owner?.face),
        viewCount = stat?.view ?: 0,
        danmakuCount = stat?.danmaku ?: 0,
        likeCount = stat?.like ?: 0,
        coinCount = stat?.coin ?: 0,
        favoriteCount = stat?.favorite ?: 0,
        replyCount = stat?.reply ?: 0,
        pages = resolvedPages,
    )
}
