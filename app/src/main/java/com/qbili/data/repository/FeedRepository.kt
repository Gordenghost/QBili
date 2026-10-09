package com.qbili.data.repository

import com.qbili.data.remote.api.FeedApi
import com.qbili.data.remote.api.VideoTagApi
import com.qbili.data.remote.dto.FeedItemDto
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.RecommendationFilters
import com.qbili.domain.model.VideoItem
import com.qbili.core.QBiliLog
import java.util.Collections
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class RecommendationPage(val videos: List<VideoItem>, val hasMore: Boolean)

class FeedRepository(private val api: FeedApi, private val videoTagApi: VideoTagApi) {

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
        val candidates = data.item
            // 推荐流里混有番剧、图文、直播等条目，当前只展示普通视频
            .filter { it.goto == null || it.goto == "av" }
            .mapNotNull { it.toVideoItemOrNull() }
            .filterNot { filters.blocksVideo(it) }

        // 有 Tag 规则时必须查到每个候选视频的实际标签；任何查询失败都会让整页报错，不能放行未校验视频。
        val videos = if (!filters.needsTagLookup) candidates else coroutineScope {
            candidates.map { video ->
                async {
                    video.takeUnless { filters.blocksTags(tags(video)) }
                }
            }.awaitAll().filterNotNull()
        }
        return RecommendationPage(videos, hasMore = data.item.isNotEmpty())
    }

    companion object {
        const val PAGE_SIZE = 12
    }
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
