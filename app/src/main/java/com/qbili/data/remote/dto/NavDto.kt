package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `/x/web-interface/nav` 的返回。
 *
 * 未登录时接口返回 code = -101，但 data 里的 wbi_img 依然有效，
 * 所以 WBI key 的获取不依赖登录态。
 *
 * 刻意不声明 `level_info.next_exp`：该字段在满级（Lv6）时返回字符串 "--"、
 * 未满级时返回数字，类型不稳定，声明了反而会让整个 nav 解析失败。
 */
@Serializable
data class NavDto(
    val isLogin: Boolean = false,
    val mid: Long = 0,
    val uname: String = "",
    val face: String = "",
    /** 硬币数 */
    val money: Double = 0.0,
    /** 节操值 */
    val moral: Int = 0,
    val vipStatus: Int = 0,
    val vipType: Int = 0,
    @SerialName("level_info") val levelInfo: LevelInfoDto? = null,
    val wallet: WalletDto? = null,
    @SerialName("wbi_img") val wbiImgDto: WbiImgDto? = null,
)

@Serializable
data class LevelInfoDto(
    @SerialName("current_level") val currentLevel: Int = 0,
    @SerialName("current_exp") val currentExp: Int = 0,
)

@Serializable
data class WalletDto(
    /** B 币余额 */
    @SerialName("bcoin_balance") val bcoinBalance: Double = 0.0,
)

@Serializable
data class WbiImgDto(
    @SerialName("img_url") val imgUrl: String = "",
    @SerialName("sub_url") val subUrl: String = "",
)
