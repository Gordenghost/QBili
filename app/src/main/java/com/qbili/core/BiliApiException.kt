package com.qbili.core

/**
 * B 站接口返回 code != 0 时抛出。
 *
 * 常见错误码：
 * -101 账号未登录 / -111 csrf 校验失败 / -400 请求错误 / -403 权限不足
 * -404 资源不存在 / -412 请求被拦截（风控，通常是 UA/Referer/WBI 签名问题）
 * -509 请求过于频繁 / 62002 稿件不可见
 */
class BiliApiException(
    val code: Int,
    override val message: String,
) : RuntimeException("[$code] $message") {

    val isNotLoggedIn: Boolean get() = code == -101 || code == -400 && message.contains("登录")
    val isRiskControl: Boolean get() = code == -412 || code == -352
    val isCsrfFailure: Boolean get() = code == -111

    /** -105 验证码错误；100001 是 Gaia 验证接口的同义错误 */
    val isCaptchaFailure: Boolean get() = code == -105 || code == 100001
}

/**
 * B 站 Gaia 风控挑战。
 *
 * 这类响应最阴险的地方是 `code` 仍然是 0、`message` 仍然是 "OK"，
 * 只是把 `result` 换成了 `v_voucher`。如果按「成功但没数据」处理，
 * 界面就会显示「没有找到相关内容」，把风控伪装成搜索无结果。
 *
 * 拿着 [voucher] 走 `/x/gaia-vgate/v1/register` → 极验 → `validate`，
 * 通过后重发原请求即可。
 */
class BiliRiskControlException(val voucher: String) :
    RuntimeException("需要完成安全验证（v_voucher）")
