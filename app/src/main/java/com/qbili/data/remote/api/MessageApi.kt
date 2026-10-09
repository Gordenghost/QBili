package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.MessageHistoryDto
import com.qbili.data.remote.dto.MessageSessionsDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

interface MessageApi {
    @Headers("X-QBili-Wbi: 1")
    @GET("session_svr/v1/session_svr/get_sessions")
    suspend fun sessions(
        @Query("session_type") sessionType: Int,
        @Query("group_fold") groupFold: Int,
        @Query("unfollow_fold") unfollowFold: Int,
        @Query("sort_rule") sortRule: Int,
        @Query("size") size: Int,
        @Query("end_ts") endTs: Long?,
        @Query("build") build: Int,
        @Query("mobi_app") app: String,
    ): BiliResponse<MessageSessionsDto>

    @Headers("X-QBili-Wbi: 1")
    @GET("svr_sync/v1/svr_sync/fetch_session_msgs")
    suspend fun history(
        @Query("talker_id") talkerId: Long,
        @Query("session_type") sessionType: Int,
        @Query("size") size: Int,
        @Query("end_seqno") endSeqno: Long?,
        @Query("sender_device_id") senderDeviceId: String,
        @Query("build") build: Int,
        @Query("mobi_app") app: String,
    ): BiliResponse<MessageHistoryDto>

    @FormUrlEncoded
    @Headers("X-QBili-Wbi: 1", "X-QBili-Csrf: 1")
    @POST("web_im/v1/web_im/send_msg")
    suspend fun send(
        @Query("w_sender_uid") signedSender: Long,
        @Query("w_receiver_id") signedReceiver: Long,
        @Query("w_dev_id") signedDevice: String,
        @Field("msg[sender_uid]") sender: Long,
        @Field("msg[receiver_type]") receiverType: Int,
        @Field("msg[receiver_id]") receiver: Long,
        @Field("msg[msg_type]") messageType: Int,
        @Field("msg[msg_status]") status: Int,
        @Field("msg[content]") content: String,
        @Field("msg[new_face_version]") newFaceVersion: Int,
        @Field("msg[dev_id]") device: String,
        @Field("msg[timestamp]") timestamp: Long,
        @Field("from_firework") fromFirework: Int,
        @Field("build") build: Int,
        @Field("mobi_app") app: String,
    ): BiliResponse<JsonElement>
}
