package com.qbili.data.remote.dto

import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.domain.model.SeasonRankingItem
import com.qbili.domain.model.validSeasonRating
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

@Serializable
data class SeasonRankingDto(
    val list: List<SeasonRankingEntryDto>? = null,
    val note: String = "",
    @SerialName("v_voucher") val voucher: String? = null,
)

@Serializable
data class SeasonRankingEntryDto(
    @SerialName("season_id") val seasonId: Long = 0,
    val rank: Int = 0,
    val title: String = "",
    val cover: String = "",
    val badge: String = "",
    @SerialName("index_show") val indexShow: String = "",
    @SerialName("new_ep") val newEpisode: SeasonRankingEpisodeDto? = null,
    @Serializable(with = SeasonRankingRatingSerializer::class) val rating: SeasonRatingDto? = null,
    val stat: SeasonRankingStatDto? = null,
) {
    fun toRankingItem(position: Int): SeasonRankingItem = SeasonRankingItem(
        seasonId = seasonId,
        rank = rank.takeIf { it > 0 } ?: position,
        title = stripHtml(title).ifBlank { "番剧／影视" },
        cover = normalizeUrl(cover),
        progress = stripHtml(newEpisode?.indexShow.orEmpty().ifBlank { newEpisode?.description.orEmpty() }
            .ifBlank { indexShow }),
        badge = stripHtml(badge),
        score = validSeasonRating(rating?.score),
        viewCount = stat?.view?.takeIf { it >= 0 },
        followCount = stat?.follow?.takeIf { it >= 0 },
    )
}

object SeasonRankingRatingSerializer : KSerializer<SeasonRatingDto?> {
    override val descriptor: SerialDescriptor = SeasonRatingDto.serializer().descriptor.nullable
    private val scoreText = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*分?")

    override fun deserialize(decoder: Decoder): SeasonRatingDto? {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
            ?: throw SerializationException("番剧榜评分需要JSON格式")
        val value = (if (element is JsonObject) element["score"] else element) as? JsonPrimitive ?: return null
        val score = if (value.isString) scoreText.matchEntire(value.contentOrNull.orEmpty().trim())
            ?.groupValues?.get(1)?.toDoubleOrNull() else value.doubleOrNull
        return validSeasonRating(score)?.let(::SeasonRatingDto)
    }

    override fun serialize(encoder: Encoder, value: SeasonRatingDto?) {
        val json = encoder as? JsonEncoder ?: throw SerializationException("番剧榜评分需要JSON格式")
        val score = validSeasonRating(value?.score)
        json.encodeJsonElement(if (score == null) JsonNull else buildJsonObject { put("score", score) })
    }
}

@Serializable
data class SeasonRankingEpisodeDto(
    @SerialName("index_show") val indexShow: String = "",
    @SerialName("desc") val description: String = "",
)

@Serializable
data class SeasonRankingStatDto(val view: Long? = null, val follow: Long? = null)
