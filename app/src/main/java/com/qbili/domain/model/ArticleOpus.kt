package com.qbili.domain.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class RenderedOpusContent(val html: String, val complete: Boolean)

fun renderArticleOpus(opus: JsonElement): String {
    val paragraphs = (opus as? JsonObject)?.objectValue("content")?.get("paragraphs") as? JsonArray
        ?: return ""
    return renderOpusParagraphs(paragraphs).html
}

fun renderOpusParagraphs(paragraphs: JsonArray): RenderedOpusContent {
    var complete = paragraphs.isNotEmpty()
    val html = buildString {
        paragraphs.forEach { element ->
            val paragraph = element as? JsonObject
            if (paragraph == null) {
                complete = false
                append("<p>此段内容暂不支持显示，可在 B站查看。</p>")
                return@forEach
            }
            val pictures = paragraph.objectValue("pic")?.get("pics") as? JsonArray
            if (pictures != null) {
                pictures.forEach { picture ->
                    val url = (picture as? JsonObject)?.stringValue("url")?.articleImageUrl()
                    if (url == null) {
                        complete = false
                        append("<p>[图片地址暂不可用]</p>")
                        return@forEach
                    }
                    append("<figure><img src='").append(url.escapeOpusHtml()).append("' alt='图片'></figure>")
                }
                return@forEach
            }
            val code = paragraph.objectValue("code")?.stringValue("content")
            if (code != null) {
                append("<pre><code>${code.escapeOpusHtml()}</code></pre>")
                return@forEach
            }
            if (paragraph.containsKey("line") || paragraph.stringValue("para_type") == "3") {
                append("<hr>")
                return@forEach
            }
            val list = paragraph.objectValue("list")
            if (list != null && list["items"] is JsonArray) {
                (list["items"] as JsonArray).forEach { entry ->
                    val item = entry as? JsonObject
                    val order = item?.stringValue("order")?.toIntOrNull()?.takeIf { it > 0 }
                    val tag = if (order != null) "ol" else "ul"
                    val start = order?.let { " start='$it'" }.orEmpty()
                    val rendered = renderOpusParagraphs(JsonArray(listOf(JsonObject(mapOf(
                        "text" to JsonObject(mapOf("nodes" to (item?.get("nodes") ?: JsonArray(emptyList())))),
                    )))))
                    if (!rendered.complete || item == null || item["nodes"] !is JsonArray) complete = false
                    append("<$tag$start><li>${rendered.html.removeSurrounding("<p>", "</p>")}</li></$tag>")
                }
                return@forEach
            }
            val card = paragraph.objectValue("link_card")?.objectValue("card")
            if (card != null) {
                val payload = listOf("ugc", "common", "live", "opus", "music").firstNotNullOfOrNull { card.objectValue(it) }
                val title = payload?.stringValue("title") ?: payload?.stringValue("head_text")
                val link = payload?.stringValue("jump_url")?.articleImageUrl()
                if (title != null) {
                    val label = title.escapeOpusHtml()
                    append(if (link == null) "<p>$label</p>" else "<p><a href='${link.escapeOpusHtml()}'>$label</a></p>")
                    return@forEach
                }
            }
            val nodes = (paragraph.objectValue("heading") ?: paragraph.objectValue("text"))?.get("nodes") as? JsonArray
            if (nodes == null) {
                val plain = paragraph.stringValue("text")
                if (plain != null) append("<p>${plain.escapeOpusHtml().replace("\n", "<br>")}</p>") else {
                    complete = false
                    append("<p>此段内容暂不支持显示，可在 B站查看。</p>")
                }
                return@forEach
            }
            val firstWord = (nodes.firstOrNull() as? JsonObject)?.objectValue("word")
            val level = firstWord?.stringValue("font_level")
            val size = firstWord?.stringValue("font_size")?.toDoubleOrNull()
            val listFormat = paragraph.objectValue("format")?.objectValue("list_format")
            val tag = when {
                listFormat != null -> "li"
                paragraph.stringValue("para_type") == "4" -> "blockquote"
                paragraph.containsKey("heading") -> "h2"
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
                    val item = node as? JsonObject
                    if (item == null) {
                        complete = false
                        append("[暂不支持的内容]")
                        return@forEach
                    }
                    val word = item.objectValue("word")
                    val rich = item.objectValue("rich")
                    val text = word?.stringValue("words") ?: rich?.stringValue("text")
                        ?: rich?.stringValue("orig_text") ?: item.stringValue("text")
                        ?: item.objectValue("formula")?.stringValue("latex_content")
                    val emoji = rich?.objectValue("emoji")?.stringValue("url")?.articleImageUrl()
                    if (emoji != null) {
                        append("<img class='reader-emoji' src='${emoji.escapeOpusHtml()}' alt='${text.orEmpty().escapeOpusHtml()}'>")
                        return@forEach
                    }
                    if (text == null) {
                        complete = false
                        append("[暂不支持的内容]")
                        return@forEach
                    }
                    var formatted = text.escapeOpusHtml().replace("\n", "<br>")
                    val style = word?.objectValue("style") ?: rich?.objectValue("style")
                    if (style?.stringValue("bold") == "true") formatted = "<strong>$formatted</strong>"
                    if (style?.stringValue("italic") == "true") formatted = "<em>$formatted</em>"
                    if (style?.stringValue("underline") == "true") formatted = "<u>$formatted</u>"
                    if (style?.stringValue("strikethrough") == "true") formatted = "<s>$formatted</s>"
                    val color = word?.stringValue("color")?.takeIf { it.matches(Regex("#[0-9a-fA-F]{3,8}")) }
                    if (color != null) formatted = "<span style='color:$color'>$formatted</span>"
                    val mid = rich?.stringValue("rid")?.toLongOrNull()?.takeIf { it > 0 }
                    val link = if (rich?.stringValue("type") == "RICH_TEXT_NODE_TYPE_AT" && mid != null) {
                        "https://space.bilibili.com/$mid"
                    } else rich?.stringValue("jump_url")?.articleImageUrl()
                    if (link != null) formatted = "<a href='${link.escapeOpusHtml()}'>$formatted</a>"
                    append(formatted)
                }
            }
            val order = listFormat?.stringValue("order")?.toIntOrNull()?.takeIf { it > 0 }
            val listTag = if (order != null) "ol" else "ul"
            if (tag == "li") {
                append("<$listTag")
                if (order != null) append(" start='$order'")
                append(">")
            }
            append("<$tag")
            if (alignment != null) append(" style='text-align:$alignment'")
            append(">").append(body.ifBlank { "<br>" }).append("</$tag>")
            if (tag == "li") append("</$listTag>")
        }
    }
    return RenderedOpusContent(html, complete)
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
