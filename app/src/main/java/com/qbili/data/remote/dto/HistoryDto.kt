package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class HistoryDataDto(val list: List<HistoryItemDto>? = null, val cursor: HistoryCursorDto? = null)

@Serializable
data class HistoryCursorDto(val max: Long = 0, @SerialName("view_at") val viewAt: Long = 0,
    val business: String = "")

@Serializable
data class HistoryResourceDto(val oid: Long = 0, val bvid: String = "", val cid: Long = 0,
    val page: Int = 1, val part: String = "", val business: String = "", val epid: Long = 0)

@Serializable
data class HistoryItemDto(
    val kid: Long = 0,
    val title: String = "",
    val cover: String = "",
    val covers: List<String> = emptyList(),
    val history: HistoryResourceDto? = null,
    @SerialName("author_name") val authorName: String = "",
    @SerialName("view_at") val viewAt: Long = 0,
    val progress: Long = 0,
    val duration: Int = 0,
    val uri: String = "",
)

@Serializable
data class HistoryWriteResponse(
    val code: Int = -1,
    val message: String = "接口返回无效",
    val data: JsonElement? = null,
    val result: JsonElement? = null,
) {
    fun requireSuccess() {
        if (code != 0) throw BiliApiException(code, message)
        if (listOf(data, result).any { it is JsonObject && "v_voucher" in it }) {
            throw BiliApiException(-352, "观看历史请求触发风控，请稍后重试")
        }
    }
}
