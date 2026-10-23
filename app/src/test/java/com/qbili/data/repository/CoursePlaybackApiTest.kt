package com.qbili.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.api.CourseApi
import com.qbili.data.remote.cookie.LoginCookies
import com.qbili.data.remote.interceptor.HeaderInterceptor
import com.qbili.domain.model.CourseTarget
import com.qbili.domain.player.selectStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import retrofit2.Retrofit

class CoursePlaybackApiTest {
    @Test
    fun `public course catalogue trial stream and CDN range readable without account`() = runBlocking {
        Assume.assumeTrue("设置QBILI_COURSE_LIVE_TEST=1才运行课程匿名联网验证", System.getenv("QBILI_COURSE_LIVE_TEST") == "1")
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val client = OkHttpClient.Builder().addInterceptor(HeaderInterceptor()).connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()
        val api = Retrofit.Builder().baseUrl(ApiConstants.API_BASE).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(CourseApi::class.java)
        val repository = CourseRepository(api, MutableStateFlow(LoginCookies.EMPTY), json)
        val detail = repository.detail(LoginCookies.EMPTY, CourseTarget(seasonId = 61))
        assertEquals(61L, detail.id)
        assertTrue(detail.episodes.size > 1)
        assertTrue(detail.episodes.any { !it.playable })
        val episode = detail.episodes.first { it.playable && it.video && it.cid > 0 }
        val result = repository.playurl(LoginCookies.EMPTY, episode, 80)
        val stream = requireNotNull(result.playback.selectStream(80))
        val urls = stream.dash?.let { listOfNotNull(it.videoUrl, it.audioUrl) } ?: stream.segments.map { it.url }
        urls.forEach { url ->
            val request = Request.Builder().url(url).header("Referer", "https://www.bilibili.com/cheese/play/ep${episode.id}")
                .header("Range", "bytes=0-2047").build()
            client.newCall(request).execute().use { response ->
                assertTrue("CDN HTTP ${response.code}", response.isSuccessful)
                val buffer = okio.Buffer()
                assertTrue(requireNotNull(response.body).source().read(buffer, 2048) > 0)
            }
        }
        println("[Course] ${detail.title} / ${episode.title}，${stream.label}，CDN可读取")
    }
}
