package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.FavFolderListDto
import com.qbili.data.remote.dto.FavoriteVideoListDto
import retrofit2.http.GET
import retrofit2.http.Query

interface FavoriteApi {
    @GET("x/v3/fav/folder/created/list-all")
    suspend fun folders(@Query("up_mid") mid: Long): BiliResponse<FavFolderListDto>

    @GET("x/v3/fav/resource/list")
    suspend fun videos(
        @Query("media_id") folderId: Long,
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
        @Query("platform") platform: String,
    ): BiliResponse<FavoriteVideoListDto>
}
