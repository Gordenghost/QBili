package com.qbili.domain.model

data class DanmakuTarget(val aid: Long, val bvid: String, val cid: Long, val durationMillis: Long)

data class DanmakuPost(
    val target: DanmakuTarget,
    val message: String,
    val progressMillis: Long,
    val mode: DanmakuMode = DanmakuMode.SCROLL,
    val color: Int = 0xFFFFFF,
) {
    fun validated(): DanmakuPost {
        require(target.aid > 0 && target.cid > 0 && target.bvid.matches(Regex("BV[0-9A-Za-z]{10}"))) {
            "当前视频信息不完整，请重新加载"
        }
        require(target.durationMillis > 0) { "当前分 P 时长不可用，请等待加载完成" }
        val text = message.trim()
        require(text.isNotEmpty()) { "请输入弹幕内容" }
        require(text.codePointCount(0, text.length) <= MAX_LENGTH) { "弹幕最多 $MAX_LENGTH 个字" }
        require(text.none { it.isISOControl() } && text.none { it == '\u2028' || it == '\u2029' }) {
            "弹幕不能包含换行或控制字符"
        }
        require(mode in listOf(DanmakuMode.SCROLL, DanmakuMode.TOP, DanmakuMode.BOTTOM)) { "不支持该弹幕类型" }
        require(color in 0..0xFFFFFF) { "弹幕颜色无效" }
        return copy(message = text, progressMillis = progressMillis.coerceIn(0, target.durationMillis))
    }

    companion object {
        const val MAX_LENGTH = 100
    }
}
