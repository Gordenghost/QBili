package com.qbili.data.remote

import com.qbili.data.remote.api.CourseApi
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
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

class CourseApiTest {
    @Test
    fun `retrofit uses independent pugv paths fixed query and explicit cookie without defaults`() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{}}""".toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://api.bilibili.com/").client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(CourseApi::class.java)
        api.detail("SESSDATA=session", 61, null)
        api.detail("", null, 790)
        api.playurl("SESSDATA=session", 790, 123, 80, 4048, 0, 1)
        assertEquals("/pugv/view/web/season", requests[0].url.encodedPath)
        assertEquals("61", requests[0].url.queryParameter("season_id"))
        assertNull(requests[0].url.queryParameter("ep_id"))
        assertEquals("790", requests[1].url.queryParameter("ep_id"))
        assertNull(requests[1].url.queryParameter("season_id"))
        assertEquals("", requests[1].header("Cookie"))
        assertEquals("/pugv/player/web/playurl", requests[2].url.encodedPath)
        assertEquals("ep_id=790&cid=123&qn=80&fnval=4048&fnver=0&fourk=1", requests[2].url.encodedQuery)
        requests.forEach { assertEquals("GET", it.method); assertNull(it.url.queryParameter("access_key")) }
    }
}
