package com.qbili.domain.model

/** 弹幕类型。1/2/3 都是滚动弹幕，B 站历史上区分过大小，现在等价。 */
enum class DanmakuMode(val raw: Int) {
    SCROLL(1),
    BOTTOM(4),
    TOP(5),
    REVERSE(6),
    ADVANCED(7),
    CODE(8),
    BAS(9),
    UNKNOWN(0),
    ;

    companion object {
        fun from(raw: Int): DanmakuMode = when (raw) {
            1, 2, 3 -> SCROLL
            4 -> BOTTOM
            5 -> TOP
            6 -> REVERSE
            7 -> ADVANCED
            8 -> CODE
            9 -> BAS
            else -> UNKNOWN
        }
    }
}

/**
 * 一条弹幕。
 *
 * 字段编号来自对真实 seg.so 响应的实测，见 DanmakuParser 的注释。
 */
data class DanmakuItem(
    val id: Long,
    /** 出现时刻，相对视频起点的毫秒数 */
    val progressMillis: Int,
    val mode: DanmakuMode,
    val fontSize: Int,
    /** RGB，不含 alpha */
    val color: Int,
    /** 发送者 mid 的哈希，用于「屏蔽此用户」而不暴露 mid */
    val senderHash: String,
    val content: String,
    val sendTimeSeconds: Long,
    /**
     * 权重 1-10，B 站的「智能云屏蔽」等级就是按它过滤：
     * 屏蔽等级设为 N 表示只显示 weight >= N 的弹幕。
     */
    val weight: Int,
) {
    /** 高级弹幕/代码弹幕/BAS 弹幕需要脚本引擎，普通播放器直接忽略 */
    val isRenderable: Boolean
        get() = mode == DanmakuMode.SCROLL ||
            mode == DanmakuMode.TOP ||
            mode == DanmakuMode.BOTTOM ||
            mode == DanmakuMode.REVERSE
}
