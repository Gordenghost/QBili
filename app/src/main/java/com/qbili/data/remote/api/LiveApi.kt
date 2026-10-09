package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.LiveChatConfigDto
import com.qbili.data.remote.dto.LivePlayDto
import com.qbili.data.remote.dto.LiveRoomDto
import retrofit2.http.GET
import retrofit2.http.Query

interface LiveApi {
    @GET("xlive/web-room/v1/index/getH5InfoByRoom")
    suspend fun room(@Query("room_id") roomId: Long): BiliResponse<LiveRoomDto>

    @GET("xlive/web-room/v2/index/getRoomPlayInfo")
    suspend fun play(
        @Query("room_id") roomId: Long,
        @Query("protocol") protocol: String,
        @Query("format") format: String,
        @Query("codec") codec: String,
        @Query("qn") quality: Int,
        @Query("platform") platform: String,
        @Query("ptype") playerType: Int,
    ): BiliResponse<LivePlayDto>

    @GET("room/v1/Danmu/getConf")
    suspend fun chat(@Query("room_id") roomId: Long): BiliResponse<LiveChatConfigDto>
}
