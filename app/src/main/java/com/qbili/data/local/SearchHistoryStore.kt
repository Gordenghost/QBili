package com.qbili.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

/**
 * 搜索历史。用换行分隔的单个字符串存储而不是 stringSet，
 * 因为 Set 无序，而历史记录需要保持「最近使用在前」。
 */
class SearchHistoryStore(private val context: Context) {

    val history: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        prefs[KEY]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun add(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[KEY]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(trimmed) + current.filterNot { it == trimmed }).take(MAX_ENTRIES)
            prefs[KEY] = updated.joinToString("\n")
        }
    }

    suspend fun remove(keyword: String) {
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[KEY]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
            prefs[KEY] = current.filterNot { it == keyword }.joinToString("\n")
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { it.remove(KEY) }
    }

    private companion object {
        val KEY = stringPreferencesKey("keywords")
        const val MAX_ENTRIES = 20
    }
}
