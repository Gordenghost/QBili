package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.api.HistoryApi
import com.qbili.data.remote.dto.requireData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit

class HistoryApiTest {
    @Test
    fun `Retrofit历史请求使用固定账号Cookie正确游标与CSRF表单且没有默认参数`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"list":[]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(HistoryApi::class.java)
        val cookies = "SESSDATA=oldaccount; bili_jct=csrf; DedeUserID=42"
        assertEquals(emptyList<Any>(), api.list(cookies, 99, 123, "archive", "all", 20).requireData().list)
        api.delete(cookies, "archive_99", "csrf", "jsonp").requireSuccess()
        api.clear(cookies, "csrf", "jsonp").requireSuccess()
        api.heartbeat(cookies, "BV1", 20, 32, 3, "csrf").requireSuccess()
        assertEquals("/x/web-interface/history/cursor", requests[0].url.encodedPath)
        assertEquals("99", requests[0].url.queryParameter("max"))
        assertEquals("123", requests[0].url.queryParameter("view_at"))
        assertEquals("archive", requests[0].url.queryParameter("business"))
        assertEquals("20", requests[0].url.queryParameter("ps"))
        assertEquals("/x/v2/history/delete", requests[1].url.encodedPath)
        val deleted = requests[1].body as FormBody
        assertEquals("kid=archive_99&csrf=csrf&jsonp=jsonp", (0 until deleted.size).joinToString("&") {
            "${deleted.encodedName(it)}=${deleted.encodedValue(it)}"
        })
        assertEquals("/x/v2/history/clear", requests[2].url.encodedPath)
        assertEquals("/x/click-interface/web/heartbeat", requests[3].url.encodedPath)
        val heartbeat = requests[3].body as FormBody
        val fields = (0 until heartbeat.size).associate { heartbeat.name(it) to heartbeat.value(it) }
        assertEquals(mapOf("bvid" to "BV1", "cid" to "20", "played_time" to "32", "type" to "3", "csrf" to "csrf"), fields)
        requests.forEach {
            assertEquals(cookies, it.header("Cookie"))
            assertNull(it.header("X-QBili-Csrf"))
            assertNull(it.url.queryParameter("access_key"))
        }
    }
}
