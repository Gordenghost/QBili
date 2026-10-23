package com.qbili.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qbili.domain.player.VideoCompletionMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackCompletionSettingsTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `completion mode defaults manual and persists without altering history settings`() = runBlocking {
        val file = folder.newFile("mode.preferences_pb")
        val firstJob = SupervisorJob()
        try {
            val settings = PlaybackSettingsStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(PlaybackSettings(), settings.read())
            settings.setResume(false)
            settings.setReport(true)
            VideoCompletionMode.entries.forEach { mode ->
                settings.setCompletionMode(mode)
                assertEquals(PlaybackSettings(false, true, mode), settings.read())
            }
        } finally { firstJob.cancelAndJoin() }
        val secondJob = SupervisorJob()
        try {
            val settings = PlaybackSettingsStore(PreferenceDataStoreFactory.create(
                scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
            assertEquals(PlaybackSettings(false, true, VideoCompletionMode.NEXT_PAGE), settings.read())
        } finally { secondJob.cancelAndJoin() }
    }

    @Test
    fun `unknown saved mode falls back to manual without writing preferences`() = runBlocking {
        val file = folder.newFile("unknown.preferences_pb")
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
            val key = stringPreferencesKey("video_completion_mode")
            store.edit { it[key] = "future_mode" }
            assertEquals(VideoCompletionMode.STOP, PlaybackSettingsStore(store).read().completionMode)
        } finally { job.cancelAndJoin() }
    }
}
