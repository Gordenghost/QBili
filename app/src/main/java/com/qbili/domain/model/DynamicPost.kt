package com.qbili.domain.model

data class DynamicPost(
    val id: String,
    val authorMid: Long,
    val authorName: String,
    val authorFace: String,
    val text: String,
    val cover: String,
    val publishedAt: Long,
    val video: VideoItem? = null,
    val images: List<String> = emptyList(),
    val opusId: String? = null,
    val articleId: Long? = null,
)

/** 动态正文中的图片占位符与实际图片分开排版，避免标记夹在句子中间。 */
fun formatDynamicText(text: String): String = text
    .replace(Regex("""[ \t]*(?:\[图片\]|【图片】)[ \t]*"""), "\n[图片]\n")
    .replace(Regex("""\n+\[图片\]\n+"""), "\n[图片]\n")
    .trim()
