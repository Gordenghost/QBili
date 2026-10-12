package com.qbili.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qbili.domain.model.VideoProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackHistoryStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `默认启用续播但关闭上传设置持久化`() = runBlocking {
        val file = folder.newFile("settings.preferences_pb")
        val firstJob = SupervisorJob()
        val settings = PlaybackSettingsStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
        try {
            assertTrue(settings.read().resumeEnabled)
            assertFalse(settings.read().reportHistory)
            settings.setResume(false)
            settings.setReport(true)
            assertEquals(PlaybackSettings(false, true), settings.read())
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val rebuilt = PlaybackSettingsStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(PlaybackSettings(false, true), rebuilt.read())
        } finally { secondJob.cancelAndJoin() }
    }

    @Test
    fun `删除清空和查询均按账号隔离且进度持久化`() = runBlocking {
        val file = folder.newFile("progress.preferences_pb")
        val firstJob = SupervisorJob()
        val store = VideoProgressStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
        val progress = VideoProgress(42, 1, "BV1", 20, 2, "分P", "标题", "", "作者", 30_000, 60_000, 100)
        try {
            store.save(progress)
            store.save(progress.copy(accountMid = 43))
            store.save(progress.copy(accountMid = 0))
            store.delete(43, 1)
            assertNull(store.read(43, 1))
            assertEquals(progress, store.read(42, 1))
            store.clear(0)
            assertEquals(1, store.entries.first().size)
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val rebuilt = VideoProgressStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(progress, rebuilt.read(42, 1))
        } finally { secondJob.cancelAndJoin() }
    }

    @Test
    fun `损坏的记录安全回退空列表并可重新保存`() = runBlocking {
        val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { folder.newFile("broken.preferences_pb") })
        try {
            preferences.edit { it[stringPreferencesKey("entries")] = "invalid json" }
            val store = VideoProgressStore(preferences)
            assertTrue(store.entries.first().isEmpty())
            store.save(VideoProgress(0, 1, "BV1", 20, 1, "", "标题", "", "", 100, 500, 10))
            assertEquals(1, store.entries.first().size)
        } finally { job.cancelAndJoin() }
    }

    @Test
    fun `删除清空后拒绝延迟旧保存但允许之后重新观看`() = runBlocking {
        val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { folder.newFile("delayed.preferences_pb") })
        try {
            val store = VideoProgressStore(preferences)
            val progress = VideoProgress(42, 1, "BV1", 20, 1, "", "标题", "", "", 100, 500, 10)
            store.save(progress)
            store.delete(42, 1)
            store.save(progress)
            assertNull(store.read(42, 1))
            store.save(progress.copy(updatedAt = System.currentTimeMillis() + 1000))
            assertEquals(1, store.entries.first().size)
            store.clear(42)
            store.save(progress)
            assertNull(store.read(42, 1))
            store.save(progress.copy(accountMid = 43))
            assertEquals(1, store.entries.first().size)
        } finally { job.cancelAndJoin() }
    }
}
