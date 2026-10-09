package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.FavFolderListDto
import com.qbili.data.remote.dto.VideoRelationDto
import com.qbili.data.remote.dto.WatchLaterListDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 视频互动（Web 端 + csrf）。
 *
 * 刻意全部用 Web 端点而不是 App 端点：Web 端只需要 Cookie 与 csrf，
 * 因此**不管用户是扫码、短信还是 Cookie 导入登录的都能用**；
 * App 端点要 access_key，扫码和 Cookie 登录拿不到。
 *
 * `X-QBili-Csrf` 标记头让 CsrfInterceptor 自动把 bili_jct 注入表单，
 * 少了它服务端会返回 -111。注解值必须是字面量，不能引用常量。
 */
interface InteractionApi {

    /** 一次拿到点赞/点踩/收藏/投币数/关注状态 */
    @GET("x/web-interface/archive/relation")
    suspend fun relation(
        @Query("aid") aid: Long?,
        @Query("bvid") bvid: String?,
    ): BiliResponse<VideoRelationDto>

    /** @param like 1 点赞，2 取消点赞 */
    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/web-interface/archive/like")
    suspend fun like(
        @Field("bvid") bvid: String,
        @Field("like") like: Int,
    ): BiliResponse<JsonElement>

    /**
     * @param multiply 投币数 1 或 2
     * @param selectLike 1 表示同时点赞
     */
    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/web-interface/coin/add")
    suspend fun addCoin(
        @Field("bvid") bvid: String,
        @Field("multiply") multiply: Int,
        @Field("select_like") selectLike: Int,
    ): BiliResponse<JsonElement>

    /**
     * 收藏夹列表。带上 [rid] 服务端才会在每项里填 `fav_state`，
     * 否则无法知道视频已在哪些收藏夹中。
     */
    @GET("x/v3/fav/folder/created/list-all")
    suspend fun favFolders(
        @Query("up_mid") upMid: Long,
        @Query("type") type: Int,
        @Query("rid") rid: Long,
    ): BiliResponse<FavFolderListDto>

    /** @param type 2 表示视频稿件 */
    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/v3/fav/resource/deal")
    suspend fun dealFavorite(
        @Field("rid") rid: Long,
        @Field("type") type: Int,
        @Field("add_media_ids") addMediaIds: String,
        @Field("del_media_ids") delMediaIds: String,
    ): BiliResponse<JsonElement>

    @GET("x/v2/history/toview")
    suspend fun watchLaterList(): BiliResponse<WatchLaterListDto>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/v2/history/toview/add")
    suspend fun addToView(@Field("bvid") bvid: String): BiliResponse<JsonElement>

    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/v2/history/toview/del")
    suspend fun removeFromToView(@Field("aid") aid: Long): BiliResponse<JsonElement>

    /**
     * @param act 1 关注 / 2 取关 / 3 悄悄关注 / 5 拉黑
     * @param reSrc 来源标识，14 为视频页
     */
    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/relation/modify")
    suspend fun modifyRelation(
        @Field("fid") fid: Long,
        @Field("act") act: Int,
        @Field("re_src") reSrc: Int,
    ): BiliResponse<JsonElement>

    /** 只是给分享计数 +1，失败不影响实际分享 */
    @FormUrlEncoded
    @Headers("X-QBili-Csrf: 1")
    @POST("x/web-interface/share/add")
    suspend fun addShare(@Field("bvid") bvid: String): BiliResponse<JsonElement>
}

/**
 * 只有 App 端才有的互动接口。
 *
 * 视频点踩没有 Web 版——`/x/web-interface/archive/dislike` 实测返回 404。
 * 这里用 App 端点，鉴权靠 access_key（[com.qbili.data.local.AccessTokenStore]），
 * 所以扫码/Cookie 登录的用户用不了，UI 必须明确提示认证限制。
 */
interface AppInteractionApi {

    /** @param dislike 0 点踩，1 取消点踩 */
    @FormUrlEncoded
    @POST("x/v2/view/dislike")
    suspend fun dislike(
        @Field("access_key") accessKey: String,
        @Field("aid") aid: Long,
        @Field("dislike") dislike: Int,
    ): BiliResponse<JsonElement>
}
