package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.api.RankingApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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

class RankingApiTest {
    @Test
    fun `热门接口使用正确路径查询参数和WBI标记且无默认参数`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"list":[]}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(RankingApi::class.java)
        api.popular(2, 20); api.ranking(4, "all", "333.934"); api.weeklyIssues(); api.weekly(394); api.precious()
        assertEquals(listOf("/x/web-interface/popular", "/x/web-interface/ranking/v2",
            "/x/web-interface/popular/series/list", "/x/web-interface/popular/series/one",
            "/x/web-interface/popular/precious"), requests.map { it.url.encodedPath })
        assertEquals("2", requests[0].url.queryParameter("pn"))
        assertEquals("20", requests[0].url.queryParameter("ps"))
        assertEquals("4", requests[1].url.queryParameter("rid"))
        assertEquals("all", requests[1].url.queryParameter("type"))
        assertEquals("333.934", requests[1].url.queryParameter("web_location"))
        assertEquals("394", requests[3].url.queryParameter("number"))
        assertNull(requests[0].header(ApiConstants.HEADER_NEED_WBI))
        requests.drop(1).forEach { assertEquals("1", it.header(ApiConstants.HEADER_NEED_WBI)) }
        requests.forEach { assertEquals("GET", it.method); assertNull(it.url.queryParameter("access_key")) }
    }
}
