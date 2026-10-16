package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.PopularPageDto
import com.qbili.data.remote.dto.RankingListDto
import com.qbili.data.remote.dto.WeeklyIssuesDto
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface RankingApi {
    @GET("x/web-interface/popular")
    suspend fun popular(
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
    ): BiliResponse<PopularPageDto>

    @Headers("X-QBili-Wbi: 1")
    @GET("x/web-interface/ranking/v2")
    suspend fun ranking(
        @Query("rid") regionId: Int,
        @Query("type") type: String,
        @Query("web_location") webLocation: String,
    ): BiliResponse<RankingListDto>

    @Headers("X-QBili-Wbi: 1")
    @GET("x/web-interface/popular/series/list")
    suspend fun weeklyIssues(): BiliResponse<WeeklyIssuesDto>

    @Headers("X-QBili-Wbi: 1")
    @GET("x/web-interface/popular/series/one")
    suspend fun weekly(@Query("number") number: Int): BiliResponse<RankingListDto>

    @Headers("X-QBili-Wbi: 1")
    @GET("x/web-interface/popular/precious")
    suspend fun precious(): BiliResponse<RankingListDto>
}
