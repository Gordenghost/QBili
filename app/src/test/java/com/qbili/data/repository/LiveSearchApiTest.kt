package com.qbili.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.api.AppSearchApi
import com.qbili.data.remote.api.SearchApi
import com.qbili.data.remote.api.SuggestApi
import com.qbili.data.remote.interceptor.AppSignInterceptor
import com.qbili.data.remote.interceptor.HeaderInterceptor
import com.qbili.data.remote.interceptor.WbiInterceptor
import com.qbili.data.remote.wbi.WbiKeyProvider
import com.qbili.data.session.GaiaTokenStore
import com.qbili.domain.model.DurationFilter
import com.qbili.domain.model.PartitionFilter
import com.qbili.domain.model.SearchType
import com.qbili.domain.model.SearchOrder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 真机联网测试：用**生产代码的同一套** OkHttp 拦截器、DTO 和 Repository
 * 去打真实接口，用来把「网络层对不对」和「UI 层对不对」彻底分开。
 *
 * 这些依赖（OkHttp / Retrofit / kotlinx.serialization）都是纯 JVM 库，
 * 唯一的 Android 依赖是 Cookie 持久化，这里用内存实现替代。
 *
 * 默认跳过，不让离线构建挂掉。手动运行：
 *   QBILI_LIVE_TEST=1 ./gradlew :app:testDebugUnitTest --tests "*LiveSearchApiTest"
 */
class LiveSearchApiTest {

    @Before
    fun requireOptIn() {
        Assume.assumeTrue(
            "设置 QBILI_LIVE_TEST=1 才会执行联网测试",
            System.getenv("QBILI_LIVE_TEST") == "1",
        )
    }

    /** 只需要能带上 buvid3，不需要持久化 */
    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        init {
            Cookie.Builder()
                .name(ApiConstants.Cookie.BUVID3)
                .value("ABCDEF01-1234-5678-9ABC-DEF01234567812345infoc")
                .domain("bilibili.com")
                .path("/")
                .expiresAt(Long.MAX_VALUE)
                .build()
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

    private val cookieJar = MemoryCookieJar()

    private val bareClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HeaderInterceptor())
        .build()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HeaderInterceptor())
        .addInterceptor(WbiInterceptor(WbiKeyProvider({ bareClient }, json)))
        // 与生产代码一致：放在最后，才能覆盖掉桌面 UA
        .addInterceptor(AppSignInterceptor())
        // 必须是 network interceptor：它在签名拦截器之后执行，看到的才是最终 URL
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            println(">>> 实际请求: ${request.url}")
            val response = chain.proceed(request)
            println("<<< 响应前 400 字: ${response.peekBody(400).string()}")
            response
        }
        .build()

    private fun retrofit(baseUrl: String) = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val repository = SearchRepository(
        searchApi = retrofit(ApiConstants.API_BASE).create(SearchApi::class.java),
        appSearchApi = retrofit(ApiConstants.APP_BASE).create(AppSearchApi::class.java),
        suggestApi = retrofit(ApiConstants.SUGGEST_BASE).create(SuggestApi::class.java),
        gaiaTokenStore = GaiaTokenStore(),
    )

    /**
     * 这些是诊断用例而不是断言用例：Gaia 风控是否触发取决于当前 IP 的历史请求量，
     * 不是代码正确性的函数。所以统一把风控当成一种合法结果打印出来，
     * 否则频繁跑测试自己就会把套件搞红。
     */
    private fun probe(label: String, block: suspend () -> String) = runBlocking {
        try {
            println("[$label] ${block()}")
        } catch (e: BiliRiskControlException) {
            println("[$label] 触发 Gaia 风控，已正确识别，voucher=${e.voucher}")
        }
    }

    @Test
    fun `单词关键词`() = probe("单词") {
        val page = repository.searchVideos(
            "苦力怕", SearchOrder.TOTAL_RANK, DurationFilter.ALL, PartitionFilter.ALL, 1,
        )
        page.items.take(3).forEach { println("   - ${it.title}") }
        "items=${page.items.size} total=${page.totalResults} hasMore=${page.hasMore}"
    }

    @Test
    fun `含空格的多词关键词`() = probe("多词") {
        val page = repository.searchVideos(
            "我的世界 苦力怕", SearchOrder.TOTAL_RANK, DurationFilter.ALL, PartitionFilter.ALL, 1,
        )
        page.items.take(3).forEach { println("   - ${it.title}") }
        "items=${page.items.size} total=${page.totalResults}"
    }

    @Test
    fun `多词关键词的第二页`() = probe("多词第2页") {
        val page = repository.searchVideos(
            "我的世界 苦力怕", SearchOrder.TOTAL_RANK, DurationFilter.ALL, PartitionFilter.ALL, 2,
        )
        "items=${page.items.size} hasMore=${page.hasMore}"
    }

    @Test
    fun `番剧与专栏搜索`() = probe("番剧") {
        val bangumi = repository.searchSeasons("孤独摇滚", SearchType.BANGUMI, 1)
        val article = repository.searchArticles("编程", 1)
        "番剧=${bangumi.items.size} 专栏=${article.items.size}"
    }

    @Test
    fun `搜索建议对含空格的词`() = runBlocking {
        val single = runCatching { repository.suggest("苦力怕") }
        val multi = runCatching { repository.suggest("我的世界 苦力怕") }
        println("[建议-单词] ${single.getOrNull()?.size ?: "异常: ${single.exceptionOrNull()}"}")
        println("[建议-多词] ${multi.getOrNull()?.size ?: "异常: ${multi.exceptionOrNull()}"}")
    }
}
