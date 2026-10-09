package com.qbili.data.remote

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.BuildConfig
import com.qbili.data.local.CookieStore
import com.qbili.data.remote.cookie.BiliCookieJar
import com.qbili.data.remote.interceptor.AppSignInterceptor
import com.qbili.data.remote.interceptor.CsrfInterceptor
import com.qbili.data.remote.interceptor.HeaderInterceptor
import com.qbili.data.remote.interceptor.SmsTraceInterceptor
import com.qbili.data.remote.interceptor.WbiInterceptor
import com.qbili.data.remote.wbi.WbiKeyProvider
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 全局网络设施：一份 Cookie、一份 OkHttp、按 host 分的若干 Retrofit。
 *
 * 有两个 OkHttpClient：
 * - [bareClient]：只带基础请求头，供 WBI key 拉取使用，避免拦截器递归；
 * - [client]：业务用，叠加 WBI 签名与 csrf 注入。
 */
class BiliNetwork(context: Context) {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    val cookieJar = BiliCookieJar(CookieStore(context, json))

    private val bareClient: OkHttpClient by lazy { baseBuilder().build() }

    val wbiKeyProvider = WbiKeyProvider(bareClient = { bareClient }, json = json)

    val client: OkHttpClient by lazy {
        baseBuilder()
            .addInterceptor(CsrfInterceptor(cookieJar))
            .addInterceptor(WbiInterceptor(wbiKeyProvider))
            // 放在最后：移动端请求要覆盖掉 HeaderInterceptor 设的桌面 UA
            .addInterceptor(AppSignInterceptor())
            // 必须在 AppSign 之后：这样能看到最终签名的请求与未消费的响应体
            .addInterceptor(SmsTraceInterceptor())
            .build()
    }

    /** 图片加载专用：不需要签名与 csrf，但需要 Referer，否则部分图床返回 403 */
    val imageClient: OkHttpClient by lazy { baseBuilder().build() }

    private fun baseBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HeaderInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        // SESSDATA 等敏感 Cookie 不打进日志
                        redactHeader("Cookie")
                        redactHeader("Set-Cookie")
                    },
                )
            }
        }

    fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val apiRetrofit: Retrofit by lazy { retrofit(ApiConstants.API_BASE) }
    val passportRetrofit: Retrofit by lazy { retrofit(ApiConstants.PASSPORT_BASE) }
    val vcRetrofit: Retrofit by lazy { retrofit(ApiConstants.VC_BASE) }
    val liveRetrofit: Retrofit by lazy { retrofit(ApiConstants.LIVE_BASE) }
    val suggestRetrofit: Retrofit by lazy { retrofit(ApiConstants.SUGGEST_BASE) }
    val appRetrofit: Retrofit by lazy { retrofit(ApiConstants.APP_BASE) }
}
