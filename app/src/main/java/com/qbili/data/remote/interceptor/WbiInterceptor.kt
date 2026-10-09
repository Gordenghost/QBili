package com.qbili.data.remote.interceptor

import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.wbi.WbiKeyProvider
import com.qbili.data.remote.wbi.WbiSigner
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 自动为需要 WBI 签名的请求补上 `wts` 和 `w_rid`。
 *
 * 触发条件：URL 路径含 `/wbi/`，或请求带 [ApiConstants.HEADER_NEED_WBI] 标记头。
 *
 * 这里用 runBlocking 拉取 key 是安全的——拦截器本身运行在 OkHttp 的工作线程上，
 * 不会阻塞主线程；且 key 有 6 小时缓存，真正发生网络请求的概率极低。
 */
class WbiInterceptor(private val keyProvider: WbiKeyProvider) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val needSign = original.header(ApiConstants.HEADER_NEED_WBI) != null ||
            original.url.encodedPath.contains("/wbi/")

        if (!needSign) return chain.proceed(original)

        val mixinKey = runBlocking { keyProvider.mixinKey() }
        if (mixinKey == null) {
            // 拿不到 key 也照常发出去，让上层看到真实的接口错误而不是签名层的异常
            return chain.proceed(original.newBuilder().removeHeader(ApiConstants.HEADER_NEED_WBI).build())
        }

        val params = LinkedHashMap<String, String>()
        for (name in original.url.queryParameterNames) {
            params[name] = original.url.queryParameter(name).orEmpty()
        }

        val signedUrl = original.url.newBuilder()
            .encodedQuery(WbiSigner.signedQuery(params, mixinKey))
            .build()

        val response = chain.proceed(
            original.newBuilder()
                .removeHeader(ApiConstants.HEADER_NEED_WBI)
                .url(signedUrl)
                .build(),
        )

        // 412 基本等于签名/风控失效，主动让 key 过期，下次请求会重新拉取
        if (response.code == 412) keyProvider.invalidate()
        return response
    }
}
