package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.VideoViewDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 视频详情与弹幕。
 *
 * 都在 api.bilibili.com 上，且都**不需要签名**（实测 playurl 也一样，
 * 见 [PlayurlApi] 的说明）。
 */
interface VideoApi {

    /**
     * 视频详情。bvid 与 aid 二选一——移动端搜索结果只给 aid，
     * 所以两种入口都要支持。
     */
    @GET("x/web-interface/view")
    suspend fun view(
        @Query("bvid") bvid: String?,
        @Query("aid") aid: Long?,
    ): BiliResponse<VideoViewDto>

    /**
     * 弹幕分段（protobuf 二进制，非 JSON）。
     *
     * 每段覆盖 6 分钟，[segmentIndex] 从 1 开始，所以长视频要拉多段。
     * 返回体交给 DanmakuParser 解析。
     *
     * @param type 1 = 视频弹幕
     * @param oid  实际是 cid，不是 aid
     */
    @GET("x/v2/dm/web/seg.so")
    suspend fun danmakuSegment(
        @Query("type") type: Int,
        @Query("oid") cid: Long,
        @Query("segment_index") segmentIndex: Int,
    ): ResponseBody
}
