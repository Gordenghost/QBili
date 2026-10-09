package com.qbili.data.remote.interceptor

import com.qbili.data.remote.ApiConstants
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 补齐 B 站风控要求的固定请求头。缺 UA 或 Referer 会直接拿到 -412。
 */
class HeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", ApiConstants.USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9")

        // 已显式指定 Referer 的请求（例如播放器取流需带视频页地址）不覆盖
        if (chain.request().header("Referer") == null) {
            builder.header("Referer", ApiConstants.WEB_REFERER)
        }
        builder.header("Origin", ApiConstants.WEB_ORIGIN)

        return chain.proceed(builder.build())
    }
}
