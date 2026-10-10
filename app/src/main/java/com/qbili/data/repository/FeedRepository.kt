package com.qbili.data.repository

import com.qbili.data.remote.api.FeedApi
import com.qbili.data.remote.api.AppFeedApi
import com.qbili.data.remote.api.VideoTagApi
import com.qbili.data.remote.dto.AppFeedItemDto
import com.qbili.data.remote.dto.FeedItemDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.RecommendationFilters
import com.qbili.domain.model.VideoItem
import com.qbili.core.QBiliLog
import com.qbili.core.BiliRiskControlException
import com.qbili.core.aidToBvid
import com.qbili.core.normalizeUrl
import com.qbili.core.parseDurationText
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class RecommendationPage(val videos: List<VideoItem>, val hasMore: Boolean, val nextIndex: Long? = null)

class FeedRepository(
    private val api: FeedApi,
    private val videoTagApi: VideoTagApi,
    private val appApi: AppFeedApi? = null,
    private val accessKey: () -> String? = { null },
    private val buvid: () -> String = { "" },
) {

    suspend fun primaryTag(video: VideoItem): String? = videoTagApi.tags(
        bvid = video.bvid.takeIf { it.isNotBlank() },
        aid = video.aid.takeIf { video.bvid.isBlank() && it > 0 },
    ).requireData().map { it.tagName }.also { tagCache[video.key] = it }.firstOrNull { it.isNotBlank() }

    private val tagRequestLimit = Semaphore(4)
    private val tagCache: MutableMap<String, List<String>> = Collections.synchronizedMap(
        object : LinkedHashMap<String, List<String>>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean = size > 256
        },
    )

    private suspend fun tags(video: VideoItem): List<String> = tagCache[video.key] ?: tagRequestLimit.withPermit {
        tagCache[video.key] ?: videoTagApi.tags(
            bvid = video.bvid.takeIf { it.isNotBlank() },
            aid = video.aid.takeIf { video.bvid.isBlank() && it > 0 },
        ).requireData().map { it.tagName }.also { tagCache[video.key] = it }
    }

    suspend fun isBlocked(video: VideoItem, filters: RecommendationFilters): Boolean {
        if (filters.blocksVideo(video)) return true
        if (!filters.needsTagLookup) return false
        return try {
            filters.blocksTags(tags(video))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            QBiliLog.w("HomeFilter", "标签校验失败，暂不展示 ${video.key}", error)
            true
        }
    }

    /**
     * 拉一页推荐。
     *
     * @param freshIdx 刷新序号，从 1 开始递增；同一序号会拿到相同内容，
     *                 所以下拉刷新需要递增它才能拿到新内容。
     */
    suspend fun recommend(
        freshIdx: Int,
        filters: RecommendationFilters,
        pageSize: Int = PAGE_SIZE,
    ): RecommendationPage {
        val data = api.recommend(
            freshType = 4,
            pageSize = pageSize,
            freshIdx = freshIdx,
            freshIdx1h = freshIdx,
            brush = freshIdx,
            feedVersion = "V8",
            homepageVer = 1,
            webLocation = "1430650",
            yNum = 3,
            lastYNum = 3,
        ).requireData()
        data.vVoucher?.takeIf { it.isNotBlank() }?.let { throw BiliRiskControlException(it) }
        val items = requireNotNull(data.item) { "网页端推荐未返回列表，请重试或切换推荐来源" }
        val candidates = items
            // 推荐流里混有番剧、图文、直播等条目，当前只展示普通视频
            .filter { it.goto == null || it.goto == "av" }
            .mapNotNull { it.toVideoItemOrNull() }
        return RecommendationPage(filter(candidates, filters), hasMore = items.isNotEmpty())
    }

    suspend fun recommendApp(index: Long, filters: RecommendationFilters): RecommendationPage {
        val data = requireNotNull(appApi) { "App 推荐接口未配置" }.recommend(
            parameters = appRecommendationParameters(index, accessKey()),
            buvid = buvid(),
        ).requireData()
        data.vVoucher?.takeIf { it.isNotBlank() }?.let { throw BiliRiskControlException(it) }
        val items = requireNotNull(data.items) { "App 端推荐未返回列表，请重试或切换推荐来源" }
        val nextIndex = if (items.isEmpty()) null else requireNotNull(items.last().idx) {
            "App 端推荐未返回分页游标，请重试或切换推荐来源"
        }.also { require(it > 0 && (index == 0L || it < index)) { "App 端推荐分页游标无效，请刷新后重试" } }
        return RecommendationPage(
            videos = filter(items.mapNotNull { it.toVideoItemOrNull() }, filters),
            hasMore = items.isNotEmpty(),
            nextIndex = nextIndex,
        )
    }

    private suspend fun filter(videos: List<VideoItem>, filters: RecommendationFilters): List<VideoItem> {
        val candidates = videos.filterNot { filters.blocksVideo(it) }
        return if (!filters.needsTagLookup) candidates else coroutineScope {
            candidates.map { video ->
                async {
                    video.takeUnless { filters.blocksTags(tags(video)) }
                }
            }.awaitAll().filterNotNull()
        }
    }

    companion object {
        const val PAGE_SIZE = 12
    }
}

