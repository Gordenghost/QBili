package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- 移动端短信登录 ----------

/**
 * 发送短信验证码的响应。
 *
 * 这是**两阶段接口**，且判断成功的标准极其反直觉：
 * 服务端在极验结果未被接受时也会返回非空的 [captchaKey]，
 * 唯一可靠的判据是 [recaptchaUrl] 是否为空——为空才代表短信真正下发。
 * （参考 PiliPlus：`code == 0 && recaptcha_url == ""`）
 */
@Serializable
data class AppSmsSendDto(
    @SerialName("captcha_key") val captchaKey: String = "",
    @SerialName("recaptcha_url") val recaptchaUrl: String? = null,
) {
    /** 非空即要求（重新）完成极验，此时 captcha_key 不代表短信已发 */
    val needsCaptcha: Boolean get() = !recaptchaUrl.isNullOrBlank()

    /** 无需验证且有凭据 */
    val sentSuccessfully: Boolean get() = !needsCaptcha && captchaKey.isNotBlank()
}

/**
 * 移动端登录结果。
 *
 * 与 Web 端最大的差别：凭据不是通过 Set-Cookie 下发，而是**放在响应体里**
 * （[cookieInfo]），必须手动写进 CookieJar，否则登录看似成功但一个接口也调不通。
 */
@Serializable
data class AppLoginResultDto(
    val status: Int = 0,
    val message: String? = null,
    @SerialName("token_info") val tokenInfo: AppTokenInfoDto? = null,
    @SerialName("cookie_info") val cookieInfo: AppCookieInfoDto? = null,
    val url: String? = null,
    val hint: String? = null,
)

@Serializable
data class AppTokenInfoDto(
    val mid: Long = 0,
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
)

@Serializable
data class AppCookieInfoDto(
    val cookies: List<AppCookieDto> = emptyList(),
    val domains: List<String> = emptyList(),
)

@Serializable
data class AppCookieDto(
    val name: String = "",
    val value: String = "",
    @SerialName("http_only") val httpOnly: Int = 0,
    val expires: Long = 0,
    val secure: Int = 0,
)

// ---------- 移动端搜索 ----------

/**
 * 移动端搜索的 type 取值（逐个实测确认）。
 * 注意 total/pages 的含义不太一致，翻页统一以 pages 为准。
 */
object AppSearchType {
    const val VIDEO = 10
    const val USER = 2
    const val LIVE_ROOM = 4
    const val LIVE_USER = 5
    const val ARTICLE = 6
    const val BANGUMI = 7
    const val MOVIE = 8
}

@Serializable
data class AppSearchTypeDataDto(
    val items: List<AppSearchItemDto> = emptyList(),
    val total: Int = 0,
    val pages: Int = 0,
)

/**
 * 移动端搜索条目。结构与 Web 端完全不同：
 * - 用 [goto] 区分类型（av / author / live / article_new / hot_recommend…）
 * - 用 [param] 承载主键：视频是 aid、用户是 mid、直播是 roomid、专栏是 cvid
 * - **不返回 bvid**，只有 aid
 * - [duration] 是 "23:57" 字符串而非秒数
 */
@Serializable
data class AppSearchItemDto(
    val goto: String? = null,
    val param: String? = null,
    val title: String? = null,
    val name: String? = null,
    val cover: String? = null,
    val author: String? = null,
    val face: String? = null,
    val play: Long = 0,
    val danmaku: Long = 0,
    val duration: String? = null,
    val mid: Long = 0,
    val uri: String? = null,
    val ptime: Long = 0,
    @SerialName("pub_date") val pubDate: Long = 0,
    val fans: Long = 0,
    val level: Int = 0,
    val sign: String? = null,
)
