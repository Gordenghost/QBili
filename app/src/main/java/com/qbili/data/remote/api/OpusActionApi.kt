package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OpusActionApi {
    @POST("x/dynamic/feed/dyn/thumb")
    suspend fun like(
        @Body request: OpusLikeRequest,
        @Header("Referer") referer: String,
    ): BiliResponse<JsonElement>

    @POST("x/community/cosmo/interface/simple_action")
    suspend fun favorite(
        @Query("csrf") csrf: String,
        @Body request: OpusFavoriteRequest,
        @Header("Referer") referer: String,
    ): BiliResponse<JsonElement>
}

@Serializable
data class OpusLikeRequest(
    @SerialName("dyn_id_str") val dynIdStr: String,
    val up: Int,
    val spmid: String,
    @SerialName("from_spmid") val fromSpmid: String,
    val csrf: String,
)

@Serializable
data class OpusFavoriteRequest(
    val meta: OpusActionMeta,
    val entity: OpusActionEntity,
    val action: Int,
)

@Serializable
data class OpusActionMeta(
    val spmid: String,
    @SerialName("from_spmid") val fromSpmid: String,
    val from: String,
)

@Serializable
data class OpusActionEntity(
    @SerialName("object_id_str") val objectIdStr: String,
    val type: OpusActionType,
)

@Serializable
data class OpusActionType(val biz: Int)
