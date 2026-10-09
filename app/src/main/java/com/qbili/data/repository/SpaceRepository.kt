package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.SpaceApi
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.SpaceProfile
import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.SpaceOpusItem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

class SpaceRepository(private val api: SpaceApi) {
    suspend fun opus(mid: Long, page: Int, offset: String?): OpusPage {
        require(mid > 0 && page > 0)
        val data = api.opus(mid, page, offset, "all", "333.1387").requireData()
        if (data.isRisk || data.voucher != null) {
            throw BiliApiException(-352, "图文列表需要安全验证，请稍后重试")
        }
        val items = data.items ?: throw BiliApiException(0, "图文列表暂不可用")
        return OpusPage(
            items = items.filter { it.opusId.isNotBlank() && it.opusId.all(Char::isDigit) }
                .distinctBy { it.opusId }.map { item ->
                    SpaceOpusItem(
                        id = item.opusId,
                        content = item.content,
                        cover = item.cover?.url.orEmpty(),
                        likeCount = (item.stat?.like as? JsonPrimitive)?.longOrNull ?: 0,
                        articleId = Regex("/read/cv(\\d+)").find(item.jumpUrl)
                            ?.groupValues?.get(1)?.toLongOrNull(),
                    )
                },
            next = data.offset?.takeIf { data.hasMore && it.isNotBlank() && it != offset },
        )
    }

    data class OpusPage(val items: List<SpaceOpusItem>, val next: String?)

    suspend fun profile(mid: Long): SpaceProfile {
        require(mid > 0)
        val data = api.card(mid).requireData()
        val user = data.card ?: throw BiliApiException(0, "账号资料暂不可用")
        return SpaceProfile(
            mid = mid, name = user.name.ifBlank { "用户$mid" }, face = user.face,
            sign = user.sign, followers = data.follower, videoCount = data.archiveCount,
            level = user.level?.value ?: 0, following = data.following,
        )
    }

    suspend fun uploads(mid: Long, page: Int): VideoPage {
        require(mid > 0 && page > 0)
        val data = api.uploads(mid, page, PAGE_SIZE, "pubdate").requireData()
        // B 站有时 code=0 却只返回风控标记，不能当作空投稿误导用户。
        if (data.isRisk) throw BiliApiException(-352, "投稿列表需要安全验证，请稍后重试")
        val raw = data.list?.vlist.orEmpty()
        return VideoPage(
            videos = raw.filter { it.aid > 0 }.map { it.toVideo() },
            hasMore = raw.isNotEmpty() && page * PAGE_SIZE < (data.page?.count ?: 0),
        )
    }

    data class VideoPage(val videos: List<VideoItem>, val hasMore: Boolean)

    companion object {
        const val PAGE_SIZE = 20
    }
}
