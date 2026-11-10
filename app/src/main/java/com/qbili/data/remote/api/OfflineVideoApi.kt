package com.qbili.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface OfflineVideoApi {
    @GET("x/web-interface/view")
    suspend fun view(@Header("Cookie") cookie: String, @Query("aid") aid: Long): JsonObject

    @GET("x/player/playurl")
    suspend fun playurl(@Header("Cookie") cookie: String, @Query("avid") aid: Long, @Query("cid") cid: Long,
        @Query("qn") quality: Int, @Query("fnval") fnval: Int, @Query("fnver") fnver: Int,
        @Query("fourk") fourk: Int): JsonObject
}
