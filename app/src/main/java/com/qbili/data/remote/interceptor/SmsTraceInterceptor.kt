package com.qbili.data.remote.interceptor

import com.qbili.core.QBiliLog
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 短信登录接口的原始流量追踪。
 *
 * DTO 只声明了我们已知的字段，服务端悄悄加的新字段（比如标记投递结果的
 * 某个 flag）会被静默丢弃，排查「接口成功但短信不到」时就成了盲区。
 * 这里把 sms/send 与 login/sms 的完整响应体原样写进文件日志。
 *
 * 放在拦截器链最末位：入口看到的 request 已是最终签名形态，
 * peekBody 取的是缓冲副本，不影响 Retrofit 正常消费流。
 */
class SmsTraceInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (request.url.encodedPath.contains("/passport-login/sms")) {
            val body = runCatching { response.peekBody(MAX_PEEK_BYTES).string() }
                .getOrDefault("<peek失败>")
            QBiliLog.i(
                TAG,
                "${request.method} ${request.url.encodedPath} -> HTTP ${response.code} $body",
            )
        }

        return response
    }

    private companion object {
        const val TAG = "Net"
        const val MAX_PEEK_BYTES = 64L * 1024
    }
}
