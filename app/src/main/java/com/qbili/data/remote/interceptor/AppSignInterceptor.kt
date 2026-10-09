package com.qbili.data.remote.interceptor

import com.qbili.core.crypto.AppSigner
import com.qbili.data.remote.ApiConstants
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * 为移动端接口补上 appkey / ts / sign 以及 BiliDroid UA。
 *
 * 触发条件：host 是 app.bilibili.com，或请求带 [ApiConstants.HEADER_NEED_APP_SIGN] 标记
 * （passport 上的移动端登录接口与 Web 接口同域，只能靠标记区分）。
 *
 * 标记头的值选择身份：
 * - `hd`：HD 版（android_hd）身份。**短信登录必须用它**——用手机版身份时
 *   接口成功但短信被风控静默丢弃（详见 [AppSigner.ANDROID_HD]）；
 * - 其他/缺省：手机版身份。
 *
 * GET 签名写进 query，POST 表单签名写进 body——两者的签名内容都必须与
 * 真正发出去的字节完全一致，所以统一由 [AppSigner] 产出最终字符串后整体替换。
 */
class AppSignInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val marker = original.header(ApiConstants.HEADER_NEED_APP_SIGN)
        val isAppHost = original.url.host == ApiConstants.APP_HOST

        if (marker == null && !isAppHost) return chain.proceed(original)

        val isHd = marker == MARKER_HD
        val credential = if (isHd) AppSigner.ANDROID_HD else AppSigner.ANDROID
        val commonParams = if (isHd) AppSigner.HD_COMMON_PARAMS else AppSigner.COMMON_PARAMS

        val builder = original.newBuilder()
            .removeHeader(ApiConstants.HEADER_NEED_APP_SIGN)
            // 移动端接口校验 UA；Referer/Origin 是网页概念，带上反而不一致
            .header(
                "User-Agent",
                if (isHd) AppSigner.USER_AGENT_HD else AppSigner.USER_AGENT,
            )
            .removeHeader("Referer")
            .removeHeader("Origin")

        // HD 身份必须带全套客户端风控头，缺了会被判定为非官方流量——
        // 表现正是短信「接口成功但不投递」。逐字段对齐 PiliPlus 的实测值。
        if (isHd) {
            builder
                .header("env", "prod")
                .header("app-key", "android_hd")
                .header("x-bili-trace-id", HD_TRACE_ID)
                .header("x-bili-aurora-eid", "")
                .header("x-bili-aurora-zone", "")
                .header("bili-http-engine", "cronet")
        }

        val body = original.body
        if (body is FormBody) {
            builder.method(original.method, signFormBody(body, credential, commonParams))
        } else {
            builder.url(signUrl(original, credential, commonParams))
        }

        return chain.proceed(builder.build())
    }

    private fun signUrl(
        request: Request,
        credential: AppSigner.Credential,
        commonParams: Map<String, String>,
    ): okhttp3.HttpUrl {
        val params = LinkedHashMap<String, String>()
        for (name in request.url.queryParameterNames) {
            params[name] = request.url.queryParameter(name).orEmpty()
        }
        return request.url.newBuilder()
            .encodedQuery(AppSigner.signedQuery(params, credential, commonParams = commonParams))
            .build()
    }

    private fun signFormBody(
        body: FormBody,
        credential: AppSigner.Credential,
        commonParams: Map<String, String>,
    ): okhttp3.RequestBody {
        val params = LinkedHashMap<String, String>()
        for (i in 0 until body.size) params[body.name(i)] = body.value(i)
        return AppSigner.signedQuery(params, credential, commonParams = commonParams)
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
    }

    private companion object {
        const val MARKER_HD = "hd"

        /** PiliPlus 使用的固定 trace-id（32 个 1 : 16 个 1 : 0 : 0） */
        const val HD_TRACE_ID = "11111111111111111111111111111111:1111111111111111:0:0"
    }
}
