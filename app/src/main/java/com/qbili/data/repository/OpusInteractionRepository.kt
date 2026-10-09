package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.OpusActionApi
import com.qbili.data.remote.api.OpusActionEntity
import com.qbili.data.remote.api.OpusActionMeta
import com.qbili.data.remote.api.OpusActionType
import com.qbili.data.remote.api.OpusFavoriteRequest
import com.qbili.data.remote.api.OpusLikeRequest
import com.qbili.data.remote.dto.requireSuccess

class OpusInteractionRepository(
    private val api: OpusActionApi,
    private val csrf: () -> String?,
) {
    suspend fun setLike(id: String, liked: Boolean) {
        api.like(
            OpusLikeRequest(id, if (liked) 1 else 2, SPMID, "", csrfToken()),
            referer(id),
        ).requireSuccess()
    }

    suspend fun setFavorite(id: String, favorited: Boolean) {
        api.favorite(
            csrfToken(),
            OpusFavoriteRequest(
                meta = OpusActionMeta(SPMID, "", "unknown"),
                entity = OpusActionEntity(id, OpusActionType(biz = 2)),
                action = if (favorited) 3 else 4,
            ),
            referer(id),
        ).requireSuccess()
    }

    private fun csrfToken(): String = csrf()?.takeIf { it.isNotBlank() }
        ?: throw BiliApiException(-101, "请先登录再操作")

    private fun referer(id: String) = "https://www.bilibili.com/opus/$id"

    private companion object {
        const val SPMID = "333.1369.0.0"
    }
}
