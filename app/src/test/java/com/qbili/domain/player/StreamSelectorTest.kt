package com.qbili.domain.player

import com.qbili.domain.model.AudioQuality
import com.qbili.domain.model.DashResult
import com.qbili.domain.model.DashStream
import com.qbili.domain.model.DolbyInfo
import com.qbili.domain.model.FlacInfo
import com.qbili.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 选轨逻辑的回归测试。
 *
 * 重点锁两件事：
 * 1. 画质菜单只能出现**真实存在**的轨道——接口的 accept_quality 会虚报更高档位，
 *    照它渲染会让用户选了却播不出来；
 * 2. 期望画质/编码不可用时的降级方向必须确定，否则同一个视频在不同机型上
 *    会随机挑到软解 AV1，表现为「有些手机卡成幻灯片」。
 */
class StreamSelectorTest {

    private fun video(qn: Int, codecId: Int, height: Int = 0, bandwidth: Long = 0) = DashStream(
        id = qn,
        url = "https://cdn.example/v_${qn}_$codecId.m4s",
        codecid = codecId,
        height = height,
        width = height * 16 / 9,
        bandwidth = bandwidth,
    )

    private fun audio(id: Int, bandwidth: Long) = DashStream(
        id = id,
        url = "https://cdn.example/a_$id.m4s",
        bandwidth = bandwidth,
    )

    /** 未登录时的真实形态：accept_quality 报到 1080P+，实际只有 480P/360P 三编码 */
    private val guestDash = DashResult(
        video = listOf(
            video(32, 12, 480), video(32, 7, 480), video(32, 13, 480),
            video(16, 12, 360), video(16, 7, 360), video(16, 13, 360),
        ),
        audio = listOf(audio(30216, 43962), audio(30232, 102931), audio(30280, 203786)),
    )

    @Test
    fun `可选画质只反映真实轨道`() {
        val options = StreamSelector.availableQualities(guestDash)

        assertEquals(listOf(32, 16), options.map { it.qn })
        assertEquals(
            listOf(VideoCodec.AVC, VideoCodec.HEVC, VideoCodec.AV1),
            options.first().availableCodecs,
        )
    }

    @Test
    fun `期望 1080P 但只有 480P 时降到最高可用档而不是最低档`() {
        val stream = StreamSelector.select(guestDash, desiredQn = 80, desiredCodec = VideoCodec.AVC)

        assertEquals(32, stream?.quality)
        assertEquals("480P", stream?.qualityLabel)
    }

    @Test
    fun `期望画质低于所有可用档时取最低档`() {
        val onlyHigh = DashResult(video = listOf(video(80, 7, 1080)), audio = listOf(audio(30280, 1)))
        val stream = StreamSelector.select(onlyHigh, desiredQn = 16, desiredCodec = null)

        assertEquals(80, stream?.quality)
    }

    @Test
    fun `精确命中期望的画质与编码`() {
        val stream = StreamSelector.select(guestDash, desiredQn = 16, desiredCodec = VideoCodec.HEVC)

        assertEquals(16, stream?.quality)
        assertEquals(VideoCodec.HEVC, stream?.codec)
    }

    @Test
    fun `该画质缺少期望编码时按 AVC 优先降级`() {
        val noAvc = DashResult(
            video = listOf(video(32, 12, 480), video(32, 13, 480)),
            audio = listOf(audio(30280, 1)),
        )
        // 期望 AVC 不存在，回退顺序里 HEVC 先于 AV1
        val stream = StreamSelector.select(noAvc, desiredQn = 32, desiredCodec = VideoCodec.AVC)

        assertEquals(VideoCodec.HEVC, stream?.codec)
    }

    @Test
    fun `没有指定编码时默认选 AVC 而不是 AV1`() {
        val stream = StreamSelector.select(guestDash, desiredQn = 32, desiredCodec = null)

        assertEquals(VideoCodec.AVC, stream?.codec)
    }

