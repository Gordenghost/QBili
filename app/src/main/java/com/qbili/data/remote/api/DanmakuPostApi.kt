package com.qbili.data.remote.api

import com.qbili.data.remote.dto.DanmakuPostResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface DanmakuPostApi {
    @FormUrlEncoded
    @Headers("X-QBili-Wbi: 1")
    @POST("x/v2/dm/post")
    suspend fun send(
        @Header("Cookie") cookies: String,
        @Query("web_location") webLocation: String,
        @Query("csrf") queryCsrf: String,
        @Field("type") type: Int,
        @Field("oid") cid: Long,
        @Field("bvid") bvid: String,
        @Field("msg") message: String,
        @Field("progress") progress: Long,
        @Field("color") color: Int,
        @Field("fontsize") fontSize: Int,
        @Field("pool") pool: Int,
        @Field("mode") mode: Int,
        @Field("rnd") nonce: Long,
        @Field("csrf") csrf: String,
    ): DanmakuPostResponse
}
