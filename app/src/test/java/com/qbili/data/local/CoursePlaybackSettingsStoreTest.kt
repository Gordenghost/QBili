package com.qbili.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

class CoursePlaybackSettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun `default speed and selection persist across store restart`() = runBlocking {
        val file = folder.newFile("course_speed.preferences_pb")
        val firstJob = SupervisorJob()
        try {
            val store = CoursePlaybackSettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(1f, store.speeds.first())
            store.setSpeed(1.5f)
            assertEquals(1.5f, store.speeds.first())
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val store = CoursePlaybackSettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(1.5f, store.speeds.first())
        } finally { secondJob.cancelAndJoin() }
    }

    @Test fun `invalid persisted values and wrong preference type recover to one`() = runBlocking {
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { folder.newFile("invalid.preferences_pb") })
            val store = CoursePlaybackSettingsStore(data)
            for (speed in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f, 3f)) {
                data.edit { it[floatPreferencesKey("speed")] = speed }
                assertEquals(1f, store.speeds.first())
            }
            data.edit { it[stringPreferencesKey("speed")] = "wrong type" }
            assertEquals(1f, store.speeds.first())
        } finally { job.cancelAndJoin() }
    }

    @Test fun `invalid writes do not replace valid speed or other preferences`() = runBlocking {
        val job = SupervisorJob()
        try {
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { folder.newFile("write.preferences_pb") })
            val store = CoursePlaybackSettingsStore(data)
            data.edit { it[stringPreferencesKey("unrelated")] = "keep" }
            store.setSpeed(1.25f)
            for (speed in listOf(Float.NaN, Float.NEGATIVE_INFINITY, 0f, 3f)) {
                try { store.setSpeed(speed); fail("Invalid speed accepted") } catch (_: IllegalArgumentException) { }
                assertEquals(1.25f, store.speeds.first())
            }
            assertEquals("keep", data.data.first()[stringPreferencesKey("unrelated")])
        } finally { job.cancelAndJoin() }
    }

    @Test fun `course preference file does not change ordinary playback preference file`() = runBlocking {
        val job = SupervisorJob()
        try {
            val ordinary = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { folder.newFile("ordinary.preferences_pb") })
            val course = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { folder.newFile("course.preferences_pb") })
            ordinary.edit { it[floatPreferencesKey("speed")] = 1.25f }
            CoursePlaybackSettingsStore(course).setSpeed(2f)
            assertEquals(1.25f, ordinary.data.first()[floatPreferencesKey("speed")])
        } finally { job.cancelAndJoin() }
    }
}
