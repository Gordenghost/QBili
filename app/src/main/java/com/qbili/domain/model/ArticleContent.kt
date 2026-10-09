package com.qbili.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 新版专栏使用 Quill ops 而非 HTML；先还原结构，旧版 HTML 保持原样。 */
fun renderArticleContent(content: String): String {
    val trimmed = content.trim()
    if (trimmed.startsWith("<")) return content
    if (!trimmed.startsWith("{")) return content.split('\n')
        .joinToString("") { "<p>${it.escapeArticleHtml().ifEmpty { "<br>" }}</p>" }

    val ops = (Json.parseToJsonElement(trimmed) as? JsonObject)?.get("ops") as? JsonArray
        ?: throw IllegalArgumentException("专栏正文格式不受支持")
    val html = StringBuilder()
    val line = StringBuilder()
    var afterImage = false
    var lineAttributes: JsonObject? = null

    fun flush(attributes: JsonObject? = null) {
        if (line.isEmpty() && afterImage) {
            afterImage = false
            return
        }
        val contents = line.toString().ifEmpty { "<br>" }
        val blockAttributes = attributes?.takeIf { it.hasBlockFormatting() } ?: lineAttributes
        val header = blockAttributes?.text("header")?.toIntOrNull()?.takeIf { it in 1..6 }
        val list = blockAttributes?.text("list")
        val tag = when {
            header != null -> "h$header"
            list == "bullet" || list == "ordered" -> "li"
            blockAttributes?.text("blockquote") == "true" -> "blockquote"
            else -> "p"
        }
        val alignment = blockAttributes?.text("align")?.takeIf { it in setOf("center", "right", "justify") }
        val style = alignment?.let { " style='text-align:$it'" }.orEmpty()
        val item = "<$tag$style>$contents</$tag>"
        html.append(when (list) {
            "bullet" -> "<ul>$item</ul>"
            "ordered" -> "<ol>$item</ol>"
            else -> item
        })
        line.clear()
        afterImage = false
        lineAttributes = null
    }

    ops.forEach { opElement ->
        val op = opElement as? JsonObject ?: return@forEach
        val attributes = (op["attributes"] ?: op["attribute"]) as? JsonObject
        when (val insert = op["insert"]) {
            is JsonPrimitive -> {
                val parts = insert.content.split('\n')
                parts.forEachIndexed { index, part ->
                    if (part.isNotEmpty()) {
                        line.append(part.escapeArticleHtml().withArticleFormatting(attributes))
                        if (attributes?.hasBlockFormatting() == true) lineAttributes = attributes
                        afterImage = false
                    }
                    if (index < parts.lastIndex) flush(attributes)
                }
            }
            is JsonObject -> {
                if (line.isNotEmpty()) flush()
                val media = listOf("native-image", "cut-off", "video-card", "article-card")
                    .firstNotNullOfOrNull { insert[it] as? JsonObject }
                val url = media?.text("url")?.safeArticleUrl()
                if (url != null) {
                    html.append("<figure><img src='${url.escapeArticleHtml()}' alt='${media.text("alt").orEmpty().escapeArticleHtml()}'></figure>")
                    afterImage = true
                }
            }
            else -> Unit
        }
    }
    if (line.isNotEmpty()) flush()
    return html.toString()
}

private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.hasBlockFormatting(): Boolean = listOf("header", "align", "list", "blockquote")
    .any { containsKey(it) }

private fun String.safeArticleUrl(): String? {
    val normalized = when {
        startsWith("//") -> "https:$this"
        startsWith("http://") -> "https://${removePrefix("http://")}"
        else -> this
    }
    return normalized.takeIf { it.startsWith("https://") }
}

private fun String.withArticleFormatting(attributes: JsonObject?): String {
    var result = this
    if (attributes?.text("bold") == "true") result = "<strong>$result</strong>"
    if (attributes?.text("italic") == "true") result = "<em>$result</em>"
    if (attributes?.text("strike") == "true") result = "<s>$result</s>"
    if (attributes?.text("underline") == "true") result = "<u>$result</u>"
    val size = when (attributes?.text("size")) {
        "huge" -> "1.8em"
        "large" -> "1.4em"
        "small" -> "0.8em"
        else -> null
    }
    if (size != null) result = "<span style='font-size:$size'>$result</span>"
    val color = attributes?.text("color")?.takeIf { it.matches(Regex("#[0-9a-fA-F]{3,8}")) }
    if (color != null) result = "<span style='color:$color'>$result</span>"
    val link = attributes?.text("link")?.safeArticleUrl()
    if (link != null) result = "<a href='${link.escapeArticleHtml()}'>$result</a>"
    return result
}

private fun String.escapeArticleHtml(): String = replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;")
    .replace("'", "&#39;")
