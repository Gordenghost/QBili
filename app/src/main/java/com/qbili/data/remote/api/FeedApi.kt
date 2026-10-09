package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.RecommendDataDto
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 首页推荐。
 *
 * 注意：Retrofit 接口方法不要写 Kotlin 默认参数值（会生成 DefaultImpls 导致解析异常），
 * 固定参数统一由 Repository 传入。
 */
interface FeedApi {

    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun recommend(
        @Query("fresh_type") freshType: Int,
        @Query("ps") pageSize: Int,
        @Query("fresh_idx") freshIdx: Int,
        @Query("fresh_idx_1h") freshIdx1h: Int,
        @Query("brush") brush: Int,
        @Query("feed_version") feedVersion: String,
        @Query("homepage_ver") homepageVer: Int,
        @Query("web_location") webLocation: String,
        @Query("y_num") yNum: Int,
        @Query("last_y_num") lastYNum: Int,
    ): BiliResponse<RecommendDataDto>
}
