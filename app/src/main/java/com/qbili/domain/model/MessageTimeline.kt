package com.qbili.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface MessageTimelineItem {
    val key: String
    data class Message(val value: PrivateMessage) : MessageTimelineItem {
        override val key: String get() = "message:${value.key}"
    }
    data class Time(val messageKey: Long, val timestamp: Long) : MessageTimelineItem {
        override val key: String get() = "time:$messageKey"
    }
}

fun messageTimeline(messages: List<PrivateMessage>, zone: ZoneId = ZoneId.systemDefault()): List<MessageTimelineItem> {
    val sorted = messages.distinctBy { it.key }.sortedBy { it.seqNo }
    val result = mutableListOf<MessageTimelineItem>()
    sorted.forEachIndexed { index, message ->
        val previous = sorted.getOrNull(index - 1)
        if (message.timestamp > 0 && (previous == null || previous.timestamp <= 0 ||
                message.timestamp - previous.timestamp >= 300 ||
                Instant.ofEpochSecond(message.timestamp).atZone(zone).toLocalDate() !=
                    Instant.ofEpochSecond(previous.timestamp).atZone(zone).toLocalDate())) {
            result += MessageTimelineItem.Time(message.key, message.timestamp)
        }
        result += MessageTimelineItem.Message(message)
    }
    return result
}

fun messageTimeLabel(timestamp: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    if (timestamp <= 0) return ""
    val dateTime = Instant.ofEpochSecond(timestamp).atZone(zone)
    val clock = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (dateTime.toLocalDate()) {
        today -> clock
        today.minusDays(1) -> "昨天 $clock"
        else -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    }
}
