package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.api.DanmakuPostApi
import com.qbili.data.remote.interceptor.WbiInterceptor
import com.qbili.data.remote.wbi.WbiKeyProvider
import com.qbili.data.remote.wbi.WbiSigner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class DanmakuPostApiTest {
    @Test
    fun `WBI查询与实际请求逐字节一致且弹幕表单特殊字符不被签名过滤`() = runBlocking {
        val imgKey = "7cd084941338484aae1ad9425b84077c"
        val subKey = "4932caff0ff746eab6f01bf08b70ac45"
        val bare = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/$imgKey.png","sub_url":"https://i0.hdslb.com/bfs/wbi/$subKey.png"}}}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor(WbiInterceptor(WbiKeyProvider({ bare }, Json)))
            .addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"code":0,"data":{"dmid_str":"42"}}""".toResponseBody("application/json".toMediaType())).build()
            }.build()
        val api = Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build().create(DanmakuPostApi::class.java)
        val message = "你好 & + % ' ! 😄"
        assertEquals(42L, api.send("SESSDATA=session; bili_jct=csrf; DedeUserID=42", "1315873", "csrf",
            1, 62131, "BV1xx411c7mD", message, 5000, 0xFFFFFF, 25, 0, 1, 123456, "csrf").requireSent())
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/x/v2/dm/post", request.url.encodedPath)
        assertEquals("SESSDATA=session; bili_jct=csrf; DedeUserID=42", request.header("Cookie"))
        assertNull(request.header(ApiConstants.HEADER_NEED_WBI))
        val timestamp = request.url.queryParameter("wts")!!.toLong()
        assertEquals(WbiSigner.signedQuery(mapOf("csrf" to "csrf", "web_location" to "1315873"),
            WbiSigner.mixinKey(imgKey, subKey), timestamp), request.url.encodedQuery)
        val body = request.body as FormBody
        val fields = (0 until body.size).associate { index -> body.name(index) to body.value(index) }
        assertEquals(message, fields["msg"])
        assertEquals("62131", fields["oid"])
        assertEquals("BV1xx411c7mD", fields["bvid"])
        assertEquals("5000", fields["progress"])
        assertEquals("csrf", fields["csrf"])
        assertEquals("25", fields["fontsize"])
        assertEquals("0", fields["pool"])
        assertNull(fields["access_key"])
    }
}
