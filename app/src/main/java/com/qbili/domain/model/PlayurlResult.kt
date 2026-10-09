package com.qbili.domain.model

import kotlinx.serialization.SerialName

/**
 * 视频清晰度代码，对应 playurl 的 qn 参数。
 * 对照 B 站官方清晰度表：
 * 120=4K, 116=HDR, 112=1080P60, 80=1080P, 74=720P60, 64=720P, 32=480P, 16=360P
 */
enum class VideoQuality(val qn: Int, val label: String) {
    Q4K(120, "4K"),
    HDR(116, "HDR 真彩"),
    Q1080P60(112, "1080P 高帧率"),
    Q1080P(80, "1080P"),
    Q720P60(74, "720P 高帧率"),
    Q720P(64, "720P"),
    Q480P(32, "480P"),
    Q360P(16, "360P"),
}

/** 统一的播放地址结果，统一 dash/hls/durl 三种格式 */
data class PlayurlResult(
    /** dash 自适应流（首选：自适应码率、4K/HDR/杜比/杜比视界） */
    val dash: DashResult? = null,
    /** hls 流（次选：兼容性好、支持求精准 seek） */
    val hls: HlsResult? = null,
    /** durl 旧格式（flv/mp4 单码率，兜底） */
    val durl: List<DurlResult> = emptyList(),
    /** 视频总时长（毫秒） */
    val durationMillis: Long = 0,
    /** 视频总时长（秒，来自 timelength） */
    val timelength: Long = 0,
    /** 接口返回的 format 字段 */
    val format: String = "",
) {
    /** 判断是否有可播放流 */
    val isPlayable: Boolean
        get() = dash != null || hls != null

    companion object {
        /**
         * 将网络层 DTO 转换为领域模型。
         * 优先级：dash > hls > durl
         */
        fun fromDto(dto: com.qbili.data.remote.dto.PlayurlDataDto): PlayurlResult {
            val dashResult = dto.dash?.let { dtoDash ->
                DashResult(
                    duration = dtoDash.duration,
                    minBufferTime = kotlin.math.max(dtoDash.min_buffer_time, dtoDash.minBufferTime),
                    video = dtoDash.video.map { dtoStream ->
                        DashStream(
                            id = dtoStream.id,
                            url = dtoStream.baseUrl,
                            backupUrls = dtoStream.backupUrls,
                            bandwidth = dtoStream.bandwidth,
                            codecs = dtoStream.codecs,
                            mimeType = dtoStream.mimeType,
                            codecid = dtoStream.codecid,
                            frameRate = dtoStream.frameRate,
                            width = dtoStream.width,
                            height = dtoStream.height,
                            segmentBase = dtoStream.segment_base?.let { dtoSeg ->
                                SegmentBase(
                                    initialization = dtoSeg.initialization,
                                    indexRange = dtoSeg.index_range,
                                )
                            },
                        )
                    },
                    audio = dtoDash.audio.map { dtoStream ->
                        DashStream(
                            id = dtoStream.id,
                            url = dtoStream.baseUrl,
                            backupUrls = dtoStream.backupUrls,
                            bandwidth = dtoStream.bandwidth,
                            codecs = dtoStream.codecs,
                            mimeType = dtoStream.mimeType,
                            codecid = dtoStream.codecid,
                            frameRate = dtoStream.frameRate,
                            width = dtoStream.width,
                            height = dtoStream.height,
                        )
                    },
                    dolby = dtoDash.dolby?.let { dtoDolby ->
                        DolbyInfo(
                            type = dtoDolby.type,
                            audio = dtoDolby.audio.map { dtoStream ->
                                DashStream(
                                    id = dtoStream.id,
                                    url = dtoStream.baseUrl,
                                    backupUrls = dtoStream.backupUrls,
                                    bandwidth = dtoStream.bandwidth,
                                    codecs = dtoStream.codecs,
                                    mimeType = dtoStream.mimeType,
                                    codecid = dtoStream.codecid,
                                    frameRate = dtoStream.frameRate,
                                    width = dtoStream.width,
                                    height = dtoStream.height,
                                )
                            },
                        )
                    },
                    flac = dtoDash.flac?.let { dtoFlac ->
                        FlacInfo(
                            audio = dtoFlac.audio.map { dtoStream ->
                                DashStream(
                                    id = dtoStream.id,
                                    url = dtoStream.baseUrl,
                                    backupUrls = dtoStream.backupUrls,
                                    bandwidth = dtoStream.bandwidth,
                                    codecs = dtoStream.codecs,
                                    mimeType = dtoStream.mimeType,
                                    codecid = dtoStream.codecid,
                                    frameRate = dtoStream.frameRate,
                                    width = dtoStream.width,
                                    height = dtoStream.height,
                                )
                            },
                        )
                    },
                )
            }

            val hlsResult = dto.hls?.let { dtoHls ->
                HlsResult(
                    duration = dtoHls.duration,
                    minBufferTime = dtoHls.min_buffer_time,
                    video = dtoHls.video.map { dtoStream ->
                        HlsStream(
                            id = dtoStream.id,
                            url = dtoStream.baseUrl,
                            backupUrls = dtoStream.backupUrls,
                            bandwidth = dtoStream.bandwidth,
                            codecs = dtoStream.codecs,
                            mimeType = dtoStream.mimeType,
                            codecid = dtoStream.codecid,
                            frameRate = dtoStream.frameRate,
                            width = dtoStream.width,
                            height = dtoStream.height,
                        )
                    },
                    audio = dtoHls.audio.map { dtoStream ->
                        HlsStream(
                            id = dtoStream.id,
                            url = dtoStream.baseUrl,
                            backupUrls = dtoStream.backupUrls,
                            bandwidth = dtoStream.bandwidth,
                            codecs = dtoStream.codecs,
                            mimeType = dtoStream.mimeType,
                            codecid = dtoStream.codecid,
                            frameRate = dtoStream.frameRate,
                            width = dtoStream.width,
                            height = dtoStream.height,
                        )
                    },
                )
            }

            val durlResult = dto.durl.map { dtoDurl ->
                DurlResult(
                    order = dtoDurl.order,
                    length = dtoDurl.length,
                    size = dtoDurl.size,
                    url = dtoDurl.fullUrl,
                    backupUrls = if (dtoDurl.backupUrl.isNotEmpty()) dtoDurl.backupUrl else dtoDurl.backup_url,
                )
            }

            return PlayurlResult(
                dash = dashResult,
                hls = hlsResult,
                durl = durlResult,
                durationMillis = dto.timelength * 1000,
                timelength = dto.timelength,
                format = dto.format,
            )
        }
    }
}

