package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.core.normalizeUrl
import com.qbili.core.stripHtml
import com.qbili.data.remote.api.SeasonApi
import com.qbili.data.remote.dto.PlayurlDataDto
import com.qbili.data.remote.dto.SeasonEpisodeDto
import com.qbili.domain.model.PlayurlResult
import com.qbili.domain.model.SeasonDetail
import com.qbili.domain.model.SeasonEpisode
import com.qbili.domain.model.SeasonPlayback
import com.qbili.domain.model.SeasonSection

class SeasonRepository(private val api: SeasonApi) {
    suspend fun detail(seasonId: Long): SeasonDetail {
        require(seasonId > 0)
        val dto = api.detail(seasonId).requireResult()
        if (dto.seasonId <= 0) throw BiliApiException(-1, "番剧详情缺少有效的番剧 ID")
        val seen = mutableSetOf<Long>()
        fun episodes(items: List<SeasonEpisodeDto>) = items.filterNot { it.hidden }
            .mapNotNull { episode -> episode.toEpisode()?.takeIf { seen.add(it.id) } }
        val sections = buildList {
            val main = episodes(dto.episodes)
            if (main.isNotEmpty()) add(SeasonSection("正片", main))
            dto.section.forEach { section ->
                val extras = episodes(section.episodes)
                if (extras.isNotEmpty()) add(SeasonSection(stripHtml(section.title).ifBlank { "其他剧集" }, extras))
            }
        }
        return SeasonDetail(
            id = dto.seasonId,
            title = stripHtml(dto.title).ifBlank { "番剧 / 影视" },
            cover = normalizeUrl(dto.cover),
            description = stripHtml(dto.evaluate),
            typeName = when (dto.type) { 1 -> "番剧"; 2 -> "电影"; 3 -> "纪录片"; 4 -> "国创"; 5 -> "电视剧"; else -> "影视" },
            progress = dto.newEpisode?.desc.orEmpty(),
            score = dto.rating?.score ?: 0.0,
            styles = dto.styles,
            areas = dto.areas.map { it.name }.filter { it.isNotBlank() },
            followed = dto.userStatus?.follow == 1,
            areaLimited = dto.userStatus?.areaLimit == 1,
            sections = sections,
        )
    }

    suspend fun playurl(episode: SeasonEpisode, quality: Int): SeasonPlayback {
        if (episode.areaLimited) throw BiliApiException(-10403, "该剧集在当前地区暂不可播放")
        if (episode.id <= 0 || episode.cid <= 0) throw BiliApiException(-1, "该剧集尚未提供播放信息，可能未开播")
        val dto = api.playurl(episode.id, episode.cid, quality, 4048, 0, 1).requireResult()
        if (dto.dash?.video.orEmpty().none { it.url.isNotBlank() } && dto.durl.none { it.fullUrl.isNotBlank() }) {
            throw BiliApiException(-1, "暂无可播放的地址，请确认登录、会员／购买权限或地区限制")
        }
        val result = PlayurlResult.fromDto(PlayurlDataDto(
            dash = dto.dash, durl = dto.durl, quality = dto.quality,
            format = dto.format, timelength = dto.timelength,
        ))
        return SeasonPlayback(result, preview = dto.preview == 1, quality = dto.quality)
    }

    suspend fun setFollowed(seasonId: Long, followed: Boolean) {
        require(seasonId > 0)
        (if (followed) api.follow(seasonId) else api.unfollow(seasonId)).requireSuccess()
    }
}

private fun SeasonEpisodeDto.toEpisode(): SeasonEpisode? {
    val resolvedId = id.takeIf { it > 0 } ?: episodeId.takeIf { it > 0 } ?: return null
    val label = showTitle.ifBlank { listOf(title, longTitle).filter { it.isNotBlank() }.joinToString(" · ") }
    return SeasonEpisode(
        id = resolvedId, aid = aid, cid = cid,
        title = stripHtml(label).ifBlank { "剧集 $resolvedId" },
        cover = normalizeUrl(cover),
        durationSeconds = (duration.coerceAtLeast(0) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        badge = stripHtml(badge), status = status, areaLimited = rights?.areaLimit == 1,
    )
}
