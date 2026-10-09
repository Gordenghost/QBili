package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.VideoTagDto
import retrofit2.http.GET
import retrofit2.http.Query

interface VideoTagApi {
    @GET("x/tag/archive/tags")
    suspend fun tags(
        @Query("bvid") bvid: String?,
        @Query("aid") aid: Long?,
    ): BiliResponse<List<VideoTagDto>>
}
