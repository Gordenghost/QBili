package com.qbili.data.remote.dto

import com.qbili.core.BiliApiException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * B 站接口统一外层包装。
 * 不同接口的错误文案字段名不一致（message / msg），两个都收。
 */
@Serializable
data class BiliResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val msg: String? = null,
    val ttl: Int? = null,
    val data: T? = null,
) {
    val errorMessage: String
        get() = message?.takeIf { it.isNotBlank() }
            ?: msg?.takeIf { it.isNotBlank() }
            ?: "未知错误"

    val isSuccess: Boolean get() = code == 0
}

/** 取出 data，失败或 data 为空时抛 [BiliApiException] */
fun <T> BiliResponse<T>.requireData(): T {
    if (!isSuccess) throw BiliApiException(code, errorMessage)
    return data ?: throw BiliApiException(code, "接口返回数据为空")
}

/** 只关心成功与否的接口（点赞/投币等） */
fun BiliResponse<*>.requireSuccess() {
    if (!isSuccess) throw BiliApiException(code, errorMessage)
}

/** 分页游标，B 站新接口普遍使用 */
@Serializable
data class PageCursor(
    @SerialName("has_more") val hasMore: Boolean = false,
    val offset: String? = null,
    val next: Long? = null,
)
