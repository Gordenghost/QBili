package com.qbili.data.remote.api

import com.qbili.data.remote.dto.CourseResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface CourseApi {
    @GET("pugv/view/web/season")
    suspend fun detail(@Header("Cookie") cookies: String, @Query("season_id") seasonId: Long?,
        @Query("ep_id") episodeId: Long?): CourseResponse

    @GET("pugv/player/web/playurl")
    suspend fun playurl(@Header("Cookie") cookies: String, @Query("ep_id") episodeId: Long,
        @Query("cid") cid: Long, @Query("qn") quality: Int, @Query("fnval") fnval: Int,
        @Query("fnver") fnver: Int, @Query("fourk") fourk: Int): CourseResponse
}
