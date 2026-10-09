package com.qbili.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * playurl 接口的 data 字段。
 *
 * 新版结构 (fnver=4096) 返回 dash、hls、durl 等多种格式。
 * 我们优先使用 dash（自适应码率、支持 4K/杜比），其次 hls，最后 durl (flv/mp4 旧格式)。
 */
@Serializable
data class PlayurlDataDto(
    @SerialName("from") val from: String = "",
    val result: String = "",
    val message: String = "",
    val quality: Int = 0,
    val format: String = "",
    val timelength: Long = 0,
    val accept_description: List<String> = emptyList(),
    val accept_quality: List<Int> = emptyList(),
    val video_codecid: Int = 0,
    val seek_param: String = "",
    val seek_type: String = "",
    val dash: DashDto? = null,
    val hls: HlsDto? = null,
    val durl: List<DurlDto> = emptyList(),
    val high_format: Int = 0,
    val last_play_cid: Long = 0,
    val from_client: String = "",
    val video_project: Boolean = false,
    val video_type: Int = 0,
    val is_preview: Int = 0,
) {
    /** 判断是否有可用的播放地址 */
    val hasPlayableUrl: Boolean
        get() = dash != null || hls != null || durl.isNotEmpty()
}

@Serializable
data class DashDto(
    val duration: Long = 0,
    val min_buffer_time: Double = 0.0,
    val minBufferTime: Double = 0.0,
    val video: List<DashStreamDto> = emptyList(),
    val audio: List<DashStreamDto> = emptyList(),
    val dolby: DashDolbyDto? = null,
    val flac: DashFlacDto? = null,
)

@Serializable
data class DashStreamDto(
    val id: Int = 0,
    val base_url: String = "",
    val baseUrl: String = "",
    val backup_url: List<String> = emptyList(),
    val backupUrl: List<String> = emptyList(),
    val bandwidth: Long = 0,
    @SerialName("codecs") val codecs: String = "",
    val mime_type: String = "",
    val mimeType: String = "",
    val codecid: Int = 0,
    val frame_rate: String = "",
    val frameRate: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sar: String = "",
    val start_with_sap: Int = 0,
    val startWithSap: Int = 0,
    val segment_base: SegmentBaseDto? = null,
    val SegmentBase: SegmentBaseDto? = null,
) {
    val url: String
        get() = if (baseUrl.isNotBlank()) baseUrl else base_url
    val backupUrls: List<String>
        get() = if (backupUrl.isNotEmpty()) backupUrl else backup_url
}

@Serializable
data class SegmentBaseDto(
    val initialization: String = "",
    val index_range: String = "",
)

@Serializable
data class DashDolbyDto(
    val type: Int = 0,
    val audio: List<DashStreamDto> = emptyList(),
)

@Serializable
data class DashFlacDto(
    val audio: List<DashStreamDto> = emptyList(),
)

@Serializable
data class HlsDto(
    val duration: Long = 0,
    val min_buffer_time: Double = 0.0,
    val video: List<HlsStreamDto> = emptyList(),
    val audio: List<HlsStreamDto> = emptyList(),
)

@Serializable
data class HlsStreamDto(
    val id: Int = 0,
    val base_url: String = "",
    val baseUrl: String = "",
    val backup_url: List<String> = emptyList(),
    val backupUrl: List<String> = emptyList(),
    val bandwidth: Long = 0,
    val codecs: String = "",
    val mime_type: String = "",
    val mimeType: String = "",
    val codecid: Int = 0,
    val frame_rate: String = "",
    val frameRate: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sar: String = "",
    val start_with_sap: Int = 0,
    val startWithSap: Int = 0,
) {
    val url: String
        get() = if (baseUrl.isNotBlank()) baseUrl else base_url
    val backupUrls: List<String>
        get() = if (backupUrl.isNotEmpty()) backupUrl else backup_url
}

@Serializable
data class DurlDto(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val ahead: String = "",
    val vhead: String = "",
    val url: String = "",
    val url_pfx: String = "",
    val backup_url: List<String> = emptyList(),
    val backupUrl: List<String> = emptyList(),
) {
    val fullUrl: String
        get() = if (url_pfx.isNotBlank()) "$url_pfx$url" else url
}

/*
 * 这里刻意不声明 support_formats / accept_format。
 *
 * 一是它们的 codecs 字段实际是字符串数组（["avc1.640032", ...]）而非对象数组，
 * 之前按对象声明会让整个 playurl 响应解析失败；
 * 二是更根本的原因——这两个字段会**虚报**更高画质：未登录时 accept_quality
 * 列到 1080P+，但 dash.video 里只有 480P/360P。真实可选画质一律从 dash 轨道
 * 推导（见 StreamSelector），所以这两个字段没有任何消费者。
 */