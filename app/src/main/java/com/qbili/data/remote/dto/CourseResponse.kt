package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.domain.model.CourseDetail
import com.qbili.domain.model.CourseEpisode
import com.qbili.domain.model.CoursePlayback
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonPlayback
import com.qbili.domain.model.readerImageUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class CourseResponse(val code: Int, val message: JsonElement? = null,
    val data: JsonElement? = null, val result: JsonElement? = null,
    val v_voucher: JsonElement? = null) {
    fun body(): JsonObject {
        if (code != 0) throw BiliApiException(code, (message as? JsonPrimitive)?.contentOrNull ?: "课程请求失败")
        if (v_voucher != null || listOf(data, result).any { it is JsonObject && "v_voucher" in it }) {
            throw BiliApiException(-352, "课程请求触发风控，请稍后重试")
        }
        val body = data as? JsonObject ?: result as? JsonObject ?: throw BiliApiException(-1, "课程响应缺少内容")
        val innerCode = body.number("code")
        if (innerCode != null && innerCode != 0L) throw BiliApiException(innerCode.toInt(), body.text("message"))
        return body
    }

    fun detail(): CourseDetail {
        val body = body()
        val id = body.number("season_id")?.takeIf { it > 0 } ?: throw BiliApiException(-1, "课程缺少有效ID")
        val rows = body["episodes"] as? JsonArray ?: throw BiliApiException(-1, "课程缺少目录")
        val seen = mutableSetOf<Long>()
        val episodes = rows.mapIndexedNotNull { position, value ->
            val row = value as? JsonObject ?: return@mapIndexedNotNull null
            val episodeId = row.number("id")?.takeIf { it > 0 && seen.add(it) } ?: return@mapIndexedNotNull null
            CourseEpisode(episodeId, row.number("cid")?.coerceAtLeast(0) ?: 0,
                row.number("index")?.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt() ?: position + 1,
                stripHtml(row.text("title")).ifBlank { "课时 $episodeId" }, normalizeUrl(row.text("cover")),
                (row.number("duration") ?: 0).coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                stripHtml(row.text("label")), stripHtml(row.text("preview_toast")),
                row.flag("playable") == true && row.flag("episode_can_view") != false && (row.number("ep_status") ?: 0) >= 0,
                row.text("from") == "pugv" && row.number("play_way") == 1L)
        }
        val up = body["up_info"] as? JsonObject
        val brief = body["brief"] as? JsonObject
        val status = body["user_status"] as? JsonObject
        val payment = body["payment"] as? JsonObject
        val page = body["episode_page"] as? JsonObject
        return CourseDetail(id, stripHtml(body.text("title")).ifBlank { "课程" }, normalizeUrl(body.text("cover")),
            stripHtml(body.text("subtitle")), up?.number("mid")?.coerceAtLeast(0) ?: 0,
            stripHtml(up?.text("uname").orEmpty()), normalizeUrl(up?.text("avatar").orEmpty()),
            stripHtml(brief?.text("content").orEmpty()),
            (brief?.get("img") as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("url") }
                .mapNotNull(::readerImageUrl).distinct(), stripHtml(body.text("release_info")),
            listOf(payment?.text("price_format").orEmpty(), payment?.text("price_unit").orEmpty())
                .filter { it.isNotBlank() }.joinToString(" "),
            when { status?.flag("is_expired") == true -> "已购权限可能已过期"
                status?.number("payed") == 1L -> "已购，具体课时权限以平台为准"
                status?.number("payed") == 0L -> "未购买，可选择平台允许的试看课时"
                else -> "购买／试看权限以平台为准" }, episodes,
            page?.flag("next") == true || (page?.number("total") ?: 0) > episodes.size)
    }

    fun playback(json: Json, episode: CourseEpisode): CoursePlayback {
        val body = body()
        if (body.flag("is_drm") == true) throw BiliApiException(-1, "暂不支持此课程的加密播放，请在B站查看")
        val preview = body.flag("is_preview") == true
        val normalized = JsonObject(body + ("is_preview" to JsonPrimitive(if (preview) 1 else 0)))
        val dto = json.decodeFromJsonElement(SeasonPlayurlDto.serializer(), normalized)
        val result = PlayurlResult.fromDto(PlayurlDataDto(dash = dto.dash, durl = dto.durl,
            quality = dto.quality, format = dto.format, timelength = dto.timelength))
        if (result.dash?.video.orEmpty().none { it.url.isNotBlank() } && result.durl.none { it.url.isNotBlank() }) {
            throw BiliApiException(-1, "平台未提供可播放地址，请确认登录、购买或试看权限")
        }
        val hint = if (preview) "正在播放平台提供的试看，购买与完整权限请在B站查看" else episode.previewHint
        return CoursePlayback(SeasonPlayback(result, preview, dto.quality), hint,
            progressAllowed = body.flag("is_preview") == false)
    }
}

private fun JsonObject.text(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonObject.number(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let {
    it.booleanOrNull ?: when (it.intOrNull) { 1 -> true; 0 -> false; else -> null }
}
