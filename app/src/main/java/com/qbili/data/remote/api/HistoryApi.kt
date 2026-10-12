package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.HistoryDataDto
import com.qbili.data.remote.dto.HistoryWriteResponse
import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonDetailDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface HistoryApi {
    @GET("pgc/view/web/season")
    suspend fun episodeSeason(@Query("ep_id") episodeId: Long): PgcResponse<SeasonDetailDto>

    @GET("x/web-interface/history/cursor")
    suspend fun list(
        @Header("Cookie") cookies: String,
        @Query("max") max: Long,
        @Query("view_at") viewAt: Long,
        @Query("business") business: String,
        @Query("type") type: String,
        @Query("ps") pageSize: Int,
    ): BiliResponse<HistoryDataDto>

    @FormUrlEncoded
    @POST("x/v2/history/delete")
    suspend fun delete(@Header("Cookie") cookies: String, @Field("kid") kid: String,
        @Field("csrf") csrf: String, @Field("jsonp") jsonp: String): HistoryWriteResponse

    @FormUrlEncoded
    @POST("x/v2/history/clear")
    suspend fun clear(@Header("Cookie") cookies: String, @Field("csrf") csrf: String,
        @Field("jsonp") jsonp: String): HistoryWriteResponse

    @FormUrlEncoded
    @POST("x/click-interface/web/heartbeat")
    suspend fun heartbeat(@Header("Cookie") cookies: String, @Field("bvid") bvid: String,
        @Field("cid") cid: Long, @Field("played_time") playedTime: Long,
        @Field("type") type: Int, @Field("csrf") csrf: String): HistoryWriteResponse
}
