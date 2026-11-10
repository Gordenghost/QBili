package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import com.qbili.core.BiliRiskControlException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class DanmakuPostResponse(
    val code: Int,
    val message: JsonElement? = null,
    val data: JsonObject? = null,
    val result: JsonObject? = null,
    @SerialName("v_voucher") val voucher: String? = null,
) {
    fun requireSent(): Long {
        if (code != 0) throw BiliApiException(code,
            (message as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: "发送弹幕失败")
        val risk = listOf(voucher, (data?.get("v_voucher") as? JsonPrimitive)?.contentOrNull,
            (result?.get("v_voucher") as? JsonPrimitive)?.contentOrNull).firstOrNull { !it.isNullOrBlank() }
        if (risk != null) throw BiliRiskControlException(risk)
        val payload = data ?: result
        return listOf("dmid_str", "dmid").firstNotNullOfOrNull { key ->
            (payload?.get(key) as? JsonPrimitive)?.contentOrNull?.toLongOrNull()?.takeIf { it > 0 }
        } ?: throw IllegalStateException("未确认发送结果，请勿立即重复发送")
    }
}
