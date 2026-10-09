package com.qbili.domain.model

/** 当前用户对某个视频的互动状态 */
data class VideoInteraction(
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val favorited: Boolean = false,
    /** 已投币数 0-2 */
    val coinCount: Int = 0,
    val following: Boolean = false,
    /** archive/relation 不返回这个字段，视频页另查稍后再看列表后同步。 */
    val inWatchLater: Boolean = false,
) {
    fun withLike(value: Boolean): VideoInteraction = copy(
        liked = value,
        disliked = if (value) false else disliked,
    )

    fun withDislike(value: Boolean): VideoInteraction = copy(
        disliked = value,
        liked = if (value) false else liked,
    )
}

/** 收藏夹（用于收藏时的多选） */
data class FavFolder(
    val id: Long,
    val title: String,
    val mediaCount: Int,
    val isPrivate: Boolean,
    /** 该视频当前是否已在这个收藏夹里 */
    val containsVideo: Boolean,
)

data class FavoriteChanges(val addIds: List<Long>, val removeIds: List<Long>)

fun favoriteChanges(folders: List<FavFolder>, selectedIds: Set<Long>): FavoriteChanges {
    val knownIds = folders.map { it.id }.toSet()
    val previouslySelected = folders.filter { it.containsVideo }.map { it.id }.toSet()
    val selected = selectedIds intersect knownIds
    return FavoriteChanges(
        addIds = (selected - previouslySelected).toList(),
        removeIds = (previouslySelected - selected).toList(),
    )
}
