package com.qbili.domain.download

import org.junit.Assert.*
import org.junit.Test

class OfflineDownloadTest {
    @Test fun acceptsOnlyTrustedHttpsCdn() {
        assertTrue(trustedDownloadUrl("https://upos-sz-mirrorcos.bilivideo.com/a.m4s?token=abc"))
        assertTrue(trustedDownloadUrl("https://a.bilivideo.cn:443/a"))
        listOf("http://a.bilivideo.com/a", "https://a.bilivideo.com.evil.com/a", "https://user@a.bilivideo.com/a",
            "https://a.bilivideo.com:8443/a", "file:///a", "https://127.0.0.1/a", "https://bilibili.com/a").forEach {
            assertFalse(it, trustedDownloadUrl(it))
        }
    }
    @Test fun etagMustBeStrongAndSingleLine() {
        assertEquals("\"abc\"", strongDownloadEtag("\"abc\""))
        listOf(null, "", "W/\"abc\"", "abc", "\"abc\r\n\"").forEach { assertEquals("", strongDownloadEtag(it)) }
    }
    @Test fun fullResponseRestartsRatherThanAppends() {
        assertEquals(DownloadRange(false, 100), downloadRange(200, 50, 100, "\"old\"", 100, null, "\"new\""))
    }
    @Test fun verifiedPartialResponseAppends() {
        assertEquals(DownloadRange(true, 100), downloadRange(206, 50, 100, "\"same\"", 50, "bytes 50-99/100", "\"same\""))
    }
    @Test fun rejectsUnsafeRanges() {
        listOf("bytes 0-49/100", "bytes 50-98/100", "bytes 50-99/101", "bytes 50-100/100", "bytes 50-99/*", "invalid").forEach {
            assertThrows(IllegalArgumentException::class.java) { downloadRange(206, 50, 100, "\"same\"", 50, it, "\"same\"") }
        }
        assertThrows(IllegalArgumentException::class.java) { downloadRange(206, 50, 100, "\"same\"", 50, "bytes 50-99/100", "\"other\"") }
        assertThrows(IllegalArgumentException::class.java) { downloadRange(206, 50, 100, "", 50, "bytes 50-99/100", null) }
        assertThrows(IllegalArgumentException::class.java) { downloadRange(200, 0, 0, "", -1, null, null) }
        assertThrows(IllegalArgumentException::class.java) { downloadRange(416, 0, 0, "", 1, null, null) }
    }
    @Test fun identifiersAndSizesAreBounded() {
        val task = sampleDownload()
        assertEquals("1_2_3_80", task.id)
        assertTrue(task.valid)
        assertFalse(task.copy(aid = 0).valid)
        assertFalse(task.copy(accountMid = -1).valid)
        assertFalse(task.copy(video = DownloadTrack(bytes = 10, total = 9)).valid)
        assertEquals("1.0 MB", formatDownloadBytes(1024 * 1024))
    }
    @Test fun totalIsUnknownUntilBothTrackSizesAreKnown() {
        val task = sampleDownload().copy(video = DownloadTrack(10, 10, complete = true))
        assertEquals(0L, task.total)
        assertEquals(20L, task.copy(audio = DownloadTrack(5, 10)).total)
        assertFalse(task.copy(status = DownloadStatus.COMPLETE).valid)
    }
}

fun sampleDownload(account: Long = 1) = OfflineDownload(account, 2, 3, 80, "视频", "P1", "UP", 60, "1080P", 30280, 1)
