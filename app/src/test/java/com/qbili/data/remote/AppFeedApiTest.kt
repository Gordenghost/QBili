package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.core.crypto.AppSigner
import com.qbili.data.local.DeviceIdStore
import com.qbili.data.remote.api.AppFeedApi
import com.qbili.data.remote.dto.requireData
import com.qbili.data.remote.interceptor.AppSignInterceptor
import com.qbili.data.remote.interceptor.HeaderInterceptor
import com.qbili.data.repository.appRecommendationParameters
import com.qbili.data.repository.toVideoItemOrNull
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import retrofit2.Retrofit

class AppFeedApiTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun api(client: OkHttpClient): AppFeedApi = Retrofit.Builder().baseUrl(ApiConstants.APP_BASE)
        .client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(AppFeedApi::class.java)

    @Test
    fun `请求真实App路径附设备账号身份且签名字节与发送一致`() = runBlocking {
        var sent: Request? = null
        val client = OkHttpClient.Builder().addInterceptor(HeaderInterceptor())
            .addInterceptor(AppSignInterceptor()).addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("OK").body("""{"code":0,"data":{"items":[]}}""".toResponseBody()).build()
            }.build()
        api(client).recommend(appRecommendationParameters(4_000_000_000L, "token +&中"), "XYdevice").requireData()
        val request = requireNotNull(sent)
        assertEquals("app.bilibili.com", request.url.host)
        assertEquals("/x/v2/feed/index", request.url.encodedPath)
        assertEquals("4000000000", request.url.queryParameter("idx"))
        assertEquals("false", request.url.queryParameter("pull"))
        assertEquals("token +&中", request.url.queryParameter("access_key"))
        assertEquals("android", request.url.queryParameter("mobi_app"))
        assertEquals(AppSigner.ANDROID.appKey, request.url.queryParameter("appkey"))
        assertEquals("XYdevice", request.header("buvid"))
        assertEquals(AppSigner.USER_AGENT, request.header("User-Agent"))
        assertNull(request.header("Referer"))
        assertNull(request.header("Origin"))
        assertNull(request.header(ApiConstants.HEADER_NEED_APP_SIGN))
        val query = requireNotNull(request.url.encodedQuery).substringBeforeLast("&sign=")
        val signature = MessageDigest.getInstance("MD5").digest((query + AppSigner.ANDROID.secret).toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(signature, request.url.queryParameter("sign"))
        assertTrue(query.contains("token%20%2B%26%E4%B8%AD"))
    }

    @Test
    fun `App推荐账号凭据不会出现在网络日志文本`() {
        val message = "--> GET https://app.bilibili.com/x/v2/feed/index?idx=0&access_key=secret-token&sign=abc"
        val redacted = redactAccessKey(message)
        assertFalse(redacted.contains("secret-token"))
        assertTrue(redacted.contains("access_key=<redacted>&sign=abc"))
        assertEquals("GET /x/v2/feed/index", redactAccessKey("GET /x/v2/feed/index"))
    }

    @Test
    fun `匿名真实App推荐解析卡片并沿服务端游标翻页`() = runBlocking {
        Assume.assumeTrue("设置 QBILI_FEED_LIVE_TEST=1 才运行 App 推荐匿名联网验证",
            System.getenv("QBILI_FEED_LIVE_TEST") == "1")
        val client = OkHttpClient.Builder().addInterceptor(HeaderInterceptor()).addInterceptor(AppSignInterceptor())
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).build()
        val api = api(client)
        val buvid = DeviceIdStore.newDeviceId()
        val firstItems = requireNotNull(api.recommend(appRecommendationParameters(0, null), buvid).requireData().items)
        assertTrue(firstItems.isNotEmpty())
        val videos = firstItems.mapNotNull { it.toVideoItemOrNull() }
        assertTrue(videos.isNotEmpty())
        assertTrue(videos.all { it.aid > 0 && it.bvid.matches(Regex("BV1[0-9a-zA-Z]{9}")) })
        val cursor = requireNotNull(firstItems.last().idx)
        val nextItems = requireNotNull(api.recommend(appRecommendationParameters(cursor, null), buvid).requireData().items)
        assertTrue(nextItems.isNotEmpty())
        assertTrue(requireNotNull(nextItems.last().idx) < cursor)
        assertTrue(nextItems.mapNotNull { it.toVideoItemOrNull() }.isNotEmpty())
        println("[AppFeed] 匿名推荐两页解析成功，首批 ${videos.size} 个视频，游标分页有效")
    }
}
