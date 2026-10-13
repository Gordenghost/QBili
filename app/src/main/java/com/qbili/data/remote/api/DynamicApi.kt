package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.DynamicFeedDto
import com.qbili.data.remote.dto.DynamicAuthorsDto
import retrofit2.http.GET
import retrofit2.http.Query

interface DynamicApi {
    @GET("x/relation/followings")
    suspend fun followedAuthors(
        @Query("vmid") mid: Long,
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
        @Query("order") order: String,
    ): BiliResponse<DynamicAuthorsDto>

    @GET("x/polymer/web-dynamic/v1/feed/all")
    suspend fun following(
        @Query("type") type: String,
        @Query("timezone_offset") timezoneOffset: Int,
        @Query("features") features: String,
        @Query("offset") offset: String?,
    ): BiliResponse<DynamicFeedDto>

    @GET("x/polymer/web-dynamic/v1/feed/space")
    suspend fun space(
        @Query("host_mid") mid: Long,
        @Query("features") features: String,
        @Query("offset") offset: String?,
    ): BiliResponse<DynamicFeedDto>

    @GET("x/polymer/web-dynamic/v1/opus/detail")
    suspend fun opusDetail(
        @Query("id") id: String,
        @Query("features") features: String,
    ): BiliResponse<com.qbili.data.remote.dto.OpusDetailDto>
}
