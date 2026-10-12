package com.qbili.data.remote.api

import com.qbili.data.remote.dto.AppTokenStatusDto
import com.qbili.data.remote.dto.BiliResponse
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface AppAccountApi {
    @Headers("X-QBili-AppSign: hd")
    @GET("api/v2/oauth2/info")
    suspend fun tokenInfo(@Query("access_token") accessKey: String): BiliResponse<AppTokenStatusDto>
}
