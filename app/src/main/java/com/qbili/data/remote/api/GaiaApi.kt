package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.GaiaRegisterDto
import com.qbili.data.remote.dto.GaiaValidateDto
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

/**
 * Gaia 风控验证。当业务接口返回 `v_voucher` 时走这两步：
 * register 拿极验参数 -> 用户完成验证 -> validate 换取 grisk_id。
 */
interface GaiaApi {

    @FormUrlEncoded
    @POST("x/gaia-vgate/v1/register")
    suspend fun register(
        @Field("v_voucher") voucher: String,
    ): BiliResponse<GaiaRegisterDto>

    @FormUrlEncoded
    @POST("x/gaia-vgate/v1/validate")
    suspend fun validate(
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String,
    ): BiliResponse<GaiaValidateDto>
}
