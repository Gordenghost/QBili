package com.qbili.data.repository

import com.qbili.data.remote.api.FavoriteApi
import com.qbili.data.remote.dto.requireData
import com.qbili.domain.model.FavFolder
import com.qbili.domain.model.VideoItem

class FavoriteRepository(private val api: FavoriteApi) {
    suspend fun folders(mid: Long): List<FavFolder> = api.folders(mid).requireData().list.orEmpty().map { dto ->
        FavFolder(
            id = dto.id.takeIf { it > 0 } ?: dto.fid,
            title = dto.title,
            mediaCount = dto.mediaCount,
            isPrivate = dto.attr and 1 == 1,
            containsVideo = false,
        )
    }

    suspend fun videos(folderId: Long, page: Int): FavoritePage {
        val data = api.videos(folderId, page, PAGE_SIZE, "web").requireData()
        val items = data.medias.orEmpty().filter { it.type == 2 && it.id > 0 }.map { it.toVideo() }
        return FavoritePage(items, data.hasMore && data.medias.orEmpty().isNotEmpty())
    }

    data class FavoritePage(val videos: List<VideoItem>, val hasMore: Boolean)

    private companion object {
        const val PAGE_SIZE = 20
    }
}
