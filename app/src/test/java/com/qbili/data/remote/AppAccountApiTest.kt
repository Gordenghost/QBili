package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.core.crypto.AppSigner
import com.qbili.data.remote.api.AccountRelationApi
import com.qbili.data.remote.api.AppAccountApi
import com.qbili.data.remote.dto.requireData
import com.qbili.data.remote.interceptor.AppSignInterceptor
import com.qbili.data.remote.interceptor.HeaderInterceptor
import java.security.MessageDigest
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
import org.junit.Test
import retrofit2.Retrofit

class AppAccountApiTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `OAuth校验必须使用access_token字段与HD签名并解析账号有效期`() = runBlocking {
        var sent: Request? = null
        val client = OkHttpClient.Builder().addInterceptor(HeaderInterceptor()).addInterceptor(AppSignInterceptor())
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"code":0,"data":{"mid":42,"access_token":"key","expires_in":3600}}""".toResponseBody()).build()
            }.build()
        val api = Retrofit.Builder().baseUrl(ApiConstants.PASSPORT_BASE).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(AppAccountApi::class.java)
        val status = api.tokenInfo("key +&").requireData()
        assertEquals(42L, status.mid)
        assertEquals(3600L, status.expiresIn)
        val request = requireNotNull(sent)
        assertEquals("passport.bilibili.com", request.url.host)
        assertEquals("/api/v2/oauth2/info", request.url.encodedPath)
        assertEquals("key +&", request.url.queryParameter("access_token"))
        assertNull(request.url.queryParameter("access_key"))
        assertEquals(AppSigner.ANDROID_HD.appKey, request.url.queryParameter("appkey"))
        assertEquals("android_hd", request.url.queryParameter("mobi_app"))
        assertEquals(AppSigner.USER_AGENT_HD, request.header("User-Agent"))
        assertNull(request.header(ApiConstants.HEADER_NEED_APP_SIGN))
        val query = requireNotNull(request.url.encodedQuery).substringBeforeLast("&sign=")
        val signature = MessageDigest.getInstance("MD5").digest((query + AppSigner.ANDROID_HD.secret).toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(signature, request.url.queryParameter("sign"))
        assertFalse(redactAccessKey(request.url.toString()).contains("key%20%2B%26"))
    }

    @Test
    fun `黑名单查询传实际分页参数且不需要App凭据`() = runBlocking {
        var sent: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            sent = chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":0,"data":{"list":[{"mid":77}],"total":51}}""".toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl(ApiConstants.API_BASE).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(AccountRelationApi::class.java)
        val blacklist = api.blacklist(2, 50, 0).requireData()
        assertEquals(listOf(77L), blacklist.list!!.map { it.mid })
        assertEquals("/x/relation/blacks", sent!!.url.encodedPath)
        assertEquals("2", sent!!.url.queryParameter("pn"))
        assertEquals("50", sent!!.url.queryParameter("ps"))
        assertEquals("0", sent!!.url.queryParameter("re_version"))
        assertTrue(sent!!.url.queryParameterNames.none { it == "access_key" || it == "access_token" })
    }
}
