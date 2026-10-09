package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Gaia 风控挑战的注册结果。
 *
 * type 取值实测为 "geetest"，另外还可能是 phone / sms / realname / live_detect
 * 这类需要在官方端完成的强验证，第三方客户端只能处理 geetest。
 */
@Serializable
data class GaiaRegisterDto(
    val type: String = "",
    val token: String = "",
    val geetest: GeetestDto? = null,
)

@Serializable
data class GaiaValidateDto(
    @SerialName("is_valid") val isValid: Int = 0,
    /** 验证通过后的凭据，重发原请求时作为 gaia_vtoken 携带 */
    @SerialName("grisk_id") val griskId: String? = null,
) {
    val passed: Boolean get() = isValid == 1
}
