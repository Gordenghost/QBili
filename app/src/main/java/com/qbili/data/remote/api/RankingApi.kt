package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.PopularPageDto
import retrofit2.http.GET
import retrofit2.http.Query

interface RankingApi {
    @GET("x/web-interface/popular")
    suspend fun popular(
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
    ): BiliResponse<PopularPageDto>
}
