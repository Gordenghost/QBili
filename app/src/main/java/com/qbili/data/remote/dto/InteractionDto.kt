package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `/x/web-interface/archive/relation` 的返回——一次拿到当前用户对这个视频的全部互动状态。
 *
 * 不同版本的 relation 接口把关注状态返回为布尔值或 -1/非负整数；
 * 投币数也可能使用 coin 或 coin_number。类型不兼容会让整份互动状态解析失败。
 */
@Serializable
data class VideoRelationDto(
    val like: Boolean = false,
    val dislike: Boolean = false,
    val favorite: Boolean = false,
    val coin: Int = 0,
    @SerialName("coin_number") val coinNumber: Int? = null,
    @Serializable(with = FollowingStateSerializer::class) val attention: Boolean = false,
    @SerialName("season_fav") val seasonFav: Boolean = false,
) {
    val coins: Int get() = coinNumber ?: coin
}

object FollowingStateSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FollowingState", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val value = (decoder as? JsonDecoder)?.decodeJsonElement()?.jsonPrimitive
            ?: return decoder.decodeBoolean()
        return value.booleanOrNull ?: (value.content.toIntOrNull()?.let { it >= 0 } ?: false)
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}

// ---------- 收藏夹 ----------

@Serializable
data class FavFolderListDto(
    val count: Int = 0,
    val list: List<FavFolderDto>? = null,
)

@Serializable
data class FavFolderDto(
    val id: Long = 0,
    val fid: Long = 0,
    val title: String = "",
    @SerialName("media_count") val mediaCount: Int = 0,
    /**
     * 该视频是否已在这个收藏夹里。
     * 只有请求时带了 `rid`（视频 aid）服务端才会填这个字段。
     */
    @SerialName("fav_state") val favState: Int = 0,
    /** 0 公开 1 私密 */
    val attr: Int = 0,
) {
    val containsCurrentVideo: Boolean get() = favState == 1
}
