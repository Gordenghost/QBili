package com.qbili.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.model.SeasonProgress
import com.qbili.domain.model.SeasonProgressEntry
import com.qbili.domain.model.withProgress
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.seasonProgressDataStore by preferencesDataStore(name = "season_progress")

interface SeasonProgressStorage {
    suspend fun read(seasonId: Long): SeasonProgress?
    suspend fun save(seasonId: Long, progress: SeasonProgress)
}

class SeasonProgressStore(context: Context) : SeasonProgressStorage {
    private val dataStore = context.applicationContext.seasonProgressDataStore
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(seasonId: Long): SeasonProgress? {
        val prefs = dataStore.data.catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }.first()
        return decode(prefs[KEY]).firstOrNull { it.seasonId == seasonId }?.progress
    }

    override suspend fun save(seasonId: Long, progress: SeasonProgress) {
        if (seasonId <= 0 || progress.episodeId <= 0) return
        dataStore.edit { prefs ->
            val entries = decode(prefs[KEY])
            val updated = entries.withProgress(seasonId, progress)
            prefs[KEY] = json.encodeToString(updated)
        }
    }

    private fun decode(value: String?): List<SeasonProgressEntry> = if (value.isNullOrBlank()) emptyList() else {
        runCatching { json.decodeFromString<List<SeasonProgressEntry>>(value) }.getOrDefault(emptyList())
    }

    private companion object {
        val KEY = stringPreferencesKey("progress")
    }
}
