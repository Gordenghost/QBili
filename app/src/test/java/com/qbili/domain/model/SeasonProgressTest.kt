package com.qbili.domain.model

import org.junit.Assert.assertEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class SeasonProgressTest {
    private val episode = SeasonEpisode(1, 10, 100, "第1集", "", 120, "", 2, false)

    @Test
    fun `同一剧集恢复有效进度而其他剧集从头开始`() {
        val progress = SeasonProgress(1, 30_000, 120_000, 100)
        assertEquals(30_000L, progress.resumePosition(episode))
        assertEquals(0L, progress.resumePosition(episode.copy(id = 2)))
    }

    @Test
    fun `已看完和接近结尾的记录不会停留在结束画面`() {
        listOf(115_000L, 120_000L, 150_000L).forEach { position ->
            assertEquals(0L, SeasonProgress(1, position, 120_000, 100).resumePosition(episode))
        }
    }

    @Test
    fun `未知播放器时长时使用剧集时长而过短记录不续播`() {
        assertEquals(30_000L, SeasonProgress(1, 30_000, 0, 100).resumePosition(episode))
        assertEquals(0L, SeasonProgress(1, 117_000, 0, 100).resumePosition(episode))
        assertEquals(0L, SeasonProgress(1, -1, 120_000, 100).resumePosition(episode))
        assertEquals(0L, SeasonProgress(1, 4_999, 120_000, 100).resumePosition(episode))
    }

    @Test
    fun `剧集权限标记优先地区限制其次服务端徽标`() {
        assertEquals("当前地区可能无法播放", episode.copy(areaLimited = true, badge = "会员").accessHint)
        assertEquals("会员", episode.copy(badge = "会员", status = 13).accessHint)
        assertEquals("大会员", episode.copy(status = 13).accessHint)
        assertEquals("付费", episode.copy(status = 12).accessHint)
        assertEquals("", episode.accessHint)
    }

    @Test
    fun `仅保留最近100部且同一番剧不会重复`() {
        val entries = (1L..100L).map { id -> SeasonProgressEntry(id, SeasonProgress(id, 30_000, 120_000, id)) }
        val updated = entries.withProgress(101, SeasonProgress(101, 40_000, 120_000, 101))
        assertEquals(100, updated.size)
        assertEquals(101L, updated.first().seasonId)
        assertEquals(2L, updated.last().seasonId)
        val repeated = updated.withProgress(50, SeasonProgress(50, 60_000, 120_000, 102))
        assertEquals(100, repeated.size)
        assertEquals(1, repeated.count { it.seasonId == 50L })
        assertEquals(60_000L, repeated.first().progress.positionMillis)
    }

    @Test
    fun `异步保存旧进度不能覆盖新进度`() {
        val entries = listOf(SeasonProgressEntry(1, SeasonProgress(1, 60_000, 120_000, 200)))
        val updated = entries.withProgress(1, SeasonProgress(1, 10_000, 120_000, 100))
        assertEquals(entries, updated)
        assertEquals(entries, entries.withProgress(0, SeasonProgress(1, 10_000, 120_000, 300)))
        assertEquals(entries, entries.withProgress(1, SeasonProgress(0, 10_000, 120_000, 300)))
    }

    @Test
    fun `进度序列化可恢复剧集和毫秒位置`() {
        val entries = listOf(SeasonProgressEntry(3398, SeasonProgress(84776, 30_000, 1_630_418, 100)))
        val text = Json.encodeToString(entries)
        assertEquals(entries, Json.decodeFromString<List<SeasonProgressEntry>>(text))
    }
}
