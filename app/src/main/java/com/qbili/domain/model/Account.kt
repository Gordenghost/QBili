package com.qbili.domain.model

/** 当前登录用户的资料 */
data class UserProfile(
    val mid: Long,
    val name: String,
    val avatar: String,
    val level: Int = 0,
    val coins: Double = 0.0,
    val bcoin: Double = 0.0,
    val isVip: Boolean = false,
) {
    companion object {
        val GUEST = UserProfile(mid = 0, name = "未登录", avatar = "")
    }
}

/** 扫码登录的状态机 */
sealed interface QrLoginState {
    data object Idle : QrLoginState

    data object Loading : QrLoginState

    /** 二维码已就绪，等待扫码 */
    data class Ready(val content: String, val qrcodeKey: String) : QrLoginState

    /** 已扫码，等待手机端点击确认 */
    data class Scanned(val content: String, val qrcodeKey: String) : QrLoginState

    /** 二维码超时失效，需要重新生成 */
    data object Expired : QrLoginState

    data object Success : QrLoginState

    data class Failed(val message: String) : QrLoginState
}

/** 极验校验通过后拿到的三个值，回传给 B 站接口 */
data class CaptchaResult(
    val token: String,
    val challenge: String,
    val validate: String,
    val seccode: String,
)

/** Gaia 风控挑战，结构与登录验证码一致，但走的是另一组接口 */
data class GaiaChallenge(
    val token: String,
    val gt: String,
    val challenge: String,
)

/**
 * 手机号国际区号。
 *
 * [dialPrefix] 就是传给接口 `cid` 参数的值，它是**国际拨号前缀**（中国大陆 86），
 * **不是** B 站国家列表里的列表序号（中国大陆的列表序号是 1）。
 *
 * 这个区别曾经造成过一个极难定位的故障：传列表序号 1 时，服务端按
 * 「拨号前缀 1 = 美国」受理请求——接口返回 code=0、返回 captcha_key、
 * 照常计入 60 秒冷却甚至回 86200「短信请求过快」，一切看起来都成功，
 * 但短信被投向 +1 号段，中国大陆的手机永远收不到。
 * 参考实现 PiliPlus 传的就是拨号前缀（其国家表里 `countryId` 字段）。
 */
enum class CountryCode(val dialPrefix: Int, val label: String) {
    CHINA(86, "中国大陆"),
    HONG_KONG(852, "中国香港"),
    MACAO(853, "中国澳门"),
    TAIWAN(886, "中国台湾"),
    JAPAN(81, "日本"),
    UNITED_STATES(1, "美国"),
    ;

    /** 由 [dialPrefix] 派生，避免两处硬编码日后漂移 */
    val dialCode: String get() = "+$dialPrefix"

    val display: String get() = "$label $dialCode"
}
