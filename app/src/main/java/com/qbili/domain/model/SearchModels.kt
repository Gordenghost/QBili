package com.qbili.domain.model

/** 搜索结果排序，取值对应接口的 order 参数（已逐个实测可用） */
enum class SearchOrder(val value: String, val label: String) {
    TOTAL_RANK("totalrank", "综合排序"),
    CLICK("click", "最多播放"),
    PUB_DATE("pubdate", "最新发布"),
    DANMAKU("dm", "最多弹幕"),
    FAVORITE("stow", "最多收藏"),
    SCORES("scores", "最多评论"),
}

/** 时长筛选，对应 duration 参数 */
enum class DurationFilter(val value: Int, val label: String) {
    ALL(0, "全部时长"),
    UNDER_10M(1, "10 分钟以下"),
    FROM_10_TO_30M(2, "10-30 分钟"),
    FROM_30_TO_60M(3, "30-60 分钟"),
    OVER_60M(4, "60 分钟以上"),
}

/** 搜索的内容类型，对应 search_type 参数 */
enum class SearchType(val value: String, val label: String) {
    VIDEO("video", "视频"),
    BANGUMI("media_bangumi", "番剧"),
    MOVIE("media_ft", "影视"),
    USER("bili_user", "用户"),
    LIVE_ROOM("live_room", "直播"),
    ARTICLE("article", "专栏"),
    ;

    /** 番剧与影视是同一套 PGC 结构，映射逻辑共用 */
    val isSeason: Boolean get() = this == BANGUMI || this == MOVIE
}

/** 主分区筛选，对应 tids 参数。0 表示全部分区。 */
enum class PartitionFilter(val tid: Int, val label: String) {
    ALL(0, "全部分区"),
    DOUGA(1, "动画"),
    MUSIC(3, "音乐"),
    DANCE(129, "舞蹈"),
    GAME(4, "游戏"),
    KNOWLEDGE(36, "知识"),
    TECH(188, "科技"),
    SPORTS(234, "运动"),
    CAR(223, "汽车"),
    LIFE(160, "生活"),
    FOOD(211, "美食"),
    ANIMAL(217, "动物圈"),
    KICHIKU(119, "鬼畜"),
    FASHION(155, "时尚"),
    ENT(5, "娱乐"),
    CINEPHILE(181, "影视"),
}

/** 用户搜索结果 */
data class UserItem(
    val mid: Long,
    val name: String,
    val avatar: String,
    val sign: String = "",
    val fans: Long = 0,
    val videoCount: Long = 0,
    val level: Int = 0,
    val officialVerify: String? = null,
    val isLive: Boolean = false,
    val roomId: Long? = null,
)

/** 直播间搜索结果 / 直播列表项 */
data class LiveRoomItem(
    val roomId: Long,
    val title: String,
    val cover: String,
    val uname: String,
    val mid: Long = 0,
    val areaName: String = "",
    val online: Long = 0,
    val isLiving: Boolean = true,
)

/** 一页搜索结果 */
data class SearchPage<T>(
    val items: List<T>,
    val page: Int,
    val hasMore: Boolean,
    val totalResults: Int = 0,
)

/** 热搜条目 */
data class HotSearchItem(
    val keyword: String,
    val showName: String,
    val icon: String = "",
)

/** 番剧 / 影视（PGC）条目 */
data class SeasonItem(
    val seasonId: Long,
    val mediaId: Long,
    val title: String,
    val cover: String,
    /** "番剧" / "电影" / "电视剧" 等 */
    val typeName: String = "",
    /** "中国大陆" */
    val areas: String = "",
    /** "科幻/冒险/灾难" */
    val styles: String = "",
    /** 评分，0 表示暂无评分 */
    val score: Double = 0.0,
    val scoreUserCount: Long = 0,
    /** "全13话" 之类的进度描述 */
    val indexShow: String = "",
    val description: String = "",
    /** 主演 / 声优 */
    val cast: String = "",
    val pubTime: Long = 0,
    /** 站内播放地址，形如 https://www.bilibili.com/bangumi/play/ep744327 */
    val playUrl: String = "",
)

/** 专栏文章条目 */
data class ArticleItem(
    /** cvid */
    val id: Long,
    val title: String,
    val summary: String = "",
    val cover: String = "",
    val authorName: String = "",
    val authorMid: Long = 0,
    val viewCount: Long = 0,
    val likeCount: Long = 0,
    val replyCount: Long = 0,
    val pubTime: Long = 0,
    val categoryName: String = "",
)
