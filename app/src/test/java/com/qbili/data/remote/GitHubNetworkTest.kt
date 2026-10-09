package com.qbili.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.api.GitHubReleaseApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import retrofit2.Retrofit

class GitHubNetworkTest {
    private val json = Json { ignoreUnknownKeys = true }
    @Test
    fun `更新客户端使用独立无Cookie网络与固定GitHub地址`() {
        val network = GitHubNetwork()
        val clientField = GitHubNetwork::class.java.getDeclaredField("client").apply { isAccessible = true }
        val client = clientField.get(network) as okhttp3.OkHttpClient
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(20_000, client.readTimeoutMillis)
        assertEquals(30_000, client.callTimeoutMillis)
        assertFalse(client.interceptors.any { it.javaClass.name.startsWith("com.qbili") })
        assertNotNull(network.releaseApi)
    }

    @Test
    fun `Retrofit请求包含GitHub协议头且没有B站Cookie签名或令牌`() = runBlocking {
        var request: Request? = null
        val interceptor = Interceptor { chain ->
            request = chain.request()
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val client = okhttp3.OkHttpClient.Builder()
            .cookieJar(CookieJar.NO_COOKIES)
            .callTimeout(1, TimeUnit.SECONDS)
            .addInterceptor(interceptor)
            .build()
        val api = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubReleaseApi::class.java)
        assertEquals(404, api.latestRelease().code())
        val sent = requireNotNull(request)
        assertEquals("https://api.github.com/repos/Gordenghost/QBili/releases/latest", sent.url.toString())
        assertEquals("GET", sent.method)
        assertEquals("application/vnd.github+json", sent.header("Accept"))
        assertEquals("2022-11-28", sent.header("X-GitHub-Api-Version"))
        assertEquals("QBili-Android", sent.header("User-Agent"))
        assertNull(sent.header("Cookie"))
        assertNull(sent.header("Authorization"))
        assertNull(sent.url.query)
    }
}
