package com.qbili.data.remote

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import com.qbili.data.remote.dto.DanmakuPostResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DanmakuPostResponseTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `发送成功以服务端弹幕ID确认数字文案兼容字符串ID`() {
        assertEquals(32161968826613767L, json.decodeFromString<DanmakuPostResponse>(
            """{"code":0,"message":0,"data":{"dmid_str":"32161968826613767","visible":false}}""").requireSent())
        assertEquals(42L, json.decodeFromString<DanmakuPostResponse>(
            """{"code":0,"result":{"dmid":42}}""").requireSent())
    }

    @Test
    fun `零码风控缺失ID和非零码不报告发送成功`() {
        listOf("""{"code":0,"v_voucher":"risk"}""", """{"code":0,"data":{"v_voucher":"risk"}}""",
            """{"code":0,"result":{"v_voucher":"risk"}}""").forEach { raw ->
            assertThrows(BiliRiskControlException::class.java) { json.decodeFromString<DanmakuPostResponse>(raw).requireSent() }
        }
        listOf("""{"code":0}""", """{"code":0,"data":{"dmid":0}}""").forEach { raw ->
            assertThrows(IllegalStateException::class.java) { json.decodeFromString<DanmakuPostResponse>(raw).requireSent() }
        }
        val error = assertThrows(BiliApiException::class.java) {
            json.decodeFromString<DanmakuPostResponse>("""{"code":36705,"message":"等级不足"}""").requireSent()
        }
        assertEquals(36705, error.code)
    }
}
