package com.qbili.domain.model

/**
 * 弹幕显示设置。
 *
 * [minWeight] 对应 B 站的「智能云屏蔽」等级：只显示 weight >= 该值的弹幕，
 * 服务端已经给每条弹幕打好了 1-10 的权重（见 [DanmakuItem.weight]）。
 */
data class DanmakuSettings(
    val enabled: Boolean = true,
    /** 字号倍率 */
    val fontScale: Float = 1.0f,
    /** 不透明度 0.1~1.0 */
    val opacity: Float = 0.9f,
    /** 滚动速度倍率，越大越快 */
    val speedFactor: Float = 1.0f,
    /** 显示区域占播放器高度的比例 */
    val displayAreaRatio: Float = 0.5f,
    /** 屏蔽等级 0~10，0 为不屏蔽 */
    val minWeight: Int = 0,
    /** 关键词屏蔽，命中即不显示 */
    val blockedKeywords: List<String> = emptyList(),
) {
    /** 一条滚动弹幕从右侧进入到完全离开左侧的时长 */
    val scrollDurationMillis: Int
        get() = (BASE_SCROLL_MILLIS / speedFactor).toInt().coerceIn(2_000, 24_000)

    /** 顶部/底部弹幕的停留时长 */
    val staticDurationMillis: Int
        get() = (BASE_STATIC_MILLIS / speedFactor).toInt().coerceIn(1_500, 12_000)

    fun isBlocked(item: DanmakuItem): Boolean {
        if (item.weight < minWeight) return true
        if (blockedKeywords.isEmpty()) return false
        return blockedKeywords.any { keyword ->
            keyword.isNotBlank() && item.content.contains(keyword, ignoreCase = true)
        }
    }

    private companion object {
        const val BASE_SCROLL_MILLIS = 8_000f
        const val BASE_STATIC_MILLIS = 4_000f
    }
}
