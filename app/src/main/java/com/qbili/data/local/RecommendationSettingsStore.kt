package com.qbili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.model.RecommendationSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.recommendationSettingsDataStore by preferencesDataStore(name = "recommendation_settings")

class RecommendationSettingsStore(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.recommendationSettingsDataStore)

    val source = dataStore.data.map { RecommendationSource.fromKey(it[SOURCE_KEY]) }.distinctUntilChanged()

    suspend fun setSource(source: RecommendationSource) {
        dataStore.edit { it[SOURCE_KEY] = source.key }
    }

    private companion object {
        val SOURCE_KEY = stringPreferencesKey("recommendation_source")
    }
}
