package com.qbili.data.repository

import com.qbili.data.remote.dto.OpusDetailDto
import com.qbili.domain.model.OpusBlock
import com.qbili.domain.model.OpusDetail
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun OpusDetailDto.toOpusDetail(id: String): OpusDetail {
    val item = item as? JsonObject
    val modules = item?.get("modules") as? JsonArray
    val blocks = buildList {
        modules.orEmpty().forEach { module ->
            val moduleData = module as? JsonObject ?: return@forEach
            val content = moduleData.objectValue("module_content") ?: return@forEach
            (content["paragraphs"] as? JsonArray).orEmpty().forEach { paragraph ->
                val element = paragraph as? JsonObject ?: return@forEach
                val text = (element.objectValue("text")?.get("nodes") as? JsonArray).orEmpty()
                    .joinToString("") { node ->
                        (node as? JsonObject)?.objectValue("word")?.stringValue("words")
                            ?: (node as? JsonObject)?.stringValue("text")
                            ?: ""
                    }.ifBlank { element.stringValue("text").orEmpty() }
                if (text.isNotBlank()) add(OpusBlock.Paragraph(text))
                val pics = element.objectValue("pic")?.get("pics") as? JsonArray
                pics.orEmpty().forEach { pic ->
                    val url = (pic as? JsonObject)?.stringValue("url")
                    if (!url.isNullOrBlank()) add(OpusBlock.Picture(url))
                }
            }
        }
    }
    val title = modules.orEmpty().mapNotNull { (it as? JsonObject)?.objectValue("module_title") }
        .firstOrNull()?.stringValue("text")
        ?: modules.orEmpty().mapNotNull { (it as? JsonObject)?.objectValue("module_top") }
            .firstOrNull()?.stringValue("title")
        ?: item?.stringValue("title").orEmpty()
    val author = modules.orEmpty().mapNotNull { (it as? JsonObject)?.objectValue("module_author") }
        .firstOrNull()?.stringValue("name").orEmpty()
    val authorMid = modules.orEmpty().mapNotNull { (it as? JsonObject)?.objectValue("module_author") }
        .firstOrNull()?.stringValue("mid")?.toLongOrNull() ?: 0
    val basic = item?.objectValue("basic")
    val stats = modules.orEmpty().mapNotNull { (it as? JsonObject)?.objectValue("module_stat") }
        .firstOrNull()
    return OpusDetail(
        id = id,
        title = title,
        author = author,
        blocks = blocks,
        authorMid = authorMid,
        authorFace = modules.orEmpty().mapNotNull { (it as? JsonObject)?.objectValue("module_author") }
            .firstOrNull()?.stringValue("face").orEmpty(),
        commentType = basic?.stringValue("comment_type")?.toIntOrNull() ?: 0,
        commentOid = basic?.stringValue("comment_id_str")?.toLongOrNull() ?: 0,
        commentCount = stats?.objectValue("comment")?.stringValue("count")?.toLongOrNull() ?: 0,
        likeCount = stats?.objectValue("like")?.stringValue("count")?.toLongOrNull() ?: 0,
        favoriteCount = stats?.objectValue("favorite")?.stringValue("count")?.toLongOrNull() ?: 0,
        liked = stats?.objectValue("like")?.stringValue("status") == "true",
        favorited = stats?.objectValue("favorite")?.stringValue("status") == "true",
    )
}

private fun JsonObject.objectValue(name: String): JsonObject? = get(name) as? JsonObject
private fun JsonObject.stringValue(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull
