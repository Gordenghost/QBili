package com.qbili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.model.VideoProgress
import com.qbili.domain.model.withVideoProgress
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.videoProgressDataStore by preferencesDataStore(name = "video_progress")

class VideoProgressStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.videoProgressDataStore)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("entries")
    private val removedKey = stringPreferencesKey("removed_at")
    val entries = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { decode(it[key]) }

    suspend fun read(accountMid: Long, aid: Long) = entries.first().firstOrNull {
        it.accountMid == accountMid && it.aid == aid
    }

    suspend fun save(progress: VideoProgress) {
        store.edit {
            val removed = decodeRemoved(it[removedKey])
            val barrier = maxOf(removed[progress.key] ?: 0, removed["${progress.accountMid}:*"] ?: 0)
            if (progress.updatedAt > barrier) it[key] = json.encodeToString(decode(it[key]).withVideoProgress(progress))
        }
    }

    suspend fun delete(accountMid: Long, aid: Long) {
        val now = System.currentTimeMillis()
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.accountMid == accountMid && it.aid == aid })
            prefs[removedKey] = json.encodeToString(decodeRemoved(prefs[removedKey]) + ("$accountMid:$aid" to now))
        }
    }

    suspend fun clear(accountMid: Long) {
        val now = System.currentTimeMillis()
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.accountMid == accountMid })
            prefs[removedKey] = json.encodeToString(decodeRemoved(prefs[removedKey]).filterKeys {
                !it.startsWith("$accountMid:")
            } + ("$accountMid:*" to now))
        }
    }

    private fun decodeRemoved(raw: String?): Map<String, Long> = raw?.let {
        runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrDefault(emptyMap())
    }.orEmpty()

    private fun decode(raw: String?): List<VideoProgress> = raw?.let {
        runCatching { json.decodeFromString<List<VideoProgress>>(it) }.getOrDefault(emptyList())
    }.orEmpty()
}
