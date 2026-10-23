package com.qbili.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qbili.domain.model.CourseProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CourseProgressStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private val progress = CourseProgress(42, 61, 790, 123, "课程", "", "讲师", "课时", 30_000, 80_000, 100)

    @Test fun `persists separate account and anonymous entries across restart`() = runBlocking {
        val file = folder.newFile("course.preferences_pb")
        val firstJob = SupervisorJob()
        try {
            val store = CourseProgressStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
            store.save(progress)
            store.save(progress.copy(accountMid = 0))
            store.save(progress.copy(accountMid = 43))
            store.delete(43, 61)
            assertEquals(setOf(0L, 42L), store.entries.first().map { it.accountMid }.toSet())
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val store = CourseProgressStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(progress, store.entries.first().first { it.accountMid == 42L })
        } finally { secondJob.cancelAndJoin() }
    }

    @Test fun `delete and clear barriers reject delayed saves without deleting other accounts`() = runBlocking {
        val job = SupervisorJob()
        try {
            val store = CourseProgressStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
                produceFile = { folder.newFile("barrier.preferences_pb") }), clock = { 200 })
            store.save(progress)
            store.save(progress.copy(accountMid = 43))
            store.delete(42, 61)
            store.save(progress.copy(updatedAt = 200))
            assertEquals(1, store.entries.first().size)
            store.save(progress.copy(updatedAt = 201))
            assertEquals(2, store.entries.first().size)
            store.clear(42)
            store.save(progress.copy(seasonId = 62))
            assertFalse(store.entries.first().any { it.accountMid == 42L && it.seasonId == 62L })
            assertTrue(store.entries.first().any { it.accountMid == 43L })
            store.save(progress.copy(seasonId = 62, updatedAt = 202))
            assertTrue(store.entries.first().any { it.accountMid == 42L && it.seasonId == 62L })
        } finally { job.cancelAndJoin() }
    }

    @Test fun `corrupt entries recover and invalid and out of order writes cannot replace valid records`() = runBlocking {
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { folder.newFile("corrupt.preferences_pb") })
            data.edit { it[stringPreferencesKey("entries")] = "not json" }
            val store = CourseProgressStore(data)
            assertTrue(store.entries.first().isEmpty())
            store.save(progress.copy(cid = 0))
            assertTrue(store.entries.first().isEmpty())
            store.save(progress)
            store.save(progress.copy(updatedAt = 99, positionMs = 60_000))
            assertEquals(progress, store.entries.first().single())
        } finally { job.cancelAndJoin() }
    }
}
