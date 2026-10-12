package com.qbili.data.remote.api

import com.qbili.data.remote.dto.AppRecommendDataDto
import com.qbili.data.remote.dto.BiliResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.QueryMap

interface AppFeedApi {
    @Headers("X-QBili-AppSign: hd")
    @GET("x/v2/feed/index")
    suspend fun recommend(
        @QueryMap parameters: Map<String, String>,
        @Header("buvid") buvid: String,
    ): BiliResponse<AppRecommendDataDto>
}
