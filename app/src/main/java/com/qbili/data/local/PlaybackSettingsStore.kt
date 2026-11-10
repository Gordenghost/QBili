package com.qbili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.player.VideoCompletionMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.playbackSettingsDataStore by preferencesDataStore(name = "playback_settings")

data class PlaybackSettings(
    val resumeEnabled: Boolean = true,
    val reportHistory: Boolean = false,
    val completionMode: VideoCompletionMode = VideoCompletionMode.STOP,
)

class PlaybackSettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.playbackSettingsDataStore)
    private val resumeKey = booleanPreferencesKey("resume_enabled")
    private val reportKey = booleanPreferencesKey("report_history")
    private val completionKey = stringPreferencesKey("video_completion_mode")
    val settings = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { PlaybackSettings(it[resumeKey] ?: true, it[reportKey] ?: false,
        VideoCompletionMode.fromStored(it[completionKey])) }

    suspend fun read() = settings.first()
    suspend fun setResume(enabled: Boolean) { store.edit { it[resumeKey] = enabled } }
    suspend fun setReport(enabled: Boolean) { store.edit { it[reportKey] = enabled } }
    suspend fun setCompletionMode(mode: VideoCompletionMode) { store.edit { it[completionKey] = mode.name } }
}
