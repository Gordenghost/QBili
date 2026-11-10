package com.qbili.data.repository

import com.qbili.domain.model.MessageCard
import com.qbili.domain.model.MessageLink
import com.qbili.domain.model.MessageOpenTarget
import com.qbili.domain.model.contentLabel
import com.qbili.domain.model.messageLinkTarget
import com.qbili.domain.model.readerImageUrl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class ParsedMessageContent(
    val text: String,
    val imageUrl: String? = null,
    val card: MessageCard? = null,
    val systemNotice: Boolean = false,
)

internal object MessageContentParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(type: Int, raw: String, status: Int = 0): ParsedMessageContent {
        if (status == 1 || type == 5) return ParsedMessageContent("[已撤回的消息]")
        val payload = raw.takeIf { it.length <= 100_000 }
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        return when (type) {
            1 -> ParsedMessageContent((payload?.get("content") as? JsonPrimitive)
                ?.takeIf { it.isString }?.contentOrNull ?: "[文字消息无法显示]")
            2, 6 -> ParsedMessageContent(if (type == 2) "[图片]" else "[表情]",
                imageUrl = payload?.image("url", "image_url", "pic_url", "gif_url"))
            7, 11, 12, 14 -> share(type, payload)
            10 -> notification(payload)
            18 -> systemNotice(payload)
            else -> ParsedMessageContent("[其他类型的消息]")
        }
    }

    private fun share(type: Int, payload: JsonObject?): ParsedMessageContent {
        if (payload == null) return ParsedMessageContent("[分享内容无法显示]")
        val source = payload.number("source")
        val explicitTarget = when (type) {
            11 -> payload.videoTarget("rid")
            12 -> payload.number("rid")?.let { MessageOpenTarget.Article(it) }
            7 -> when (source) {
                5L, 9L -> payload.videoTarget("id")
                6L -> payload.number("id")?.let { MessageOpenTarget.Article(it) }
                7L -> payload.number("id")?.let { MessageOpenTarget.Season(it) }
                4L -> payload.number("id")?.let { MessageOpenTarget.Live(it) }
                else -> null
            }
            else -> null
        }
        val target = explicitTarget ?: messageLinkTarget(payload.rawText("url"))
        val label = target?.contentLabel ?: when (type) {
            11 -> "视频"
            12 -> "专栏"
            else -> when (source) {
                5L, 9L -> "视频"
                6L -> "专栏"
                7L, 16L, 17L -> "番剧／影视"
                2L, 3L, 10L, 11L -> "动态"
                else -> "分享内容"
            }
        }
        val title = payload.text("headline").ifBlank { payload.text("title") }.take(200)
            .ifBlank { "${label}卡片（标题暂未返回）" }
        val description = listOf(
            payload.text("summary").ifBlank { payload.text("desc") },
            (payload["attach_msg"] as? JsonObject)?.text("content").orEmpty(),
        ).filter { it.isNotBlank() }.joinToString("\n").take(2000)
        val cover = payload.image("thumb", "cover") ?: (payload["image_urls"] as? JsonArray)
            ?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.let(::readerImageUrl) }
        val card = MessageCard(title, label, description, payload.text("author").take(80), cover,
            links = target?.let { listOf(MessageLink("查看${it.contentLabel}", it)) }.orEmpty())
        return ParsedMessageContent("[$label] $title", card = card)
    }

    private fun notification(payload: JsonObject?): ParsedMessageContent {
        if (payload == null) return ParsedMessageContent("[通知消息无法显示]")
        val title = payload.text("title").take(200).ifBlank { "通知" }
        val modules = (payload["modules"] as? JsonArray)?.take(8)?.mapNotNull { item ->
            (item as? JsonObject)?.let { listOf(it.text("title"), it.text("detail"))
                .filter { text -> text.isNotBlank() }.joinToString("：").takeIf { text -> text.isNotBlank() } }
        }.orEmpty()
        val description = (listOf(payload.text("text")) + modules).filter { it.isNotBlank() }
            .joinToString("\n").take(2000)
        val links = (1..3).mapNotNull { index ->
            val suffix = if (index == 1) "" else "_$index"
            val config = payload["jump_uri${suffix}_config"] as? JsonObject
            if (config != null && config.text("all_uri").isBlank()) return@mapNotNull null
            val target = if (config == null) messageLinkTarget(payload.rawText("jump_uri$suffix")) else
                messageLinkTarget(config.rawText("web_uri")) ?: messageLinkTarget(config.rawText("all_uri"))
            target?.let { MessageLink(config?.text("text").orEmpty().ifBlank { payload.text("jump_text$suffix") }
                .take(80).ifBlank { "查看详情" }, it) }
        }.distinctBy { it.target }
        val cover = (payload["biz_content"] as? JsonObject)?.image("cover", "backup_cover")
        return ParsedMessageContent("[通知] $title", card = MessageCard(title, "通知", description,
            cover = cover, links = links))
    }

    private fun systemNotice(payload: JsonObject?): ParsedMessageContent {
        val content = payload?.get("content")
        val entries = content as? JsonArray ?: (content as? JsonPrimitive)?.takeIf { it.isString }
            ?.contentOrNull?.let { runCatching { json.parseToJsonElement(it) as? JsonArray }.getOrNull() }
        val text = entries?.take(16)?.mapNotNull { (it as? JsonObject)?.text("text")?.takeIf(String::isNotBlank) }
            ?.joinToString("\n")?.take(2000).orEmpty().ifBlank { "[系统提示暂无法显示]" }
        return ParsedMessageContent(text, systemNotice = true)
    }

    private fun JsonObject.rawText(field: String): String = (this[field] as? JsonPrimitive)
        ?.takeIf { it.isString }?.contentOrNull.orEmpty()

    private fun JsonObject.text(field: String): String = rawText(field).trim()

    private fun JsonObject.number(field: String): Long? = (this[field] as? JsonPrimitive)?.contentOrNull
        ?.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()

    private fun JsonObject.videoTarget(idField: String): MessageOpenTarget.Video? {
        val bvid = text("bvid").takeIf { it.matches(Regex("BV[0-9A-Za-z]{10}")) }
        return bvid?.let { MessageOpenTarget.Video(it) } ?: number(idField)?.let { MessageOpenTarget.Video("av$it") }
    }

    private fun JsonObject.image(vararg fields: String): String? = fields.firstNotNullOfOrNull {
        readerImageUrl(text(it))
    }
}
