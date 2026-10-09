package com.qbili.data.remote.api

import com.qbili.data.remote.dto.ArticleViewDto
import com.qbili.data.remote.dto.ArticleViewInfoDto
import com.qbili.data.remote.dto.BiliResponse
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface ArticleApi {
    @GET("x/article/view")
    suspend fun view(
        @Query("id") id: Long,
        @Query("gaia_source") gaiaSource: String,
        @Header("Referer") referer: String,
    ): BiliResponse<ArticleViewDto>

    @GET("x/article/viewinfo")
    suspend fun viewInfo(
        @Query("id") id: Long,
        @Query("mobi_app") mobiApp: String,
        @Query("from") from: String,
        @Query("gaia_source") gaiaSource: String,
        @Header("Referer") referer: String,
    ): BiliResponse<ArticleViewInfoDto>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/article/like")
    suspend fun like(
        @Field("id") id: Long,
        @Field("type") type: Int,
        @Header("Referer") referer: String,
    ): BiliResponse<JsonElement>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/article/favorites/add")
    suspend fun addFavorite(
        @Field("id") id: Long,
        @Header("Referer") referer: String,
    ): BiliResponse<JsonElement>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/article/favorites/del")
    suspend fun removeFavorite(
        @Field("id") id: Long,
        @Header("Referer") referer: String,
    ): BiliResponse<JsonElement>
}
