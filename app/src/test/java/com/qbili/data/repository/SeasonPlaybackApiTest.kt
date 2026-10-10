package com.qbili.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.api.SeasonApi
import com.qbili.data.remote.interceptor.HeaderInterceptor
import com.qbili.domain.player.selectStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class SeasonPlaybackApiTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val client = OkHttpClient.Builder().addInterceptor(HeaderInterceptor())
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS).build()
    private val repository = SeasonRepository(Retrofit.Builder().baseUrl(ApiConstants.API_BASE)
        .client(client).addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(SeasonApi::class.java))

    @Before
    fun requireOptIn() {
        Assume.assumeTrue("设置 QBILI_PGC_LIVE_TEST=1 才运行番剧匿名联网验证",
            System.getenv("QBILI_PGC_LIVE_TEST") == "1")
    }

    @Test
    fun `真实正片详情播放地址选轨及CDN可读取`() = runBlocking {
        val detail = repository.detail(3398)
        assertTrue(detail.episodes.isNotEmpty())
        val episode = detail.episodes.first { it.status == 2 && it.cid > 0 }
        val result = repository.playurl(episode, 80)
        assertFalse(result.preview)
        val stream = requireNotNull(result.selectStream(80))
        val urls = stream.dash?.let { listOfNotNull(it.videoUrl, it.audioUrl) }
            ?: stream.segments.map { it.url }
        urls.forEach { url ->
            val request = Request.Builder().url(url)
                .header("User-Agent", ApiConstants.USER_AGENT)
                .header("Referer", "https://www.bilibili.com/bangumi/play/ep${episode.id}")
                .header("Range", "bytes=0-2047").build()
            client.newCall(request).execute().use { response ->
                assertTrue("CDN HTTP ${response.code}", response.isSuccessful)
                val buffer = okio.Buffer()
                assertTrue(requireNotNull(response.body).source().read(buffer, 2048) > 0)
            }
        }
        println("[PGC] ${detail.title} / ${episode.title}，${stream.label}，音视频 CDN 可读取")
    }

    @Test
    fun `真实会员剧集匿名请求只能显示试看`() = runBlocking {
        val detail = repository.detail(3398)
        val episode = detail.episodes.first { it.status == 13 && it.cid > 0 }
        val result = repository.playurl(episode, 80)
        assertTrue("匿名会员剧集必须保留试看标记", result.preview)
        assertTrue(result.selectStream(80) != null)
        println("[PGC] ${episode.title}：已正确识别试看")
    }
}
