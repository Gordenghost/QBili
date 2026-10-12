package com.qbili.data.remote.api

import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonDetailDto
import com.qbili.data.remote.dto.SeasonPlayurlDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface SeasonApi {
    @GET("pgc/view/web/season")
    suspend fun detail(@Query("season_id") seasonId: Long): PgcResponse<SeasonDetailDto>

    @GET("pgc/player/web/playurl")
    suspend fun playurl(
        @Query("ep_id") episodeId: Long,
        @Query("cid") cid: Long,
        @Query("qn") quality: Int,
        @Query("fnval") fnval: Int,
        @Query("fnver") fnver: Int,
        @Query("fourk") fourk: Int,
    ): PgcResponse<SeasonPlayurlDto>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("pgc/web/follow/add")
    suspend fun follow(@Field("season_id") seasonId: Long): PgcResponse<JsonElement>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("pgc/web/follow/del")
    suspend fun unfollow(@Field("season_id") seasonId: Long): PgcResponse<JsonElement>
}
