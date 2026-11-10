package com.qbili.data.repository

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.qbili.data.remote.ApiConstants
import com.qbili.data.remote.api.RankingApi
import com.qbili.domain.model.SeasonRankingCategory
import com.qbili.domain.model.SeasonRankingPeriod
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Assume
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class SeasonRankingApiTest {
    @Test
    fun `匿名请求各正片榜三七日能够解析完整列表与有效评分`() = runBlocking {
        Assume.assumeTrue("设置QBILI_SEASON_RANKING_LIVE_TEST=1才运行匿名榜单验证",
            System.getenv("QBILI_SEASON_RANKING_LIVE_TEST") == "1")
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
                .header("User-Agent", ApiConstants.USER_AGENT).header("Referer", "https://www.bilibili.com/").build()) }.build()
        val api = Retrofit.Builder().baseUrl(ApiConstants.API_BASE).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build().create(RankingApi::class.java)
        val repository = RankingRepository(api)
        SeasonRankingCategory.entries.forEach { category ->
            SeasonRankingPeriod.entries.forEach { period ->
                val page = repository.seasonRanking(category, period)
                assertTrue("${category.label} ${period.label}应有作品", page.seasons.isNotEmpty())
                assertFalse(page.hasMore)
                assertTrue(page.videos.isEmpty())
                assertTrue(page.seasons.all { it.seasonId > 0 && it.rank > 0 })
                assertEquals(page.seasons.size, page.seasons.distinctBy { it.seasonId }.size)
                assertTrue(page.seasons.all { it.score == null || it.score.isFinite() && it.score > 0 && it.score <= 10 })
                println("[SeasonRanking] ${category.label} ${period.label}: ${page.seasons.size}部，${page.seasons.count { it.score != null }}部有评分")
            }
        }
    }
}
