package com.qbili.domain.model

enum class RecommendationFilterGroup(val key: String, val title: String, val description: String) {
    TITLE("title", "标题关键词屏蔽", "标题中包含关键词的视频不出现在首页（不区分大小写）。"),
    TAG("tag", "Tag 屏蔽", "标签名称中包含关键词的视频不出现在首页（不区分大小写）。"),
    CHANNEL("channel", "频道屏蔽", "屏蔽所选频道的首页推荐；也可长按视频卡片添加频道。"),
    ;

    fun keywords(filters: RecommendationFilters): Set<String> = when (this) {
        TITLE -> filters.titleKeywords
        TAG -> filters.tagKeywords
        CHANNEL -> filters.blockedChannels
    }
}
