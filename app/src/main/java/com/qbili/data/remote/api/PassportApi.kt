package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.CaptchaDto
import com.qbili.data.remote.dto.LoginResultDto
import com.qbili.data.remote.dto.PasswordKeyDto
import com.qbili.data.remote.dto.QrCodeGenerateDto
import com.qbili.data.remote.dto.QrCodePollDto
import okhttp3.ResponseBody
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** passport.bilibili.com 上的登录相关接口 */
interface PassportApi {

    // ---------- 扫码登录 ----------

    @GET("x/passport-login/web/qrcode/generate")
    suspend fun generateQrCode(
        @Query("source") source: String,
    ): BiliResponse<QrCodeGenerateDto>

    @GET("x/passport-login/web/qrcode/poll")
    suspend fun pollQrCode(
        @Query("qrcode_key") qrcodeKey: String,
        @Query("source") source: String,
    ): BiliResponse<QrCodePollDto>

    // ---------- 验证码 ----------

    @GET("x/passport-login/captcha")
    suspend fun captcha(
        @Query("source") source: String,
    ): BiliResponse<CaptchaDto>

    // ---------- 密码登录 ----------

    @GET("x/passport-login/web/key")
    suspend fun passwordKey(): BiliResponse<PasswordKeyDto>

    @FormUrlEncoded
    @POST("x/passport-login/web/login")
    suspend fun loginByPassword(
        @Field("username") username: String,
        /** RSA(hash + 明文密码) 后的 base64 */
        @Field("password") password: String,
        @Field("keep") keep: Int,
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String,
        @Field("source") source: String,
        @Field("go_url") goUrl: String,
    ): BiliResponse<LoginResultDto>

    // ---------- 退出登录 ----------

    /**
     * 服务端登出，使当前 SESSDATA 失效。
     * 返回体不是标准 BiliResponse 结构，这里不解析，只要请求发出去即可。
     */
    @FormUrlEncoded
    @POST("login/exit/v2")
    suspend fun logout(
        @Field("biliCSRF") csrf: String,
        @Field("gourl") goUrl: String,
    ): ResponseBody
}
