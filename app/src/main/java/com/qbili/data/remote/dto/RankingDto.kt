package com.qbili.data.remote.dto

import com.qbili.domain.model.VideoItem
import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class PopularPageDto(
    val list: List<PopularVideoDto>? = null,
    @SerialName("no_more") val noMore: Boolean = false,
)

@Serializable
data class RankingListDto(
    val list: List<PopularVideoDto>? = null,
    val note: String = "",
    val title: String = "",
    val explain: String = "",
    val reminder: String = "",
    val config: WeeklyIssueDto? = null,
)

@Serializable
data class WeeklyIssuesDto(val list: List<WeeklyIssueDto>? = null)

@Serializable
data class WeeklyIssueDto(
    val number: Int = 0,
    val name: String = "",
    val subject: String = "",
    val status: Int = 0,
)

@Serializable
data class PopularVideoDto(
    val aid: Long = 0,
    val bvid: String = "",
    val title: String = "",
    val pic: String = "",
    val duration: Int = 0,
    val pubdate: Long = 0,
    val owner: OwnerDto? = null,
    val stat: VideoStatDto? = null,
    val cid: Long = 0,
    val state: Int = 0,
    val tname: String = "",
    val achievement: String = "",
    @SerialName("rcmd_reason") val recommendReason: JsonElement? = null,
) {
    fun toVideoItem(): VideoItem = VideoItem(
        aid = aid,
        bvid = bvid,
        cid = cid.takeIf { it > 0 },
        title = stripHtml(title),
        cover = normalizeUrl(pic),
        durationSeconds = duration,
        pubDate = pubdate,
        authorMid = owner?.mid ?: 0,
        authorName = owner?.name.orEmpty(),
        authorFace = owner?.face.orEmpty(),
        viewCount = stat?.view ?: 0,
        danmakuCount = stat?.danmaku ?: 0,
        channel = tname,
        recommendReason = achievement.ifBlank {
            ((recommendReason as? JsonObject)?.get("content") as? JsonPrimitive)?.contentOrNull
                ?: (recommendReason as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull.orEmpty()
        }.takeIf { it.isNotBlank() },
    )
}
