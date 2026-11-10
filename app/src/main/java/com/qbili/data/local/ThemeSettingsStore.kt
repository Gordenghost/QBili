package com.qbili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.model.ThemeMode
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.themeSettingsDataStore by preferencesDataStore(name = "theme_settings")

class ThemeSettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.themeSettingsDataStore)

    val mode = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }.map { ThemeMode.fromKey(it[MODE_KEY]) }.distinctUntilChanged()

    suspend fun setMode(mode: ThemeMode) {
        store.edit { it[MODE_KEY] = mode.key }
    }

    private companion object {
        val MODE_KEY = stringPreferencesKey("theme_mode")
    }
}