internal fun appRecommendationParameters(index: Long, accessKey: String?): Map<String, String> = buildMap {
    put("idx", index.toString())
    put("pull", (index == 0L).toString())
    put("column", "2")
    put("flush", "0")
    put("autoplay_card", "0")
    put("disable_rcmd", "0")
    put("force_host", "2")
    put("https_url_req", "1")
    accessKey?.takeIf { it.isNotBlank() }?.let { put("access_key", it) }
}

internal fun AppFeedItemDto.toVideoItemOrNull(): VideoItem? {
    val videoType = goto ?: cardGoto
    if (videoType !in setOf("av", "vertical_av") || canPlay != 1 ||
        cardGoto?.startsWith("ad_") == true || (adInfo != null && adInfo != JsonNull)) return null
    val aid = playerArgs?.aid?.takeIf { it > 0 } ?: param?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }
        ?: args?.aid?.takeIf { it > 0 } ?: return null
    val reason = when (val value = rcmdReason) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> value["content"]?.jsonPrimitive?.contentOrNull
        else -> null
    }
    return VideoItem(
        aid = aid,
        bvid = bvid?.takeIf { it.isNotBlank() } ?: aidToBvid(aid),
        cid = playerArgs?.cid,
        title = title.orEmpty().ifBlank { "（无标题）" },
        cover = normalizeUrl(cover),
        durationSeconds = playerArgs?.duration ?: parseDurationText(coverRightText),
        authorMid = args?.upId ?: 0,
        authorName = args?.upName.orEmpty(),
        channel = args?.tname.orEmpty(),
        viewCount = parseAppFeedCount(coverLeftText1),
        danmakuCount = parseAppFeedCount(coverLeftText2),
        recommendReason = reason?.takeIf { it.isNotBlank() },
    )
}

internal fun parseAppFeedCount(text: String?): Long {
    val raw = text?.trim()?.replace(",", "").orEmpty()
    val multiplier = when {
        raw.endsWith("亿") -> 100_000_000
        raw.endsWith("万") -> 10_000
        else -> 1
    }
    return ((raw.removeSuffix("亿").removeSuffix("万").toDoubleOrNull() ?: 0.0) * multiplier)
        .toLong().coerceAtLeast(0)
}

internal fun FeedItemDto.toVideoItemOrNull(): VideoItem? {
    val bv = bvid?.takeIf { it.isNotBlank() }
    if (bv == null && id <= 0L) return null
    return VideoItem(
        aid = id,
        bvid = bv.orEmpty(),
        cid = cid,
        title = title.orEmpty().ifBlank { "（无标题）" },
        cover = pic.orEmpty(),
        durationSeconds = duration,
        authorMid = owner?.mid ?: 0,
        authorName = owner?.name.orEmpty(),
        authorFace = owner?.face.orEmpty(),
        channel = tname.orEmpty(),
        viewCount = stat?.view ?: 0,
        danmakuCount = stat?.danmaku ?: 0,
        pubDate = pubdate ?: 0,
        recommendReason = rcmdReason?.content?.takeIf { it.isNotBlank() },
    )
}
