package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.ExClimbWuzhiBody
import com.qbili.data.remote.dto.FingerSpiDto
import com.qbili.data.remote.dto.NavDto
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** api.bilibili.com 上的账号信息与设备指纹接口 */
interface AccountApi {

    /** 登录态与当前用户资料 */
    @GET("x/web-interface/nav")
    suspend fun nav(): BiliResponse<NavDto>

    /** 获取真实 buvid3 / buvid4，缺少 buvid 会导致部分接口 -412 */
    @GET("x/frontend/finger/spi")
    suspend fun fingerSpi(): BiliResponse<FingerSpiDto>

    /**
     * 激活 buvid（把设备指纹登记进 Gaia 风控系统）。
     *
     * 这一步很容易被忽略但影响很大：**未激活的 buvid3 会让搜索等接口
     * 频繁返回 `v_voucher` 风控挑战**。实测激活（返回 code=0）之后，
     * 原本必定触发风控的多词搜索立刻恢复正常。
     *
     * 每次安装只需成功执行一次。
     */
    @POST("x/internal/gaia-gateway/ExClimbWuzhi")
    suspend fun activateBuvid(@Body body: ExClimbWuzhiBody): BiliResponse<JsonElement>
}
