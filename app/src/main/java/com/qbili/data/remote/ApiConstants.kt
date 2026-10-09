package com.qbili.data.remote

object ApiConstants {
    const val API_BASE = "https://api.bilibili.com/"
    const val PASSPORT_BASE = "https://passport.bilibili.com/"
    const val VC_BASE = "https://api.vc.bilibili.com/"
    const val LIVE_BASE = "https://api.live.bilibili.com/"
    const val SUGGEST_BASE = "https://s.search.bilibili.com/"

    /** 移动端接口。走它可以避开 Web 端的极验与 Gaia 风控。 */
    const val APP_HOST = "app.bilibili.com"
    const val APP_BASE = "https://$APP_HOST/"

    const val WEB_ORIGIN = "https://www.bilibili.com"
    const val WEB_REFERER = "https://www.bilibili.com/"

    /**
     * 桌面 Chrome UA。B 站对 UA 有强校验，缺失或使用 okhttp 默认 UA 会直接返回 -412。
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** 标记该请求需要 WBI 签名（用于路径中不含 /wbi/ 但仍需签名的接口） */
    const val HEADER_NEED_WBI = "X-QBili-Wbi"

    /** 标记该请求需要在表单/查询中自动注入 csrf(bili_jct) */
    const val HEADER_NEED_CSRF = "X-QBili-Csrf"

    /**
     * 标记该请求走移动端签名（appkey + sign）。
     * passport 上移动端与 Web 端接口同域，无法靠 host 区分，只能显式标记。
     */
    const val HEADER_NEED_APP_SIGN = "X-QBili-AppSign"

    object Cookie {
        const val SESSDATA = "SESSDATA"
        const val BILI_JCT = "bili_jct"
        const val DEDE_USER_ID = "DedeUserID"
        const val BUVID3 = "buvid3"
    }
}
