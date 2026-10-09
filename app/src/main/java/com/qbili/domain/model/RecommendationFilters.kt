package com.qbili.domain.model

/** 仅作用于首页推荐；标题与视频标签分别按不区分大小写的关键词包含关系匹配。 */
data class RecommendationFilters(
    val titleKeywords: Set<String> = emptySet(),
    val tagKeywords: Set<String> = emptySet(),
    val hiddenVideos: Set<String> = emptySet(),
    val blockedAuthors: Set<Long> = emptySet(),
    val blockedChannels: Set<String> = emptySet(),
) {
    val needsTagLookup: Boolean get() = tagKeywords.isNotEmpty()

    fun blocksTitle(title: String): Boolean =
        titleKeywords.any { keyword -> keyword.isNotBlank() && title.contains(keyword, ignoreCase = true) }

    fun blocksTags(tags: List<String>): Boolean =
        tags.any { tag ->
            tagKeywords.any { keyword -> keyword.isNotBlank() && tag.contains(keyword, ignoreCase = true) }
        }

    fun blocksVideo(video: VideoItem): Boolean =
        blocksTitle(video.title) || video.key in hiddenVideos || video.authorMid in blockedAuthors ||
            blockedChannels.any { channel -> video.channel.isNotBlank() &&
                video.channel.equals(channel, ignoreCase = true) }
}
