package com.qbili.data.remote.cookie

import com.qbili.data.local.CookieStore
import com.qbili.data.remote.ApiConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * 内存 + SharedPreferences 双层 CookieJar。
 *
 * 登录态就是 Cookie 里有没有 SESSDATA，所以这里同时充当「登录状态源」，
 * 通过 [loginState] 对外暴露，UI 层可直接 collect。
 */
class BiliCookieJar(private val store: CookieStore) : CookieJar {

    /** key = "domain|path|name"，与 RFC 6265 的唯一性定义一致 */
    private val cache = ConcurrentHashMap<String, Cookie>()

    private val _loginState = MutableStateFlow(LoginCookies.EMPTY)
    val loginState: StateFlow<LoginCookies> = _loginState.asStateFlow()

    init {
        store.load().forEach { cache[keyOf(it)] = it }
        ensureBuvid()
        publishLoginState()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val expired = mutableListOf<String>()
        val matched = mutableListOf<Cookie>()

        for ((key, cookie) in cache) {
            if (cookie.expiresAt < now) {
                expired += key
            } else if (cookie.matches(url)) {
                matched += cookie
            }
        }
        if (expired.isNotEmpty()) {
            expired.forEach(cache::remove)
            persist()
        }
        return matched
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        for (cookie in cookies) {
            val key = keyOf(cookie)
            // 服务端用「过期时间置于过去」的方式删除 Cookie（例如退出登录）
            if (cookie.expiresAt < now) cache.remove(key) else cache[key] = cookie
        }
        persist()
        publishLoginState()
    }

    fun valueOf(name: String): String? =
        cache.values.firstOrNull { it.name == name && it.expiresAt >= System.currentTimeMillis() }?.value

    /** csrf token，POST 类接口必带 */
    fun csrf(): String? = valueOf(ApiConstants.Cookie.BILI_JCT)

    /** 手动写入 Cookie（用于「从浏览器导入 Cookie」这类兜底登录方式） */
    fun putRaw(name: String, value: String, domain: String = ".bilibili.com") {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain.removePrefix("."))
            .path("/")
            .expiresAt(System.currentTimeMillis() + YEAR_MILLIS)
            .build()
        cache[keyOf(cookie)] = cookie
        persist()
        publishLoginState()
    }

    fun clear() {
        cache.clear()
        store.clear()
        ensureBuvid()
        publishLoginState()
    }

    /**
     * 部分接口缺少 buvid3 会直接 -412。
     * 这里先本地合成一个占位值保证可用；真实的 buvid 由
     * `/x/frontend/finger/spi` 获取后覆盖（见 BuvidRepository）。
     */
    private fun ensureBuvid() {
        if (valueOf(ApiConstants.Cookie.BUVID3) != null) return
        val suffix = (1..5).map { Random.nextInt(10) }.joinToString("")
        putRawInternal(
            ApiConstants.Cookie.BUVID3,
            "${UUID.randomUUID().toString().uppercase()}${suffix}infoc",
        )
    }

    private fun putRawInternal(name: String, value: String) {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain("bilibili.com")
            .path("/")
            .expiresAt(System.currentTimeMillis() + YEAR_MILLIS)
            .build()
        cache[keyOf(cookie)] = cookie
        persist()
    }

    private fun persist() = store.save(cache.values)

    private fun publishLoginState() {
        _loginState.value = LoginCookies(
            sessData = valueOf(ApiConstants.Cookie.SESSDATA),
            biliJct = valueOf(ApiConstants.Cookie.BILI_JCT),
            mid = valueOf(ApiConstants.Cookie.DEDE_USER_ID)?.toLongOrNull(),
        )
    }

    private fun keyOf(cookie: Cookie) = "${cookie.domain}|${cookie.path}|${cookie.name}"

    private companion object {
        const val YEAR_MILLIS = 365L * 24 * 60 * 60 * 1000
    }
}

data class LoginCookies(
    val sessData: String?,
    val biliJct: String?,
    val mid: Long?,
) {
    val isLoggedIn: Boolean get() = !sessData.isNullOrBlank() && mid != null

    companion object {
        val EMPTY = LoginCookies(null, null, null)
    }
}
