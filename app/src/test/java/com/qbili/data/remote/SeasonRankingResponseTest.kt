package com.qbili.data.remote

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonRankingDto
import com.qbili.data.remote.dto.SeasonRankingEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNull
import kotlinx.serialization.json.Json
import org.junit.Test

class SeasonRankingResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `result与data混合评分结构不会阻断榜单并保留接口顺序`() {
        listOf("result", "data").forEach { payload ->
            val response = json.decodeFromString<PgcResponse<SeasonRankingDto>>("""{"code":0,"$payload":{"list":[
                {"season_id":33415,"rank":1,"rating":"8.8分","new_ep":{"index_show":"更新至第1277话"}},
                {"season_id":33378,"rank":2,"rating":{"score":9.4,"count":1000}},
                {"season_id":112827,"rank":3,"rating":""},
                {"season_id":42,"rank":4,"rating":null},
                {"season_id":43,"rank":5}
            ]}}""")
            val items = response.requireResult().list!!.mapIndexed { index, dto -> dto.toRankingItem(index + 1) }
            assertEquals(listOf(33415L, 33378L, 112827L, 42L, 43L), items.map { it.seasonId })
            assertEquals(listOf(1, 2, 3, 4, 5), items.map { it.rank })
            assertEquals(listOf(8.8, 9.4, null, null, null), items.map { it.score })
            assertEquals("更新至第1277话", items.first().progress)
        }
    }

    @Test
    fun `有效数字字符串及带分后缀评分均可识别`() {
        val ratings = listOf("\"8.8分\"", "\" 9.4 分 \"", "\"9.8\"", "9.6", "10", "{\"score\":\"9.7\"}", "{\"score\":10.0}")
        val scores = ratings.map { rating ->
            json.decodeFromString<SeasonRankingEntryDto>("""{"season_id":42,"rating":$rating}""").toRankingItem(1).score
        }
        assertEquals(listOf(8.8, 9.4, 9.8, 9.6, 10.0, 9.7, 10.0), scores)
    }

    @Test
    fun `空缺异常形状非评分文字和无效范围仅忽略评分不忽略作品`() {
        listOf("null", "\"\"", "\"暂无评分\"", "\"评分8.8分\"", "\"8.8分/10分\"", "\"9.8abc\"",
            "\"NaN\"", "\"Infinity\"", "\"0分\"", "\"10.1分\"", "\"-1分\"", "0", "-1", "10.1",
            "true", "false", "[]", "{}", "{\"score\":null}", "{\"score\":true}", "{\"score\":[]}",
            "{\"score\":{\"score\":8.8}}", "{\"score\":\"unknown\"}").forEach { rating ->
            val item = json.decodeFromString<SeasonRankingEntryDto>("""{"season_id":42,"rating":$rating}""").toRankingItem(1)
            assertEquals(42L, item.seasonId)
            assertNull(rating, item.score)
        }
    }

    @Test
    fun `两种榜单外层都能返回对应原始数据`() {
        val data = SeasonRankingDto(list = listOf(SeasonRankingEntryDto(seasonId = 42)))
        assertSame(data, PgcResponse(result = data).requireResult())
        assertSame(data, PgcResponse(data = data).requireResult())
        assertEquals(42L, PgcResponse(data = data).requireResult().list!!.single().seasonId)
    }

    @Test
    fun `result与data零码voucher都不能当作普通榜单返回`() {
        val payload = SeasonRankingDto(list = emptyList(), voucher = "challenge")
        listOf(PgcResponse(result = payload), PgcResponse(data = payload)).forEach { response ->
            val error = assertThrows(BiliRiskControlException::class.java) { response.requireResult() }
            assertEquals("challenge", error.voucher)
        }
    }

    @Test
    fun `非零状态码和空载荷必须报错而非空榜单`() {
        val denied = PgcResponse(code = -352, message = "请求被拦截", data = SeasonRankingDto(list = emptyList()))
        val error = assertThrows(BiliApiException::class.java) { denied.requireResult() }
        assertEquals(-352, error.code)
        assertThrows(BiliApiException::class.java) { PgcResponse<SeasonRankingDto>().requireResult() }
    }
}
