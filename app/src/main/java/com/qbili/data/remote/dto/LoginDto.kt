package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- 扫码登录 ----------

@Serializable
data class QrCodeGenerateDto(
    /** 需要生成二维码的内容 */
    val url: String = "",
    @SerialName("qrcode_key") val qrcodeKey: String = "",
)

/**
 * 轮询结果。注意外层 [BiliResponse.code] 恒为 0，真正的状态在 [code]：
 * 86101 未扫码 / 86090 已扫码待确认 / 86038 二维码失效 / 0 登录成功
 *
 * 登录成功时服务端会通过 Set-Cookie 下发凭据，CookieJar 会自动落库，
 * 这里的 [url] 只是同样内容的兜底形式。
 */
@Serializable
data class QrCodePollDto(
    val url: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    val timestamp: Long = 0,
    val code: Int = 0,
    val message: String = "",
)

// ---------- 验证码（极验 v3） ----------

@Serializable
data class CaptchaDto(
    /** 实测为 "geetest" */
    val type: String = "",
    /** 后续 sms/send、login 都要回传这个 token */
    val token: String = "",
    val geetest: GeetestDto? = null,
)

@Serializable
data class GeetestDto(
    val gt: String = "",
    val challenge: String = "",
)

// ---------- 密码登录 ----------

@Serializable
data class PasswordKeyDto(
    /** 加密时必须拼在密码前面的盐 */
    val hash: String = "",
    /** PEM 格式 RSA 公钥 */
    val key: String = "",
)

/**
 * 密码登录结果。
 * status: 0 成功 / 1 账号未注册 / 2 需要二次验证（异地、风险登录）
 */
@Serializable
data class LoginResultDto(
    val status: Int = 0,
    val message: String? = null,
    /** status=2 时是二次验证页面地址；status=0 时是含凭据的跳转地址 */
    val url: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val timestamp: Long = 0,
    val hint: String? = null,
    @SerialName("in_reg_audit") val inRegAudit: Int = 0,
)

// ---------- buvid ----------

@Serializable
data class FingerSpiDto(
    @SerialName("b_3") val buvid3: String = "",
    @SerialName("b_4") val buvid4: String = "",
)
