package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.api.DynamicApi
import com.qbili.data.remote.dto.requireData
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
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

class DynamicApiTest {
    @Test
    fun `动态筛选Retrofit按账号关注列表与所选UP独立游标发送请求`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).addInterceptor { chain ->
            requests += chain.request()
            val body = if (chain.request().url.encodedPath == "/x/relation/followings") {
                """{"code":0,"data":{"total":1,"list":[{"mid":42,"uname":"作者","face":"//face.jpg"}]}}"""
            } else """{"code":0,"data":{"items":[]}}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(client)
            .addConverterFactory(Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()))
            .build().create(DynamicApi::class.java)
        assertEquals(42L, api.followedAuthors(7, 2, 50, "desc").requireData().list.single().mid)
        api.space(42, "itemOpusStyle", "up-next").requireData()
        api.following("all", -480, "itemOpusStyle", "all-next").requireData()
        assertEquals("/x/relation/followings", requests[0].url.encodedPath)
        assertEquals("7", requests[0].url.queryParameter("vmid"))
        assertEquals("2", requests[0].url.queryParameter("pn"))
        assertEquals("50", requests[0].url.queryParameter("ps"))
        assertEquals("desc", requests[0].url.queryParameter("order"))
        assertEquals("/x/polymer/web-dynamic/v1/feed/space", requests[1].url.encodedPath)
        assertEquals("42", requests[1].url.queryParameter("host_mid"))
        assertEquals("up-next", requests[1].url.queryParameter("offset"))
        assertEquals("/x/polymer/web-dynamic/v1/feed/all", requests[2].url.encodedPath)
        assertEquals("all-next", requests[2].url.queryParameter("offset"))
        assertNull(requests[2].url.queryParameter("host_mid"))
    }
}
