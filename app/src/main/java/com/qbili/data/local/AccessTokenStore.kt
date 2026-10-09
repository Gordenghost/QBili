package com.qbili.data.local

import android.content.Context

/**
 * App 端 access_key（OAuth token）。
 *
 * 为什么需要它：少数写操作只有 App 端接口，没有 Web 版对应物——
 * 最典型的是**视频点踩**（`/x/web-interface/archive/dislike` 实测 404，
 * 只有 `app.bilibili.com/x/v2/view/dislike`），而 App 端接口用 access_key 鉴权，
 * 不认 Cookie。
 *
 * 只有移动端短信/密码登录会返回它；扫码与 Cookie 导入拿不到。
 * 所以依赖它的功能必须能在缺失时优雅降级，而不是报一个看不懂的错。
 */
class AccessTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    val accessKey: String?
        get() = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }

    val hasAccessKey: Boolean get() = accessKey != null

    fun save(token: String?) {
        if (token.isNullOrBlank()) return
        prefs.edit().putString(KEY, token).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val PREF_NAME = "qbili_access_token"
        const val KEY = "access_key"
    }
}
