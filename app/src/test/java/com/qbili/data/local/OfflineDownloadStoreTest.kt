package com.qbili.data.local

import com.qbili.domain.download.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OfflineDownloadStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun persistsRecordsAndPausesInterruptedTasks() {
        val root = temporary.newFolder()
        val store = OfflineDownloadStore(root)
        store.write(listOf(sampleDownload().copy(status = DownloadStatus.DOWNLOADING), sampleDownload(2)))
        val reloaded = OfflineDownloadStore(root).read()
        assertEquals(2, reloaded.size)
        assertTrue(reloaded.all { it.status == DownloadStatus.PAUSED })
        assertNotEquals(reloaded[0].id, reloaded[1].id)
    }
    @Test fun rejectsCorruptIndexWithoutOverwritingIt() {
        val root = temporary.newFolder()
        val index = File(root, "index.json").apply { writeText("not json") }
        assertThrows(Exception::class.java) { OfflineDownloadStore(root).read() }
        assertEquals("not json", index.readText())
    }
    @Test fun cannotTraverseOutsideCacheRoot() {
        val store = OfflineDownloadStore(temporary.newFolder())
        listOf("../other", "1/2/3", "file:///a", "1_2_3_-1").forEach {
            assertThrows(IllegalArgumentException::class.java) { store.media(it, false) }
        }
    }
    @Test fun completeRequiresBothFilesWithExactLengths() {
        val store = OfflineDownloadStore(temporary.newFolder())
        val task = sampleDownload().copy(status = DownloadStatus.COMPLETE,
            video = DownloadTrack(10, 10, complete = true), audio = DownloadTrack(4, 4, complete = true))
        store.media(task.id, false).writeBytes(ByteArray(10))
        store.media(task.id, true).writeBytes(ByteArray(4))
        assertEquals(10, store.playable(task).first.length())
        assertEquals(14, store.occupiedBytes(listOf(task)))
        assertThrows(IllegalStateException::class.java) { store.playable(task.copy(status = DownloadStatus.PAUSED)) }
        store.media(task.id, true).writeBytes(ByteArray(3))
        assertThrows(IllegalStateException::class.java) { store.playable(task) }
    }
    @Test fun deletingOneAccountLeavesOtherFilesIntact() {
        val store = OfflineDownloadStore(temporary.newFolder())
        val first = sampleDownload(1); val second = sampleDownload(2)
        store.media(first.id, false).writeBytes(ByteArray(10))
        store.media(second.id, false).writeBytes(ByteArray(20))
        store.delete(first)
        assertEquals(20, store.media(second.id, false).length())
    }
}
