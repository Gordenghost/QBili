package com.qbili.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App 端 access_key（OAuth token）。
 *
 * 为什么需要它：少数写操作只有 App 端接口，没有 Web 版对应物——
 * 最典型的是**视频点踩**（`/x/web-interface/archive/dislike` 实测 404，
 * 只有 `app.bilibili.com/x/v2/view/dislike`），而 App 端接口用 access_key 鉴权，
 * 不认 Cookie。
 *
 * 本项目当前只有移动端短信登录返回它；扫码、Cookie 与网页密码登录拿不到。
 * 所以依赖它的功能必须能在缺失时优雅降级，而不是报一个看不懂的错。
 */
class AccessTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    val credential: AppAccessToken?
        get() = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }?.let {
            AppAccessToken(it, prefs.getLong(MID_KEY, 0).takeIf { mid -> mid > 0 },
                prefs.getLong(EXPIRY_KEY, 0).takeIf { expiry -> expiry > 0 })
        }

    val accessKey: String?
        get() = credential?.takeUnless { it.isExpired(System.currentTimeMillis()) }?.value

    val hasAccessKey: Boolean get() = accessKey != null

    fun save(token: String?, mid: Long = 0, expiresIn: Long = 0) {
        if (token.isNullOrBlank()) { clear(); return }
        prefs.edit().putString(KEY, token).putLong(MID_KEY, mid)
            .putLong(EXPIRY_KEY, if (expiresIn > 0) System.currentTimeMillis() + expiresIn * 1000 else 0).apply()
        _revision.value += 1
    }

    fun clear() {
        prefs.edit().remove(KEY).remove(MID_KEY).remove(EXPIRY_KEY).apply()
        _revision.value += 1
    }

    private companion object {
        const val PREF_NAME = "qbili_access_token"
        const val KEY = "access_key"
        const val MID_KEY = "access_key_mid"
        const val EXPIRY_KEY = "access_key_expires_at"
    }
}
