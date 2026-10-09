package com.qbili.domain.player

import com.qbili.domain.model.AudioQuality
import com.qbili.domain.model.DashResult
import com.qbili.domain.model.DashStream
import com.qbili.domain.model.PlayableStream
import com.qbili.domain.model.QualityOption
import com.qbili.domain.model.VideoCodec
import com.qbili.domain.model.VideoQuality

/**
 * 从 playurl 返回的 DASH 轨道里挑出一路可播放的音视频组合。
 *
 * 为什么要单独一层：接口返回的 `accept_quality` 和 `dash.video` **不是一回事**。
 * 实测未登录时 accept_quality 会列出 [112, 80, 64, 32, 16]（到 1080P+），
 * 但 dash.video 里实际只有 qn 32/16 两档。照 accept_quality 渲染画质菜单，
 * 用户选了 1080P 却根本没有对应轨道——所以这里一律以真实轨道为准。
 *
 * 同一画质通常有 3 条编码轨道（AVC / HEVC / AV1），需要按偏好选，选不到要降级。
 */
object StreamSelector {

    /**
     * 编码回退顺序：AVC 硬解支持面最广，作为兜底最安全；
     * AV1 虽然码率最省，但中低端机型常常只能软解，放最后。
     */
    private val CODEC_FALLBACK = listOf(VideoCodec.AVC, VideoCodec.HEVC, VideoCodec.AV1)

    /**
     * @param desiredQn      期望画质，没有完全匹配时取「不超过它的最高档」
     * @param desiredCodec   期望编码，该画质下没有时按 [CODEC_FALLBACK] 降级
     * @return 选中的组合；[dash] 里没有任何视频轨时返回 null
     */
    fun select(
        dash: DashResult,
        desiredQn: Int,
        desiredCodec: VideoCodec?,
        desiredAudioId: Int = 0,
    ): PlayableStream? {
        val videoTracks = dash.video.filter { it.url.isNotBlank() }
        if (videoTracks.isEmpty()) return null

        val targetQn = resolveQn(videoTracks.map { it.id }.distinct(), desiredQn)
        val sameQuality = videoTracks.filter { it.id == targetQn }

        val video = pickByCodec(sameQuality, desiredCodec) ?: return null
        val audio = pickAudio(dash, desiredAudioId)

        return PlayableStream(
            videoUrl = video.url,
            videoBackupUrls = video.backupUrls.filter { it.isNotBlank() },
            audioUrl = audio?.url,
            audioBackupUrls = audio?.backupUrls?.filter { it.isNotBlank() }.orEmpty(),
            quality = video.id,
            qualityLabel = qualityLabel(video),
            codec = VideoCodec.from(video.codecid),
            width = video.width,
            height = video.height,
            frameRate = video.frameRate,
            audioId = audio?.id ?: 0,
            audioLabel = audio?.let { audioLabel(it.id) }.orEmpty(),
        )
    }

    /**
     * 真实可用的音质列表。
     *
     * 杜比与 Hi-Res 不在 dash.audio 数组里，而是分别挂在 dash.dolby / dash.flac 下，
     * 只看 dash.audio 会让这两档永远出不来。
     */
    fun availableAudioQualities(dash: DashResult): List<AudioQuality> =
        allAudioTracks(dash)
            .mapNotNull { AudioQuality.from(it.id) }
            .distinct()
            .sortedBy { it.id }

    /** 真实可选的画质列表（含每档实际存在的编码），供 UI 渲染菜单 */
    fun availableQualities(dash: DashResult): List<QualityOption> =
        dash.video
            .filter { it.url.isNotBlank() }
            .groupBy { it.id }
            .map { (qn, tracks) ->
                QualityOption(
                    qn = qn,
                    label = qualityLabel(tracks.first()),
                    availableCodecs = tracks.mapNotNull { VideoCodec.from(it.codecid) }
                        .distinct()
                        .sortedBy { codec -> CODEC_FALLBACK.indexOf(codec) },
                )
            }
            .sortedByDescending { it.qn }

    /**
     * 期望画质不可用时的降级：优先取不超过期望的最高档（宁可清晰度低一点，
     * 也不要莫名跳到更高档去吃流量），全都超过时退到最低档。
     */
    private fun resolveQn(availableQns: List<Int>, desiredQn: Int): Int {
        availableQns.firstOrNull { it == desiredQn }?.let { return it }
        return availableQns.filter { it <= desiredQn }.maxOrNull()
            ?: availableQns.min()
    }

    private fun pickByCodec(tracks: List<DashStream>, desired: VideoCodec?): DashStream? {
        if (tracks.isEmpty()) return null
        desired?.let { wanted ->
            tracks.firstOrNull { it.codecid == wanted.codecId }?.let { return it }
        }
        for (codec in CODEC_FALLBACK) {
            tracks.firstOrNull { it.codecid == codec.codecId }?.let { return it }
        }
        // 出现了未知 codecid，也总比不播好
        return tracks.first()
    }

    /**
     * 选音轨：优先精确匹配用户选的音质，否则取码率最高的一条。
     *
     * 自动选择时**不主动挑杜比/Hi-Res**——它们体积大得多，且部分设备解不了，
     * 让用户显式选择更合适。
     */
    private fun pickAudio(dash: DashResult, desiredAudioId: Int): DashStream? {
        val tracks = allAudioTracks(dash)
        if (tracks.isEmpty()) return null
        if (desiredAudioId != 0) {
            tracks.firstOrNull { it.id == desiredAudioId }?.let { return it }
        }
        return dash.audio.filter { it.url.isNotBlank() }.maxByOrNull { it.bandwidth }
            ?: tracks.maxByOrNull { it.bandwidth }
    }

    /** 普通音轨 + 杜比 + Hi-Res，全部来源汇总 */
    private fun allAudioTracks(dash: DashResult): List<DashStream> =
        (dash.audio + dash.dolby?.audio.orEmpty() + dash.flac?.audio.orEmpty())
            .filter { it.url.isNotBlank() }

    private fun audioLabel(id: Int): String =
        AudioQuality.from(id)?.label ?: "音质 $id"

    /** 优先用官方档位名称，遇到没收录的 qn 就退回按高度描述 */
    private fun qualityLabel(track: DashStream): String =
        VideoQuality.entries.firstOrNull { it.qn == track.id }?.label
            ?: track.height.takeIf { it > 0 }?.let { "${it}P" }
            ?: "画质 ${track.id}"
}
