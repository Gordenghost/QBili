package com.qbili.domain.model

/** 视频详情。播放与弹幕都以 [VideoPage.cid] 为索引。 */
data class VideoDetail(
    val aid: Long,
    val bvid: String,
    val title: String,
    val description: String,
    val cover: String,
    /** 秒 */
    val durationSeconds: Int,
    val pubDate: Long,
    val authorMid: Long,
    val authorName: String,
    val authorFace: String,
    val viewCount: Long,
    val danmakuCount: Long,
    val likeCount: Long,
    val coinCount: Long,
    val favoriteCount: Long,
    val replyCount: Long,
    val pages: List<VideoPage>,
) {
    val isMultiPage: Boolean get() = pages.size > 1
}

data class VideoPage(
    val cid: Long,
    val index: Int,
    val title: String,
    val durationSeconds: Int,
)

/** 视频编码。playurl 的 dash.video[].codecid 取这三个值。 */
enum class VideoCodec(val codecId: Int, val label: String) {
    AVC(7, "AVC / H.264"),
    HEVC(12, "HEVC / H.265"),
    AV1(13, "AV1"),
    ;

    companion object {
        fun from(codecId: Int): VideoCodec? = entries.firstOrNull { it.codecId == codecId }
    }
}

/**
 * 音质。对应 playurl 的 dash.audio[].id。
 *
 * 杜比与 Hi-Res 不在 dash.audio 里，而是单独放在 dash.dolby / dash.flac 下，
 * 取轨时要一并考虑（见 StreamSelector）。
 */
enum class AudioQuality(val id: Int, val label: String) {
    LOW(30216, "64K"),
    MEDIUM(30232, "132K"),
    HIGH(30280, "192K"),
    DOLBY(30250, "杜比全景声"),
    HI_RES(30251, "Hi-Res 无损"),
    ;

    companion object {
        fun from(id: Int): AudioQuality? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 一次可播放的选轨结果。
 *
 * B 站 DASH 的音视频是**分离流**，各自是一个完整的 fMP4 文件，
 * 播放器必须把两条轨道合起来（MergingMediaSource），
 * 不能像普通 DASH 那样喂一个 MPD 清单。
 */
data class PlayableStream(
    val videoUrl: String,
    val videoBackupUrls: List<String>,
    val audioUrl: String?,
    val audioBackupUrls: List<String>,
    val quality: Int,
    val qualityLabel: String,
    val codec: VideoCodec?,
    val width: Int,
    val height: Int,
    val frameRate: String,
    val audioId: Int = 0,
    val audioLabel: String = "",
)

/** 某个画质下实际可用的编码集合，用于渲染「不撒谎」的画质/编码菜单 */
data class QualityOption(
    val qn: Int,
    val label: String,
    val availableCodecs: List<VideoCodec>,
)
