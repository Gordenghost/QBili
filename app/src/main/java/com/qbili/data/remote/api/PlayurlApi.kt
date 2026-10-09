package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.PlayurlDataDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 视频播放地址。
 *
 * 端点是 **api.bilibili.com/x/player/playurl**，不需要任何签名（实测 code=0）。
 * 曾经错误地挂在 app.bilibili.com 上，那个路径根本不存在、连 code 都取不到。
 * 带 wbi 的那条（`x/player/wbi/playurl`）也能用但没必要，多一层签名多一个故障点。
 *
 * 关键参数：
 * - [qn]    期望画质：120=4K 112=1080P+ 80=1080P 64=720P 32=480P 16=360P。
 *   未登录时服务端只给到 480P，而且 accept_quality 会**虚报**更高档位，
 *   所以真实可选画质必须从返回的 dash.video 里取（见 StreamSelector）
 * - [fnval] 返回格式位掩码，4048 = dash + 杜比 + HDR + 4K 全开
 * - [fourk] 允许 4K
 *
 * Retrofit 接口不写默认参数值，固定值统一由 Repository 传入。
 */
interface PlayurlApi {

    @GET("x/player/playurl")
    suspend fun getPlayurl(
        @Query("cid") cid: Long,
        @Query("bvid") bvid: String?,
        @Query("avid") aid: Long?,
        @Query("qn") qn: Int,
        @Query("fnval") fnval: Int,
        @Query("fnver") fnver: Int,
        @Query("fourk") fourk: Int,
    ): BiliResponse<PlayurlDataDto>
}
