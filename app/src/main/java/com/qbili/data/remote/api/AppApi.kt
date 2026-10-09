package com.qbili.data.remote.api

import com.qbili.data.remote.dto.AppLoginResultDto
import com.qbili.data.remote.dto.AppSearchTypeDataDto
import com.qbili.data.remote.dto.AppSmsSendDto
import com.qbili.data.remote.dto.BiliResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * 移动端登录接口（在 passport.bilibili.com 上，与 Web 接口同域）。
 *
 * 短信登录统一走 **HD 版（android_hd）身份**（标记值 hd，见 AppSignInterceptor）：
 * 实测用手机版身份时接口成功但短信被风控静默丢弃，HD 身份才能正常收到。
 *
 * 注意 @Headers 的值必须是字面量常量，不能引用 ApiConstants.HEADER_NEED_APP_SIGN，
 * 两处要一起改。
 */
interface AppPassportApi {

    /**
     * 发送短信验证码。
     *
     * 第一次调用只带基础参数；若返回 [AppSmsSendDto.recaptchaUrl]，
     * 完成极验后必须用同一组 cid/tel/login_session_id 加上 gee_* 结果重发，
     * 才会真正下发短信。login_session_id 在发送与登录两步间必须一致。
     */
    @FormUrlEncoded
    @Headers("X-QBili-AppSign: hd")
    @POST("x/passport-login/sms/send")
    suspend fun sendSms(
        /** 官方客户端同时把它放进请求头与表单，两处都要 */
        @Header("buvid") buvidHeader: String,
        @Field("cid") countryCode: Int,
        @Field("tel") tel: String,
        @Field("login_session_id") loginSessionId: String,
        @Field("channel") channel: String,
        @Field("buvid") buvid: String,
        @Field("local_id") localId: String,
        /** 固定的 JSON 串，FormBody 会自动做 URL 编码 */
        @Field("statistics") statistics: String,
        @Field("recaptcha_token") recaptchaToken: String?,
        @Field("gee_challenge") geeChallenge: String?,
        @Field("gee_validate") geeValidate: String?,
        @Field("gee_seccode") geeSeccode: String?,
    ): BiliResponse<AppSmsSendDto>

    /**
     * 短信验证码登录。
     *
     * 参数比「发送」那步多得多，逐字段对齐 PiliPlus 的实测集合：设备三件套
     * （device_id / device_name / device_platform）、来源埋点（from_pv / from_url）、
     * 以及 [deviceToken]。缺这些容易被风控判定为非官方流量。
     *
     * 注意这里**不传 login_session_id**——参考实现只在发送那步用它。
     */
    @FormUrlEncoded
    @Headers("X-QBili-AppSign: hd")
    @POST("x/passport-login/login/sms")
    suspend fun loginBySms(
        @Header("buvid") buvidHeader: String,
        @Field("cid") countryCode: Int,
        @Field("tel") tel: String,
        @Field("code") code: String,
        @Field("captcha_key") captchaKey: String,
        @Field("buvid") buvid: String,
        @Field("local_id") localId: String,
        @Field("bili_local_id") biliLocalId: String,
        @Field("device_id") deviceId: String,
        @Field("device") device: String,
        @Field("device_name") deviceName: String,
        @Field("device_platform") devicePlatform: String,
        /** RSA 加密随机串后 URL 编码，取不到公钥时可省略 */
        @Field("dt") deviceToken: String?,
        @Field("from_pv") fromPv: String,
        @Field("from_url") fromUrl: String,
        @Field("statistics") statistics: String,
    ): BiliResponse<AppLoginResultDto>
}

/**
 * 移动端搜索（app.bilibili.com，host 命中即自动签名）。
 *
 * 相比 Web 端搜索：多词关键词不会触发 Gaia 风控，
 * 但 order / duration / rid 参数实测无效，需要筛选时仍得回退到 Web 接口。
 */
interface AppSearchApi {

    @GET("x/v2/search/type")
    suspend fun searchByType(
        @Query("keyword") keyword: String,
        @Query("type") type: Int,
        @Query("pn") page: Int,
        @Query("ps") pageSize: Int,
    ): BiliResponse<AppSearchTypeDataDto>
}