    @Test
    fun `音轨取码率最高的一条`() {
        val stream = StreamSelector.select(guestDash, desiredQn = 32, desiredCodec = null)

        assertTrue(stream?.audioUrl?.endsWith("a_30280.m4s") == true)
    }

    @Test
    fun `没有视频轨时返回 null 而不是抛异常`() {
        assertNull(StreamSelector.select(DashResult(), desiredQn = 80, desiredCodec = null))
    }

    @Test
    fun `baseUrl 为空的轨道被忽略`() {
        val dirty = DashResult(
            video = listOf(DashStream(id = 80, url = "", codecid = 7), video(32, 7, 480)),
            audio = listOf(audio(30280, 1)),
        )
        val options = StreamSelector.availableQualities(dirty)

        assertEquals(listOf(32), options.map { it.qn })
        assertEquals(32, StreamSelector.select(dirty, 80, null)?.quality)
    }

    @Test
    fun `未收录的 qn 用分辨率高度作为档位名`() {
        val odd = DashResult(
            video = listOf(video(qn = 999, codecId = 7, height = 1440)),
            audio = emptyList(),
        )
        assertEquals("1440P", StreamSelector.availableQualities(odd).first().label)
    }

    @Test
    fun `没有音轨时仍可返回纯视频流`() {
        val videoOnly = DashResult(video = listOf(video(32, 7, 480)), audio = emptyList())
        val stream = StreamSelector.select(videoOnly, 32, null)

        assertEquals(32, stream?.quality)
        assertNull(stream?.audioUrl)
    }

    // ---------- 音质 ----------

    @Test
    fun `可选音质包含普通音轨`() {
        val list = StreamSelector.availableAudioQualities(guestDash)

        assertEquals(
            listOf(AudioQuality.LOW, AudioQuality.MEDIUM, AudioQuality.HIGH),
            list,
        )
    }

    /** 杜比与 Hi-Res 挂在 dash.dolby / dash.flac 下，只扫 dash.audio 会漏掉 */
    @Test
    fun `可选音质包含杜比与 Hi-Res`() {
        val rich = guestDash.copy(
            dolby = DolbyInfo(type = 2, audio = listOf(audio(30250, 400_000))),
            flac = FlacInfo(audio = listOf(audio(30251, 900_000))),
        )
        val list = StreamSelector.availableAudioQualities(rich)

        assertTrue("应包含杜比", list.contains(AudioQuality.DOLBY))
        assertTrue("应包含 Hi-Res", list.contains(AudioQuality.HI_RES))
    }

    @Test
    fun `指定音质时精确命中`() {
        val stream = StreamSelector.select(guestDash, 32, null, desiredAudioId = AudioQuality.LOW.id)

        assertEquals(AudioQuality.LOW.id, stream?.audioId)
        assertEquals("64K", stream?.audioLabel)
    }

    @Test
    fun `能选中杜比音轨`() {
        val rich = guestDash.copy(dolby = DolbyInfo(type = 2, audio = listOf(audio(30250, 400_000))))
        val stream = StreamSelector.select(rich, 32, null, desiredAudioId = AudioQuality.DOLBY.id)

        assertEquals(AudioQuality.DOLBY.id, stream?.audioId)
        assertEquals("杜比全景声", stream?.audioLabel)
    }

    /** 自动选择不应该悄悄挑上体积大、兼容性差的杜比/Hi-Res */
    @Test
    fun `不指定音质时不会自动选到杜比或 Hi-Res`() {
        val rich = guestDash.copy(
            dolby = DolbyInfo(type = 2, audio = listOf(audio(30250, 5_000_000))),
            flac = FlacInfo(audio = listOf(audio(30251, 9_000_000))),
        )
        val stream = StreamSelector.select(rich, 32, null)

        assertEquals(AudioQuality.HIGH.id, stream?.audioId)
    }

    @Test
    fun `指定的音质不存在时退回码率最高的普通音轨`() {
        val stream = StreamSelector.select(guestDash, 32, null, desiredAudioId = AudioQuality.HI_RES.id)

        assertEquals(AudioQuality.HIGH.id, stream?.audioId)
    }
}
