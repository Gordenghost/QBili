package com.qbili.domain.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageTimelineTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun message(key: Long, time: Long) = PrivateMessage(key, key, 42, "消息", time)

    @Test
    fun `消息按序号去重排序不受输入顺序影响`() {
        val messages = listOf(message(2, 200), message(1, 100), message(2, 200))
        val result = messageTimeline(messages, zone)
        assertEquals(listOf(1L, 2L), result.filterIsInstance<MessageTimelineItem.Message>().map { it.value.key })
        assertEquals(1, result.filterIsInstance<MessageTimelineItem.Time>().size)
        assertEquals(result.size, result.map { it.key }.distinct().size)
    }

    @Test
    fun `每五分钟或跨日分组而不是每条都显示相对时间`() {
        val midnight = LocalDate.of(2026, 10, 15).atStartOfDay(zone).toEpochSecond()
        val result = messageTimeline(listOf(message(1, midnight - 10), message(2, midnight + 10),
            message(3, midnight + 200), message(4, midnight + 500)), zone)
        assertEquals(listOf(1L, 2L, 4L), result.filterIsInstance<MessageTimelineItem.Time>().map { it.messageKey })
    }

    @Test
    fun `空列表和未知时间不生成无效时间标签`() {
        assertTrue(messageTimeline(emptyList(), zone).isEmpty())
        assertEquals(0, messageTimeline(listOf(message(1, 0)), zone).filterIsInstance<MessageTimelineItem.Time>().size)
        assertEquals("", messageTimeLabel(0))
    }

    @Test
    fun `时间标签遵循本地日期和时区跨年不混淆`() {
        val today = LocalDate.of(2026, 1, 1)
        fun seconds(day: LocalDate) = day.atTime(10, 5).atZone(zone).toEpochSecond()
        assertEquals("10:05", messageTimeLabel(seconds(today), today, zone))
        assertEquals("昨天 10:05", messageTimeLabel(seconds(today.minusDays(1)), today, zone))
        assertEquals("2025-12-30 10:05", messageTimeLabel(seconds(today.minusDays(2)), today, zone))
    }
}
