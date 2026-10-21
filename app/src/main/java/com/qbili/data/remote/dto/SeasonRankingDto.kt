package com.qbili.data.remote.dto

import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.domain.model.SeasonRankingItem
import com.qbili.domain.model.validSeasonRating
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val rating: SeasonRatingDto? = null,
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

@Serializable
data class SeasonRankingEpisodeDto(
    @SerialName("index_show") val indexShow: String = "",
    @SerialName("desc") val description: String = "",
)

@Serializable
data class SeasonRankingStatDto(val view: Long? = null, val follow: Long? = null)