data class DashResult(
    val duration: Long = 0,
    val minBufferTime: Double = 0.0,
    val video: List<DashStream> = emptyList(),
    val audio: List<DashStream> = emptyList(),
    val dolby: DolbyInfo? = null,
    val flac: FlacInfo? = null,
) {
    /** 按带宽升序排序的视频流（用于清晰度切换 UI） */
    val videoSorted: List<DashStream>
        get() = video.sortedBy { it.bandwidth }
}

data class DashStream(
    val id: Int = 0,
    val url: String = "",
    val backupUrls: List<String> = emptyList(),
    val bandwidth: Long = 0,
    @SerialName("codecs") val codecs: String = "",
    val mimeType: String = "",
    val codecid: Int = 0,
    val frameRate: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val segmentBase: SegmentBase? = null,
) {
    val resolution: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else ""
    val qualityLabel: String
        get() = when {
            height >= 2160 -> "4K"
            height >= 1080 -> "1080P"
            height >= 720 -> "720P"
            height >= 480 -> "480P"
            else -> "360P"
        }
}

data class SegmentBase(
    val initialization: String = "",
    val indexRange: String = "",
)

data class DolbyInfo(
    val type: Int = 0,
    val audio: List<DashStream> = emptyList(),
)

data class FlacInfo(
    val audio: List<DashStream> = emptyList(),
)

data class HlsResult(
    val duration: Long = 0,
    val minBufferTime: Double = 0.0,
    val video: List<HlsStream> = emptyList(),
    val audio: List<HlsStream> = emptyList(),
)

data class HlsStream(
    val id: Int = 0,
    val url: String = "",
    val backupUrls: List<String> = emptyList(),
    val bandwidth: Long = 0,
    val codecs: String = "",
    val mimeType: String = "",
    val codecid: Int = 0,
    val frameRate: String = "",
    val width: Int = 0,
    val height: Int = 0,
) {
    val resolution: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else ""
}

data class DurlResult(
    val order: Int = 0,
    val length: Long = 0,
    val size: Long = 0,
    val url: String = "",
    val backupUrls: List<String> = emptyList(),
)
