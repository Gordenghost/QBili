package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * search/type 的 result 是异构数组：同一个字段名在不同 search_type 下含义不同，
 * 所以这里用一个宽松的联合 DTO 承接，由 Repository 按 type 分派映射。
 */
@Serializable
data class SearchResultItemDto(
    val type: String? = null,

    // --- 视频 ---
    val id: Long = 0,
    val aid: Long = 0,
    val bvid: String? = null,
    /** 带 <em class="keyword"> 高亮标签，展示前必须 stripHtml */
    val title: String? = null,
    val author: String? = null,
    val mid: Long = 0,
    /** UP 主头像（视频结果用 upic，用户结果用 upic/uface） */
    val upic: String? = null,
    /** 封面，协议相对 URL（//i1.hdslb.com/...） */
    val pic: String? = null,
    val play: Long = 0,
    val danmaku: Long = 0,
    val review: Long = 0,
    val favorites: Long = 0,
    /** 字符串形式的时长，如 "1:1" / "1:02:03"，不是秒数 */
    val duration: String? = null,
    val pubdate: Long = 0,
    val senddate: Long = 0,
    val typename: String? = null,
    val tag: String? = null,
    val description: String? = null,

    // --- 用户 (bili_user) ---
    val uname: String? = null,
    val uface: String? = null,
    val usign: String? = null,
    val fans: Long = 0,
    val videos: Long = 0,
    val level: Int = 0,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("room_id") val roomId: Long = 0,
    @SerialName("official_verify") val officialVerify: OfficialVerifyDto? = null,

    // --- 直播间 (live_room) ---
    val roomid: Long = 0,
    val cover: String? = null,
    @SerialName("user_cover") val userCover: String? = null,
    @SerialName("cate_name") val cateName: String? = null,
    val online: Long = 0,
    @SerialName("live_status") val liveStatus: Int = 0,
    val uid: Long = 0,

    // --- 番剧 / 影视 (media_bangumi / media_ft) ---
    @SerialName("media_id") val mediaId: Long = 0,
    @SerialName("season_id") val seasonId: Long = 0,
    /** 站内播放地址 */
    val url: String? = null,
    @SerialName("goto_url") val gotoUrl: String? = null,
    @SerialName("season_type_name") val seasonTypeName: String? = null,
    /** "中国大陆" */
    val areas: String? = null,
    /** "科幻/冒险/灾难" */
    val styles: String? = null,
    /** 主演 / 声优列表，用 / 分隔 */
    val cv: String? = null,
    val staff: String? = null,
    /** 简介，注意与 video 的 description 是不同字段 */
    val desc: String? = null,
    @SerialName("ep_size") val epSize: Int = 0,
    @SerialName("index_show") val indexShow: String? = null,
    val pubtime: Long = 0,
    @SerialName("media_score") val mediaScore: MediaScoreDto? = null,

    // --- 专栏 (article) ---
    /** 文章浏览量 */
    val view: Long = 0,
    val like: Long = 0,
    val reply: Long = 0,
    @SerialName("pub_time") val pubTime: Long = 0,
    @SerialName("category_name") val categoryName: String? = null,
    /** 封面列表，协议相对 URL */
    @SerialName("image_urls") val imageUrls: List<String> = emptyList(),
)

@Serializable
data class MediaScoreDto(
    val score: Double = 0.0,
    @SerialName("user_count") val userCount: Long = 0,
)

@Serializable
data class OfficialVerifyDto(
    val type: Int = -1,
    val desc: String? = null,
)

@Serializable
data class SearchTypeDataDto(
    val result: List<SearchResultItemDto>? = null,
    @SerialName("numResults") val numResults: Int = 0,
    @SerialName("numPages") val numPages: Int = 0,
    val page: Int = 1,
    /**
     * 触发 Gaia 风控时出现：code 仍是 0、message 仍是 "OK"，但 result 变成 null，
     * 只给这一个凭据。必须与「真的没有结果」区分开。
     */
    @SerialName("v_voucher") val vVoucher: String? = null,
)

// --- 热搜 ---

@Serializable
data class SearchSquareDataDto(
    val trending: TrendingDto? = null,
)

@Serializable
data class TrendingDto(
    val title: String? = null,
    val list: List<TrendingItemDto> = emptyList(),
)

@Serializable
data class TrendingItemDto(
    val keyword: String? = null,
    @SerialName("show_name") val showName: String? = null,
    val icon: String? = null,
    @SerialName("heat_score") val heatScore: Long = 0,
)

// --- 搜索建议（s.search.bilibili.com，外层结构与主站不同，没有 code/data 包装的 data 字段） ---

@Serializable
data class SuggestResponseDto(
    val code: Int = 0,
    val result: SuggestResultDto? = null,
)

@Serializable
data class SuggestResultDto(
    val tag: List<SuggestTagDto> = emptyList(),
)

@Serializable
data class SuggestTagDto(
    val value: String? = null,
    val term: String? = null,
    /** 带 <em class="suggest_high_light"> 高亮 */
    val name: String? = null,
)
