package com.qbili.data.remote.api

import com.qbili.data.remote.dto.AccountBlacklistDto
import com.qbili.data.remote.dto.BiliResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface AccountRelationApi {
    @GET("x/relation/blacks")
    suspend fun blacklist(
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
        @Query("re_version") version: Int,
    ): BiliResponse<AccountBlacklistDto>
}
