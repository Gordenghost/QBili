package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.domain.model.VideoItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
data class RelatedVideoResponse(
    val code: Int,
    val message: JsonElement? = null,
    val data: JsonElement? = null,
    val result: JsonElement? = null,
    @SerialName("v_voucher") val voucher: String? = null,
) {
    fun videos(json: Json, currentAid: Long, currentBvid: String): List<VideoItem> {
        if (code != 0) throw BiliApiException(code,
            (message as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "相关推荐加载失败")
        val risk = listOf(voucher, ((data as? JsonObject)?.get("v_voucher") as? JsonPrimitive)?.contentOrNull,
            ((result as? JsonObject)?.get("v_voucher") as? JsonPrimitive)?.contentOrNull)
            .firstOrNull { !it.isNullOrBlank() }
        if (risk != null) throw BiliRiskControlException(risk)
        val list = data as? JsonArray ?: throw IllegalStateException("相关推荐列表缺失，请重试")
        val seenAids = mutableSetOf(currentAid)
        val seenBvids = mutableSetOf(currentBvid)
        return list.map { json.decodeFromJsonElement<RelatedVideoDto>(it) }.mapNotNull { item ->
            val bvid = item.bvid.takeIf { it.matches(Regex("BV[0-9A-Za-z]{10}")) }.orEmpty()
            if (item.aid <= 0 || item.state < 0 || item.redirectUrl.isNotBlank() || item.isOgv || item.seasonType > 0 ||
                item.aid in seenAids || bvid.isNotEmpty() && bvid in seenBvids) return@mapNotNull null
            seenAids += item.aid
            if (bvid.isNotEmpty()) seenBvids += bvid
            VideoItem(aid = item.aid, bvid = bvid, cid = item.cid.takeIf { it > 0 },
                title = stripHtml(item.title).ifBlank { "（无标题）" }, cover = normalizeUrl(item.pic),
                durationSeconds = item.duration.coerceAtLeast(0), authorMid = item.owner?.mid ?: 0,
                authorName = stripHtml(item.owner?.name), authorFace = normalizeUrl(item.owner?.face),
                channel = stripHtml(item.channel), viewCount = item.stat?.view?.coerceAtLeast(0) ?: 0,
                danmakuCount = item.stat?.danmaku?.coerceAtLeast(0) ?: 0, pubDate = item.pubdate.coerceAtLeast(0))
        }
    }
}

@Serializable
data class RelatedVideoDto(
    val aid: Long = 0,
    val bvid: String = "",
    val cid: Long = 0,
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val state: Int = 0,
    val owner: OwnerDto? = null,
    val stat: VideoStatDto? = null,
    @SerialName("tname") val channel: String = "",
    @SerialName("redirect_url") val redirectUrl: String = "",
    @SerialName("is_ogv") val isOgv: Boolean = false,
    @SerialName("season_type") val seasonType: Int = 0,
)
