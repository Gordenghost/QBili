package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.CommentListDto
import com.qbili.data.remote.dto.CommentPostDto
import com.qbili.data.remote.dto.CommentRepliesDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface CommentApi {
    /** wbi 路径会由 WbiInterceptor 对实际请求 URL 签名。 */
    @GET("x/v2/reply/wbi/main")
    suspend fun list(
        @Query("type") type: Int,
        @Query("oid") oid: Long,
        @Query("mode") mode: Int,
        @Query("next") next: Long,
        @Query("ps") pageSize: Int,
    ): BiliResponse<CommentListDto>

    @GET("x/v2/reply/reply")
    suspend fun replies(
        @Query("type") type: Int,
        @Query("oid") oid: Long,
        @Query("root") root: Long,
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
    ): BiliResponse<CommentRepliesDto>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/v2/reply/add")
    suspend fun post(
        @Field("type") type: Int,
        @Field("oid") oid: Long,
        @Field("message") message: String,
        @Field("plat") platform: Int,
        @Field("root") root: Long?,
        @Field("parent") parent: Long?,
    ): BiliResponse<CommentPostDto>
}
