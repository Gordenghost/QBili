package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.api.RelatedVideoApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit

class RelatedVideoApiTest {
    @Test
    fun `相关推荐无默认参数使用Web接口登录与游客显式Cookie`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":[]}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build()
            .create(RelatedVideoApi::class.java)
        api.related("SESSDATA=session", "BV1xx411c7mD", null)
        api.related("", null, 2)
        assertEquals("BV1xx411c7mD", requests[0].url.queryParameter("bvid"))
        assertNull(requests[0].url.queryParameter("aid"))
        assertNull(requests[1].url.queryParameter("bvid"))
        assertEquals("2", requests[1].url.queryParameter("aid"))
        assertEquals("SESSDATA=session", requests[0].header("Cookie"))
        assertEquals("", requests[1].header("Cookie"))
        requests.forEach {
            assertEquals("GET", it.method)
            assertEquals("/x/web-interface/archive/related", it.url.encodedPath)
            assertNull(it.url.queryParameter("access_key"))
            assertNull(it.header(ApiConstants.HEADER_NEED_WBI))
        }
    }
}
