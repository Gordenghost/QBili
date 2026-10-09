package com.qbili.data.remote.interceptor

import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.cookie.BiliCookieJar
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 为带 [ApiConstants.HEADER_NEED_CSRF] 标记的请求自动注入 csrf（即 Cookie 里的 bili_jct）。
 *
 * B 站所有写操作（点赞/投币/收藏/发评论/关注…）都要求携带 csrf，
 * 表单请求放进 body，GET 放进 query。缺失会返回 -111。
 */
class CsrfInterceptor(private val cookieJar: BiliCookieJar) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header(ApiConstants.HEADER_NEED_CSRF) == null) return chain.proceed(original)

        val builder = original.newBuilder().removeHeader(ApiConstants.HEADER_NEED_CSRF)
        val csrf = cookieJar.csrf()

        if (csrf.isNullOrBlank()) {
            // 未登录，直接放行让接口返回 -101，由上层引导登录
            return chain.proceed(builder.build())
        }

        val body = original.body
        if (body is FormBody) {
            val alreadySigned = (0 until body.size).any { body.name(it) == CSRF_KEY }
            if (!alreadySigned) {
                val newBody = FormBody.Builder().apply {
                    for (i in 0 until body.size) addEncoded(body.encodedName(i), body.encodedValue(i))
                    add(CSRF_KEY, csrf)
                }.build()
                builder.method(original.method, newBody)
            }
        } else if (body == null && original.url.queryParameter(CSRF_KEY) == null) {
            builder.url(original.url.newBuilder().addQueryParameter(CSRF_KEY, csrf).build())
        }

        return chain.proceed(builder.build())
    }

    private companion object {
        const val CSRF_KEY = "csrf"
    }
}
