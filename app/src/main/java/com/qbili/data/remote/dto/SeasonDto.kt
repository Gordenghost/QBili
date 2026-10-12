package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PgcResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val result: T? = null,
    val data: T? = null,
) {
    fun requireResult(): T {
        requireSuccess()
        return result ?: data ?: throw BiliApiException(-1, "番剧接口返回数据为空")
    }

    fun requireSuccess() {
        if (code != 0) throw BiliApiException(code, message.ifBlank { "番剧接口返回错误" })
        val payload = result ?: data
        val voucher = when (payload) {
            is SeasonDetailDto -> payload.voucher
            is SeasonPlayurlDto -> payload.voucher
            is JsonObject -> (payload["v_voucher"] as? JsonPrimitive)?.content
            else -> null
        }
        if (!voucher.isNullOrBlank()) throw BiliRiskControlException(voucher)
    }
}

@Serializable
data class SeasonDetailDto(
    @SerialName("season_id") val seasonId: Long = 0,
    val title: String = "",
    val cover: String = "",
    val evaluate: String = "",
    val type: Int = 0,
    val rating: SeasonRatingDto? = null,
    val styles: List<String> = emptyList(),
    val areas: List<SeasonAreaDto> = emptyList(),
    @SerialName("new_ep") val newEpisode: SeasonNewEpisodeDto? = null,
    @SerialName("user_status") val userStatus: SeasonUserStatusDto? = null,
    val episodes: List<SeasonEpisodeDto> = emptyList(),
    val section: List<SeasonSectionDto> = emptyList(),
    @SerialName("v_voucher") val voucher: String? = null,
)

@Serializable
data class SeasonRatingDto(val score: Double = 0.0)

@Serializable
data class SeasonAreaDto(val name: String = "")

@Serializable
data class SeasonNewEpisodeDto(val desc: String = "")

@Serializable
data class SeasonUserStatusDto(val follow: Int = 0, @SerialName("area_limit") val areaLimit: Int = 0)

@Serializable
data class SeasonSectionDto(val title: String = "", val episodes: List<SeasonEpisodeDto> = emptyList())

@Serializable
data class SeasonEpisodeDto(
    val id: Long = 0,
    @SerialName("ep_id") val episodeId: Long = 0,
    val aid: Long = 0,
    val cid: Long = 0,
    val title: String = "",
    @SerialName("long_title") val longTitle: String = "",
    @SerialName("show_title") val showTitle: String = "",
    val cover: String = "",
    val duration: Long = 0,
    val badge: String = "",
    val status: Int = 0,
    val rights: SeasonEpisodeRightsDto? = null,
    @SerialName("is_view_hide") val hidden: Boolean = false,
)

@Serializable
data class SeasonEpisodeRightsDto(@SerialName("area_limit") val areaLimit: Int = 0)

@Serializable
data class SeasonPlayurlDto(
    val dash: DashDto? = null,
    val durl: List<DurlDto> = emptyList(),
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    @SerialName("is_preview") val preview: Int = 0,
    @SerialName("v_voucher") val voucher: String? = null,
)
