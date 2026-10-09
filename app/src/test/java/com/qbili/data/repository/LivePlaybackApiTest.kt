package com.qbili.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.api.PlayurlApi
import com.qbili.data.remote.api.VideoApi
import com.qbili.data.remote.interceptor.HeaderInterceptor
import com.qbili.domain.model.VideoCodec
import com.qbili.domain.model.VideoQuality
import com.qbili.domain.player.StreamSelector
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 播放链路的联网诊断：详情 -> playurl -> 选轨 -> 弹幕，全部走生产代码。
 *
 * 存在的意义：这是本机唯一能替代真机验证播放链路的手段。
 * 尤其要证实两件事——playurl 端点没写错（曾经指向不存在的 app.bilibili.com 路径），
 * 以及 DASH 的 baseUrl 带上 Referer 后真的能取到数据（缺 Referer 会 403）。
 *
 * 默认跳过。手动运行：
 *   QBILI_LIVE_TEST=1 ./gradlew :app:testDebugUnitTest --tests "*LivePlaybackApiTest"
 */
class LivePlaybackApiTest {

    @Before
    fun requireOptIn() {
        Assume.assumeTrue(
            "设置 QBILI_LIVE_TEST=1 才会执行联网测试",
            System.getenv("QBILI_LIVE_TEST") == "1",
        )
    }

    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        init {
            Cookie.Builder()
                .name(ApiConstants.Cookie.BUVID3)
                .value("ABCDEF01-1234-5678-9ABC-DEF01234567812345infoc")
                .domain("bilibili.com").path("/").expiresAt(Long.MAX_VALUE).build()
                .let(cookies::add)
        }

        override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies += cookies
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .addInterceptor(HeaderInterceptor())
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(ApiConstants.API_BASE)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val videoApi = retrofit.create(VideoApi::class.java)
    private val repository = VideoRepository(videoApi, retrofit.create(PlayurlApi::class.java))
    private val danmakuRepository = DanmakuRepository(videoApi)

    /** 经典公开视频，长期可用 */
    private val bvid = "BV1GJ411x7h7"

    @Test
    fun `详情到选轨到弹幕的完整链路`() = runBlocking {
        val detail = repository.detail(bvid)
        println("[详情] ${detail.title.take(24)} | aid=${detail.aid} 时长=${detail.durationSeconds}s 分P=${detail.pages.size}")
        require(detail.pages.isNotEmpty()) { "没有可播放的分P" }
        val page = detail.pages.first()
        println("[分P] cid=${page.cid} ${page.title}")

        val playurl = repository.playurl(page.cid, bvid, VideoQuality.Q1080P.qn)
        val dash = playurl.dash
        require(dash != null) { "未返回 DASH" }
        println("[DASH] 视频轨=${dash.video.size} 音频轨=${dash.audio.size}")

        val qualities = StreamSelector.availableQualities(dash)
        println("[可选画质] " + qualities.joinToString { "${it.label}(${it.availableCodecs.map { c -> c.label }})" })

        VideoCodec.entries.forEach { codec ->
            val stream = StreamSelector.select(dash, VideoQuality.Q1080P.qn, codec)
            println("  期望 ${codec.label} -> 实际 ${stream?.qualityLabel} / ${stream?.codec?.label} ${stream?.width}x${stream?.height}")
        }

        // 关键验证：DASH 的 baseUrl 带 Referer 能否真的取到数据（缺 Referer 会 403）
        val stream = StreamSelector.select(dash, VideoQuality.Q1080P.qn, VideoCodec.AVC)!!
        listOf("视频" to stream.videoUrl, "音频" to stream.audioUrl).forEach { (label, url) ->
            if (url == null) return@forEach
            val request = Request.Builder().url(url)
                .header("User-Agent", ApiConstants.USER_AGENT)
                .header("Referer", ApiConstants.WEB_REFERER)
                .header("Range", "bytes=0-2047")
                .build()
            OkHttpClient().newCall(request).execute().use { response ->
                println("[$label 流] HTTP ${response.code} 取到 ${response.body?.bytes()?.size ?: 0} 字节 host=${request.url.host}")
            }
        }

        val danmaku = danmakuRepository.load(page.cid, detail.durationSeconds)
        println("[弹幕] ${danmaku.size} 条")
        danmaku.take(3).forEach { println("   ${it.progressMillis}ms [${it.mode}] w=${it.weight} ${it.content}") }
    }

    /**
     * 观察 CDN 对缺失 Referer 的反应。
     *
     * 实测 mcdn（P2P 节点）并不校验 Referer，会正常返回 206。
     * 但播放器里仍然固定带上——upos 系列节点会 403，而返回哪种节点是服务端决定的，
     * 不能赌每次都分到宽松的那种。这里只做观察、不做断言。
     */
    @Test
    fun `观察 CDN 对缺失 Referer 的反应`() = runBlocking {
        val detail = repository.detail(bvid)
        val playurl = repository.playurl(detail.pages.first().cid, bvid, VideoQuality.Q480P.qn)
        val stream = StreamSelector.select(playurl.dash!!, VideoQuality.Q480P.qn, null)!!

        val request = Request.Builder().url(stream.videoUrl)
            .header("Range", "bytes=0-1023")
            .build()
        OkHttpClient().newCall(request).execute().use { response ->
            println("[无 Referer] HTTP ${response.code} host=${request.url.host}")
        }
    }
}
