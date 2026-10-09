package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.SpaceCardResultDto
import com.qbili.data.remote.dto.SpaceUploadsDto
import com.qbili.data.remote.dto.SpaceOpusFeedDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface SpaceApi {
    @Headers("X-QBili-Wbi: 1")
    @GET("x/polymer/web-dynamic/v1/opus/feed/space")
    suspend fun opus(
        @Query("host_mid") mid: Long,
        @Query("page") page: Int,
        @Query("offset") offset: String?,
        @Query("type") type: String,
        @Query("web_location") webLocation: String,
    ): BiliResponse<SpaceOpusFeedDto>

    @GET("x/web-interface/card")
    suspend fun card(@Query("mid") mid: Long): BiliResponse<SpaceCardResultDto>

    @GET("x/space/wbi/arc/search")
    suspend fun uploads(
        @Query("mid") mid: Long,
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
        @Query("order") order: String,
    ): BiliResponse<SpaceUploadsDto>
}
