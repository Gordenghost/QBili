package com.qbili.data.remote

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.dto.PgcResponse
import com.qbili.data.remote.dto.SeasonRankingDto
import com.qbili.data.remote.dto.SeasonRankingEntryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class SeasonRankingResponseTest {
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
