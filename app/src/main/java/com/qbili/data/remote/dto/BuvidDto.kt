package com.qbili.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `ExClimbWuzhi` 的请求体：外层只有一个 payload 字段，
 * 里面装的是**被序列化成字符串的** JSON 指纹数据（不是嵌套对象）。
 */
@Serializable
data class ExClimbWuzhiBody(val payload: String)
