package com.qbili.data.repository

import com.qbili.core.BiliRiskControlException
import com.qbili.core.normalizeUrl
import com.qbili.core.parseDurationText
import com.qbili.core.stripHtml
import com.qbili.data.remote.api.AppSearchApi
import com.qbili.data.remote.api.SearchApi
import com.qbili.data.remote.api.SuggestApi
import com.qbili.data.remote.dto.AppSearchItemDto
import com.qbili.data.remote.dto.AppSearchType
import com.qbili.data.remote.dto.SearchResultItemDto
import com.qbili.data.remote.dto.SearchTypeDataDto
import com.qbili.data.remote.dto.requireData
import com.qbili.data.session.GaiaTokenStore
import com.qbili.domain.model.ArticleItem
import com.qbili.domain.model.DurationFilter
import com.qbili.domain.model.HotSearchItem
import com.qbili.domain.model.LiveRoomItem
import com.qbili.domain.model.PartitionFilter
import com.qbili.domain.model.SearchOrder
import com.qbili.domain.model.SearchPage
import com.qbili.domain.model.SearchType
import com.qbili.domain.model.SeasonItem
import com.qbili.domain.model.UserItem
import com.qbili.domain.model.VideoItem
import java.net.URLEncoder

class SearchRepository(
    private val searchApi: SearchApi,
    private val appSearchApi: AppSearchApi,
    private val suggestApi: SuggestApi,
    private val gaiaTokenStore: GaiaTokenStore,
) {

    /**
     * 统一走 Web 端分类搜索。
     *
     * 之前多词关键词会被 Gaia 风控拦下（返回 v_voucher），根因不是关键词本身，
     * 而是请求「不像真的网页搜索」。实测补上 platform=pc、web_location=1430654，
     * 并把 Referer/Origin 指向 search.bilibili.com 之后，风控就不再触发；
     * 配合启动时的 buvid 激活（AccountApi.activateBuvid）更稳。
     */
    private suspend fun webSearch(
        searchType: String,
        keyword: String,
        page: Int,
        order: String? = null,
        duration: Int? = null,
        tids: Int? = null,
    ): SearchTypeDataDto = searchApi.searchByType(
        searchType = searchType,
        keyword = keyword,
        order = order,
        duration = duration,
        tids = tids,
        page = page,
        pageSize = PAGE_SIZE,
        platform = SEARCH_PLATFORM,
        webLocation = SEARCH_WEB_LOCATION,
        gaiaVtoken = gaiaTokenStore.token,
        referer = searchReferer(searchType, keyword),
        origin = SEARCH_ORIGIN,
    ).requireData()

    /**
     * 视频搜索。
     *
     * 以 Web 接口为主：只有它的 order / duration / tids 筛选是真实生效的
     * （移动端 `/x/v2/search/type` 实测会忽略这三个参数）。
     * 万一仍被风控拦下，在**没有使用筛选**的前提下退到移动端接口兜底；
     * 用了筛选就把异常抛给上层走验证流程——否则会悄悄忽略用户选的条件，
     * 界面显示按播放量排序、实际却是综合排序，那比报错更糟。
     */
    suspend fun searchVideos(
        keyword: String,
        order: SearchOrder,
        duration: DurationFilter,
        partition: PartitionFilter,
        page: Int,
    ): SearchPage<VideoItem> {
        val usesFilters = order != SearchOrder.TOTAL_RANK ||
            duration != DurationFilter.ALL ||
            partition != PartitionFilter.ALL

        return try {
            searchVideosViaWeb(keyword, order, duration, partition, page)
        } catch (e: BiliRiskControlException) {
            if (usesFilters) throw e else searchVideosViaApp(keyword, page)
        }
    }

    private suspend fun searchVideosViaApp(keyword: String, page: Int): SearchPage<VideoItem> {
        val data = appSearchApi.searchByType(
            keyword = keyword,
            type = AppSearchType.VIDEO,
            page = page,
            pageSize = PAGE_SIZE,
        ).requireData()

        // 无结果时接口会塞进 goto=hot_recommend 的「为你推荐」占位卡，必须过滤掉
        val items = data.items
            .filter { it.goto == GOTO_VIDEO }
            .mapNotNull { it.toVideoItemOrNull() }

        return SearchPage(
            items = items,
            page = page,
            hasMore = items.isNotEmpty() && page < data.pages,
            totalResults = data.total,
        )
    }

    private suspend fun searchVideosViaWeb(
        keyword: String,
        order: SearchOrder,
        duration: DurationFilter,
        partition: PartitionFilter,
        page: Int,
    ): SearchPage<VideoItem> {
        val data = webSearch(
            searchType = SearchType.VIDEO.value,
            keyword = keyword,
            page = page,
            order = order.value,
            duration = duration.value,
            tids = partition.tid,
        )

        val items = data.resultOrThrow()
            .filter { it.type == null || it.type == "video" }
            .map { it.toVideoItem() }

        return SearchPage(
            items = items,
            page = page,
            hasMore = items.isNotEmpty() && page < data.numPages,
            totalResults = data.numResults,
        )
    }

    suspend fun searchUsers(keyword: String, page: Int): SearchPage<UserItem> {
        val data = webSearch(SearchType.USER.value, keyword, page)
        val items = data.resultOrThrow().map { it.toUserItem() }
        return SearchPage(items, page, items.isNotEmpty() && page < data.numPages, data.numResults)
    }

    suspend fun searchLiveRooms(keyword: String, page: Int): SearchPage<LiveRoomItem> {
        val data = webSearch(SearchType.LIVE_ROOM.value, keyword, page)
        val items = data.resultOrThrow().map { it.toLiveRoomItem() }
        return SearchPage(items, page, items.isNotEmpty() && page < data.numPages, data.numResults)
    }

    /**
     * 番剧与影视。两者的返回结构完全一致，只有 search_type 不同。
     *
     * 注意：无结果时接口返回的 `result` 是 null 而不是空数组，
     * numResults 也为 0，所以不能靠 result 非空来判断成功。
     */
    suspend fun searchSeasons(
        keyword: String,
        type: SearchType,
        page: Int,
    ): SearchPage<SeasonItem> {
        require(type.isSeason) { "searchSeasons 只接受番剧或影视类型，收到 $type" }
        val data = webSearch(type.value, keyword, page)
        val items = data.resultOrThrow().map { it.toSeasonItem() }
        return SearchPage(items, page, items.isNotEmpty() && page < data.numPages, data.numResults)
    }

    suspend fun searchArticles(keyword: String, page: Int): SearchPage<ArticleItem> {
        val data = webSearch(SearchType.ARTICLE.value, keyword, page)
        val items = data.resultOrThrow().map { it.toArticleItem() }
        return SearchPage(items, page, items.isNotEmpty() && page < data.numPages, data.numResults)
    }

    suspend fun hotSearch(limit: Int = 10): List<HotSearchItem> =
        searchApi.searchSquare(limit = limit, platform = "web")
            .requireData()
            .trending
            ?.list
            .orEmpty()
            .mapNotNull { dto ->
                val keyword = dto.keyword?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HotSearchItem(
                    keyword = keyword,
                    showName = dto.showName?.takeIf { it.isNotBlank() } ?: keyword,
                    icon = normalizeUrl(dto.icon),
                )
            }

    suspend fun suggest(term: String): List<String> {
        if (term.isBlank()) return emptyList()
        return suggestApi.suggest(
            term = term,
            mainVer = "v1",
            referer = SUGGEST_REFERER,
        ).result?.tag.orEmpty()
            .mapNotNull { it.value ?: it.term }
            .map { stripHtml(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    companion object {
        const val PAGE_SIZE = 20
        private const val SUGGEST_REFERER = "https://search.bilibili.com/"
        private const val GOTO_VIDEO = "av"

        /** 以下三项缺一就容易被 Gaia 风控拦下，实测确认 */
        private const val SEARCH_PLATFORM = "pc"
        private const val SEARCH_WEB_LOCATION = "1430654"
        private const val SEARCH_ORIGIN = "https://search.bilibili.com"

        /** 伪造成真实搜索页地址：`https://search.bilibili.com/video?keyword=...` */
        private fun searchReferer(searchType: String, keyword: String): String {
            val encoded = URLEncoder.encode(keyword, "UTF-8").replace("+", "%20")
            return "$SEARCH_ORIGIN/$searchType?keyword=$encoded"
        }
    }
}

/**
 * 移动端搜索结果 -> VideoItem。
 *
 * 两处与 Web 端不同：主键在 [AppSearchItemDto.param] 里且是 **aid 而非 bvid**，
 * duration 是 "23:57" 字符串。没有 bvid 不影响播放——playurl 接受 avid。
 */
private fun AppSearchItemDto.toVideoItemOrNull(): VideoItem? {
    val aid = param?.trim()?.toLongOrNull() ?: return null
    if (aid <= 0) return null
    return VideoItem(
        aid = aid,
        bvid = "",
        title = stripHtml(title).ifBlank { "（无标题）" },
        cover = normalizeUrl(cover),
        durationSeconds = parseDurationText(duration),
        authorMid = mid,
        authorName = stripHtml(author ?: name),
        authorFace = normalizeUrl(face),
        viewCount = play,
        danmakuCount = danmaku,
        pubDate = if (pubDate != 0L) pubDate else ptime,
        recommendReason = null,
    )
}

private fun SearchResultItemDto.toVideoItem() = VideoItem(
    aid = if (aid != 0L) aid else id,
    bvid = bvid.orEmpty(),
    title = stripHtml(title).ifBlank { "（无标题）" },
    cover = normalizeUrl(pic),
    durationSeconds = parseDurationText(duration),
    authorMid = mid,
    authorName = stripHtml(author),
    authorFace = normalizeUrl(upic),
    viewCount = play,
    danmakuCount = danmaku,
    pubDate = if (pubdate != 0L) pubdate else senddate,
    recommendReason = null,
)

private fun SearchResultItemDto.toUserItem() = UserItem(
    mid = mid,
    name = stripHtml(uname ?: author),
    avatar = normalizeUrl(upic ?: uface),
    sign = stripHtml(usign),
    fans = fans,
    videoCount = videos,
    level = level,
    officialVerify = officialVerify?.desc?.takeIf { it.isNotBlank() && officialVerify.type >= 0 },
    isLive = isLive,
    roomId = roomId.takeIf { it != 0L },
)

private fun SearchResultItemDto.toLiveRoomItem() = LiveRoomItem(
    roomId = if (roomid != 0L) roomid else id,
    title = stripHtml(title),
    cover = normalizeUrl(cover ?: userCover ?: pic),
    uname = stripHtml(uname ?: author),
    mid = if (uid != 0L) uid else mid,
    areaName = cateName.orEmpty(),
    online = online,
    isLiving = liveStatus == 1,
)

private fun SearchResultItemDto.toSeasonItem() = SeasonItem(
    seasonId = seasonId,
    mediaId = mediaId,
    title = stripHtml(title).ifBlank { "（无标题）" },
    cover = normalizeUrl(cover),
    typeName = seasonTypeName.orEmpty(),
    areas = areas.orEmpty(),
    styles = styles.orEmpty(),
    score = mediaScore?.score ?: 0.0,
    scoreUserCount = mediaScore?.userCount ?: 0,
    // 电影没有集数进度，退化成 "全 N 话"
    indexShow = indexShow?.takeIf { it.isNotBlank() }
        ?: epSize.takeIf { it > 0 }?.let { "全 $it 话" }.orEmpty(),
    description = stripHtml(desc),
    cast = cv.orEmpty(),
    pubTime = pubtime,
    playUrl = (url ?: gotoUrl).orEmpty(),
)

private fun SearchResultItemDto.toArticleItem() = ArticleItem(
    id = id,
    title = stripHtml(title).ifBlank { "（无标题）" },
    summary = stripHtml(desc ?: description),
    cover = normalizeUrl(imageUrls.firstOrNull()),
    authorName = stripHtml(author),
    authorMid = mid,
    viewCount = view,
    likeCount = like,
    replyCount = reply,
    pubTime = if (pubTime != 0L) pubTime else pubdate,
    categoryName = categoryName.orEmpty(),
)

/**
 * 取出结果列表，遇到 Gaia 风控就抛出去。
 *
 * 关键点：风控响应的 code 是 0、message 是 "OK"，只是 result 变成 null。
 * 如果这里退化成 emptyList()，界面会显示「没有找到相关内容」，
 * 用户会以为是搜不到，实际上是需要完成验证。
 */
private fun SearchTypeDataDto.resultOrThrow(): List<SearchResultItemDto> {
    val voucher = vVoucher
    if (result == null && !voucher.isNullOrBlank()) throw BiliRiskControlException(voucher)
    return result.orEmpty()
}
