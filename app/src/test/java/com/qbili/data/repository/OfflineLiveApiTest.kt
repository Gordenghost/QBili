package com.qbili.data.repository

import com.qbili.data.remote.ApiConstants
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OfflineLiveApiTest {
    @Test fun publicDownloadPermissionActualTracksAndCdnRange() {
        assumeTrue(System.getenv("QBILI_OFFLINE_LIVE_TEST") == "1")
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder().cookieJar(CookieJar.NO_COOKIES).retryOnConnectionFailure(false)
            .followRedirects(false).connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
        fun request(url: String) = Request.Builder().url(url).header("User-Agent", ApiConstants.USER_AGENT)
            .header("Referer", ApiConstants.WEB_REFERER)
        fun read(url: String) = client.newCall(request(url).build()).execute().use { response ->
            check(response.isSuccessful); downloadResponseData(json.parseToJsonElement(response.body!!.string()).jsonObject)
        }
        val video = parseDownloadVideo(read("https://api.bilibili.com/x/web-interface/view?aid=170001"), json)
        val page = video.pages.first()
        val options = parseDownloadOptions(read("https://api.bilibili.com/x/player/playurl?avid=170001&cid=${page.cid}&qn=32&fnval=4048&fnver=0&fourk=1"), json, page.durationSeconds)
        for (url in listOf(options.first().videoUrl, options.first().audioUrl)) {
            client.newCall(request(url).header("Range", "bytes=0-1023").build()).execute().use { response ->
                assertEquals(206, response.code)
                val bytes = response.body!!.bytes()
                assertEquals(1024, bytes.size)
                assertEquals("ftyp", String(bytes, 4, 4, Charsets.US_ASCII))
            }
        }
        println("[Offline] 匿名公开视频缓存权限、AVC／音轨及 CDN 小范围读取通过；没有下载完整视频或执行写操作")
    }
}
