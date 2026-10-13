package com.qbili.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WatchHistoryTest {
    @Test
    fun `有历史时先正常播放并提供正确分P的非阻断续播入口`() {
        val pages = listOf(VideoPage(30, 1, "第一部分", 60), VideoPage(20, 2, "第二部分", 60))
        val start = videoPlaybackStart(pages, VideoResume(20, 1, 32_000))
        assertEquals(1, start.pageIndex)
        assertEquals(0L, start.positionMs)
        assertEquals(VideoResume(20, 2, 32_000), start.resumeOffer)
    }

    @Test
    fun `历史无效已看完或没有历史时不提供续播入口`() {
        val pages = listOf(VideoPage(30, 1, "第一部分", 60))
        assertEquals(VideoPlaybackStart(0, 0, null), videoPlaybackStart(pages, null))
        assertNull(videoPlaybackStart(pages, VideoResume(99, 1, 32_000)).resumeOffer)
        assertNull(videoPlaybackStart(pages, VideoResume(30, 1, 60_000)).resumeOffer)
        assertNull(videoPlaybackStart(pages, VideoResume(30, 1, -1)).resumeOffer)
        assertEquals(VideoPlaybackStart(0, 0, null), videoPlaybackStart(emptyList(), VideoResume(30, 1, 100)))
    }

    private fun progress(aid: Long = 1, mid: Long = 0, time: Long = 100) = VideoProgress(
        mid, aid, "BV1", 20, 2, "第二部分", "标题", "", "作者", 32_000, 60_000, time)

    @Test
    fun `同视频只保留最新分P且账号游客互相隔离`() {
        val first = progress()
        val updated = first.copy(cid = 30, page = 3, updatedAt = 200)
        val items = listOf(first).withVideoProgress(progress(mid = 42)).withVideoProgress(updated)
        assertEquals(2, items.size)
        assertEquals(updated, items.first())
        assertEquals(items, items.withVideoProgress(first))
    }

    @Test
    fun `历史容量有界且拒绝无效资源和负进度`() {
        val items = (1L..600).fold(emptyList<VideoProgress>()) { list, aid -> list.withVideoProgress(progress(aid, time = aid)) }
        assertEquals(500, items.size)
        assertEquals(600L, items.first().aid)
        assertEquals(items, items.withVideoProgress(progress().copy(cid = 0)))
        assertEquals(items, items.withVideoProgress(progress().copy(positionMs = -1)))
    }

    @Test
    fun `续播以CID匹配正确分P而非已改变的页码`() {
        val pages = listOf(VideoPage(30, 1, "第三部分", 60), VideoPage(20, 2, "第二部分", 60))
        assertEquals(1, VideoResume(20, 1, 32_000).pageIndex(pages))
        assertNull(VideoResume(99, 1, 32_000).pageIndex(pages))
        assertEquals(0, VideoResume(0, 1, 32_000).pageIndex(pages))
        assertEquals(32_000L, VideoResume(20, 2, 32_000).position(60))
        assertEquals(0L, VideoResume(20, 2, 60_000).position(60))
    }

    @Test
    fun `已看完记录保留历史但重新打开从头播放`() {
        val watched = progress().copy(completed = true, positionMs = 60_000)
        assertEquals(0L, watched.resumePositionMs)
        assertTrue(watched.toHistoryItem().completed)
        assertEquals(0L, watched.toHistoryItem().resume.positionMs)
        assertEquals("archive_1", watched.toHistoryItem().deleteKey)
    }

    @Test
    fun `日期分组遵循本地时区且跨年正确`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val today = LocalDate.of(2026, 1, 1)
        fun seconds(day: LocalDate) = day.atStartOfDay(zone).toEpochSecond()
        assertEquals("今天", historyDateLabel(seconds(today), today, zone))
        assertEquals("昨天", historyDateLabel(seconds(today.minusDays(1)), today, zone))
        assertEquals("2025-12-30", historyDateLabel(seconds(today.minusDays(2)), today, zone))
    }
}
