package com.qbili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.model.CourseProgress
import com.qbili.domain.model.withCourseProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.courseProgressDataStore by preferencesDataStore(name = "course_progress")

interface CourseProgressStorage {
    val entries: Flow<List<CourseProgress>>
    suspend fun save(progress: CourseProgress)
    suspend fun delete(accountMid: Long, seasonId: Long)
    suspend fun clear(accountMid: Long)
}

class CourseProgressStore(private val store: DataStore<Preferences>, private val clock: () -> Long = System::currentTimeMillis) : CourseProgressStorage {
    constructor(context: Context) : this(context.applicationContext.courseProgressDataStore)
    private val json = Json { ignoreUnknownKeys = true }
    private val key = stringPreferencesKey("entries")
    private val removedKey = stringPreferencesKey("removed_at")
    override val entries = store.data.map { decode(it[key]) }

    override suspend fun save(progress: CourseProgress) {
        if (!progress.valid) return
        store.edit { prefs ->
            val removed = decodeRemoved(prefs[removedKey])
            val barrier = maxOf(removed[progress.key] ?: 0, removed["${progress.accountMid}:*"] ?: 0)
            if (progress.updatedAt > barrier) prefs[key] = json.encodeToString(decode(prefs[key]).withCourseProgress(progress))
        }
    }

    override suspend fun delete(accountMid: Long, seasonId: Long) {
        require(accountMid >= 0 && seasonId > 0)
        val now = clock()
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.accountMid == accountMid && it.seasonId == seasonId })
            prefs[removedKey] = json.encodeToString(decodeRemoved(prefs[removedKey]) + ("$accountMid:$seasonId" to now))
        }
    }

    override suspend fun clear(accountMid: Long) {
        require(accountMid >= 0)
        val now = clock()
        store.edit { prefs ->
            prefs[key] = json.encodeToString(decode(prefs[key]).filterNot { it.accountMid == accountMid })
            prefs[removedKey] = json.encodeToString(decodeRemoved(prefs[removedKey]).filterKeys { !it.startsWith("$accountMid:") } +
                ("$accountMid:*" to now))
        }
    }

    private fun decode(raw: String?): List<CourseProgress> = raw?.let {
        runCatching { json.decodeFromString<List<CourseProgress>>(it) }.getOrDefault(emptyList())
    }.orEmpty().filter { it.valid }.sortedByDescending { it.updatedAt }.distinctBy { it.key }.take(100)

    private fun decodeRemoved(raw: String?): Map<String, Long> = raw?.let {
        runCatching { json.decodeFromString<Map<String, Long>>(it) }.getOrDefault(emptyMap())
    }.orEmpty()
}
