package com.qbili.data.local

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie

/**
 * Cookie 的持久化容器。
 *
 * 这里刻意用 SharedPreferences 而不是 DataStore：OkHttp 的 `CookieJar.loadForRequest`
 * 是同步回调，需要在任意线程立刻拿到 Cookie，同步 API 更合适。
 * 数据落在应用私有目录，不会被其他应用读取。
 */
class CookieStore(context: Context, private val json: Json) {

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): List<Cookie> {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(LIST_SERIALIZER, raw).mapNotNull { it.toCookie() }
        }.getOrDefault(emptyList())
    }

    fun save(cookies: Collection<Cookie>) {
        val stored = cookies.map { StoredCookie.from(it) }
        prefs.edit().putString(KEY_COOKIES, json.encodeToString(LIST_SERIALIZER, stored)).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private companion object {
        const val PREF_NAME = "qbili_cookies"
        const val KEY_COOKIES = "cookies"
        val LIST_SERIALIZER = ListSerializer(StoredCookie.serializer())
    }
}

@Serializable
data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val expiresAt: Long,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
) {
    fun toCookie(): Cookie? = runCatching {
        Cookie.Builder()
            .name(name)
            .value(value)
            .path(path)
            .expiresAt(expiresAt)
            .apply {
                if (hostOnly) hostOnlyDomain(domain) else domain(domain)
                if (secure) secure()
                if (httpOnly) httpOnly()
            }
            .build()
    }.getOrNull()

    companion object {
        fun from(cookie: Cookie) = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            domain = cookie.domain,
            path = cookie.path,
            expiresAt = cookie.expiresAt,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
        )
    }
}
