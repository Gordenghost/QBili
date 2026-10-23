package com.qbili.data.remote.api

import com.qbili.data.remote.dto.RelatedVideoResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface RelatedVideoApi {
    @GET("x/web-interface/archive/related")
    suspend fun related(
        @Header("Cookie") cookies: String,
        @Query("bvid") bvid: String?,
        @Query("aid") aid: Long?,
    ): RelatedVideoResponse
}
