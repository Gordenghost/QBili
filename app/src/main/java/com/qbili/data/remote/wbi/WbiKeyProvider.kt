package com.qbili.data.remote.wbi

import android.util.Log
import com.qbili.data.remote.ApiConstants
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 负责获取并缓存 WBI 的 img_key / sub_key。
 *
 * key 每天更换，这里缓存 [CACHE_TTL_MILLIS]，并支持在遇到 -412 时主动失效重取。
 * 使用一个**不带 WBI 拦截器**的裸客户端拉取 nav，避免拦截器递归。
 */
class WbiKeyProvider(
    private val bareClient: () -> OkHttpClient,
    private val json: Json,
) {
    private data class Keys(val mixinKey: String, val fetchedAt: Long)

    private val mutex = Mutex()

    @Volatile
    private var cached: Keys? = null

    suspend fun mixinKey(): String? {
        cached?.let { if (System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MILLIS) return it.mixinKey }
        return mutex.withLock {
            // 双检：可能已被其他协程刷新
            cached?.let { if (System.currentTimeMillis() - it.fetchedAt < CACHE_TTL_MILLIS) return@withLock it.mixinKey }
            val fetched = runCatching { fetchMixinKey() }
                .onFailure { Log.w(TAG, "获取 WBI key 失败", it) }
                .getOrNull()
            if (fetched != null) cached = Keys(fetched, System.currentTimeMillis())
            fetched ?: cached?.mixinKey
        }
    }

    fun invalidate() {
        cached = null
    }

    private fun fetchMixinKey(): String {
        val request = Request.Builder()
            .url(ApiConstants.API_BASE + "x/web-interface/nav")
            .header("User-Agent", ApiConstants.USER_AGENT)
            .header("Referer", ApiConstants.WEB_REFERER)
            .build()

        bareClient().newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            require(body.isNotBlank()) { "nav 返回空 body (HTTP ${response.code})" }

            // 未登录时 code = -101，但 data.wbi_img 依然存在，所以不校验 code
            val wbiImg = json.parseToJsonElement(body)
                .jsonObject["data"]?.jsonObject?.get("wbi_img")?.jsonObject
                ?: error("nav 响应缺少 data.wbi_img")

            val imgKey = wbiImg["img_url"]?.jsonPrimitive?.content?.let(::keyFromUrl)
                ?: error("缺少 img_url")
            val subKey = wbiImg["sub_url"]?.jsonPrimitive?.content?.let(::keyFromUrl)
                ?: error("缺少 sub_url")

            return WbiSigner.mixinKey(imgKey, subKey).also {
                Log.d(TAG, "WBI key 已更新 (imgKey=${imgKey.take(8)}…)")
            }
        }
    }

    /** `https://i0.hdslb.com/bfs/wbi/7cd084941338484aae1ad9425b84077c.png` -> `7cd0…077c` */
    private fun keyFromUrl(url: String): String =
        url.substringAfterLast('/').substringBefore('.')

    private companion object {
        const val TAG = "WbiKeyProvider"
        val CACHE_TTL_MILLIS = TimeUnit.HOURS.toMillis(6)
    }
}
