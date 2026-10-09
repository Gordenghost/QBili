package com.qbili.core

import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 把异常翻译成用户能看懂的话。
 *
 * 放在 core 而不是 UI 层，因为 ViewModel 也要用它来填 UiState.error，
 * 让 ViewModel 反向依赖 UI 包不合适。
 */
fun Throwable.friendlyMessage(): String = when {
    this is BiliApiException && (code == -799 || code == -509) ->
        "B 站接口请求过于频繁（$code），请稍后再试"

    this is BiliRiskControlException ->
        "B 站要求完成安全验证后才能继续（多词搜索较容易触发）"

    // 86103：B 站判定这次登录必须在官方 App 内完成。这是账号/设备维度的风控策略，
    // 不是参数或签名问题，第三方客户端没有正当手段绕过，只能引导换登录方式。
    this is BiliApiException && code == 86103 ->
        "B 站要求这次登录在官方 App 内完成（86103）。这是账号风控策略，" +
            "不是本应用的问题，也无法绕过。请改用「扫码」登录，或用「Cookie」导入。"

    // 同一手机号的发送冷却由服务端强制，客户端无法提前得知剩余秒数
    this is BiliApiException && code == 86200 ->
        "发送太频繁（86200）：同一手机号 60 秒内只能发一次，请稍等再点「获取验证码」"

    this is BiliApiException && isRiskControl ->
        "请求被拦截（$code），可能是签名失效或请求过于频繁，稍后重试"

    this is BiliApiException && isNotLoggedIn -> "需要登录后才能查看"

    this is BiliApiException && isCsrfFailure -> "登录状态已失效，请重新登录"

    this is BiliApiException && isCaptchaFailure ->
        "安全验证未通过（$code）。请重试一次，若反复失败建议改用扫码登录"

    // 带上错误码：这是第三方客户端，用户往往就是测试者，
    // 给出可复述的错误码比一句模糊的「操作失败」有用得多
    this is BiliApiException -> "$message（错误码 $code）"

    this is UnknownHostException -> "网络不可用，请检查网络连接"

    this is SocketTimeoutException -> "网络超时，请重试"

    else -> message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
}
