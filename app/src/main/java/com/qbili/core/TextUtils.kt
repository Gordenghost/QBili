package com.qbili.core

/**
 * 搜索接口会把命中的关键词包成 `<em class="keyword">原神</em>`，
 * 并且文案里可能带 HTML 实体，直接显示会露出标签。
 */
fun stripHtml(input: String?): String {
    val raw = input ?: return ""
    if (raw.isEmpty()) return ""
    val sb = StringBuilder(raw.length)
    var inTag = false
    for (c in raw) {
        when {
            c == '<' -> inTag = true
            c == '>' -> inTag = false
            !inTag -> sb.append(c)
        }
    }
    return sb.toString()
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .trim()
}

/**
 * 搜索结果里的 duration 是 `"1:1"` / `"12:34"` / `"1:02:03"` 这种字符串，
 * 而推荐流给的是整数秒。统一转成秒。
 *
 * 注意 `"1:1"` 表示 1 分 1 秒，不是 1 小时 1 分。
 */
fun parseDurationText(text: String?): Int {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return 0
    // 已经是纯数字秒数
    raw.toIntOrNull()?.let { return it.coerceAtLeast(0) }

    val parts = raw.split(':')
    if (parts.size !in 2..3) return 0
    val nums = parts.map { it.trim().toIntOrNull() ?: return 0 }
    return when (nums.size) {
        2 -> nums[0] * 60 + nums[1]
        else -> nums[0] * 3600 + nums[1] * 60 + nums[2]
    }.coerceAtLeast(0)
}

/** `//i1.hdslb.com/xxx.jpg` -> `https://i1.hdslb.com/xxx.jpg` */
fun normalizeUrl(url: String?): String {
    val raw = url?.trim().orEmpty()
    return when {
        raw.isEmpty() -> ""
        raw.startsWith("//") -> "https:$raw"
        raw.startsWith("http://") -> "https://" + raw.removePrefix("http://")
        else -> raw
    }
}
