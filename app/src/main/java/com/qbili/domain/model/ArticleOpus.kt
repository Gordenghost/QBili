package com.qbili.domain.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 新版专栏正文在 opus.content.paragraphs；旧 content 可能只有无图的摘要。 */
fun renderArticleOpus(opus: JsonElement): String {
    val paragraphs = (opus as? JsonObject)?.objectValue("content")?.get("paragraphs") as? JsonArray
        ?: return ""
    return buildString {
        paragraphs.forEach { element ->
            val paragraph = element as? JsonObject ?: return@forEach
            val pictures = paragraph.objectValue("pic")?.get("pics") as? JsonArray
            if (pictures != null) {
                pictures.forEach { picture ->
                    val url = (picture as? JsonObject)?.stringValue("url")?.articleImageUrl() ?: return@forEach
                    append("<figure><img src='").append(url.escapeOpusHtml()).append("' alt='图片'></figure>")
                }
                return@forEach
            }
            val nodes = paragraph.objectValue("text")?.get("nodes") as? JsonArray ?: return@forEach
            val firstWord = (nodes.firstOrNull() as? JsonObject)?.objectValue("word")
            val level = firstWord?.stringValue("font_level")
            val size = firstWord?.stringValue("font_size")?.toIntOrNull()
            val listFormat = paragraph.objectValue("format")?.objectValue("list_format")
            val tag = when {
                listFormat != null -> "li"
                level == "xLarge" || (size != null && size >= 22) -> "h2"
                level == "large" || (size != null && size >= 20) -> "h3"
                else -> "p"
            }
            val alignment = when (paragraph.stringValue("align")) {
                "1", "center" -> "center"
                "2", "right" -> "right"
                "3", "justify" -> "justify"
                else -> null
            }
            val body = buildString {
                nodes.forEach { node ->
                    val item = node as? JsonObject ?: return@forEach
                    val word = item.objectValue("word")
                    val text = word?.stringValue("words") ?: item.stringValue("text") ?: return@forEach
                    var formatted = text.escapeOpusHtml().replace("\n", "<br>")
                    val style = word?.objectValue("style")
                    if (style?.stringValue("bold") == "true") formatted = "<strong>$formatted</strong>"
                    if (style?.stringValue("italic") == "true") formatted = "<em>$formatted</em>"
                    if (style?.stringValue("underline") == "true") formatted = "<u>$formatted</u>"
                    if (style?.stringValue("strikethrough") == "true") formatted = "<s>$formatted</s>"
                    val color = word?.stringValue("color")?.takeIf { it.matches(Regex("#[0-9a-fA-F]{3,8}")) }
                    if (color != null) formatted = "<span style='color:$color'>$formatted</span>"
                    append(formatted)
                }
            }
            if (body.isBlank()) return@forEach
            val order = listFormat?.stringValue("order")?.toIntOrNull()?.takeIf { it > 0 }
            val listTag = if (order != null) "ol" else "ul"
            if (tag == "li") {
                append("<$listTag")
                if (order != null) append(" start='$order'")
                append(">")
            }
            append("<$tag")
            if (alignment != null) append(" style='text-align:$alignment'")
            append(">").append(body).append("</$tag>")
            if (tag == "li") append("</$listTag>")
        }
    }
}

private fun JsonObject.objectValue(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.stringValue(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

private fun String.articleImageUrl(): String? = when {
    startsWith("//") -> "https:$this"
    startsWith("https://") -> this
    startsWith("http://") -> "https://${removePrefix("http://")}"
    else -> null
}

private fun String.escapeOpusHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;")
    .replace("'", "&#39;")
