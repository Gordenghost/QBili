package com.qbili.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qbili.domain.model.RecommendationSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecommendationSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `默认网页端并且重建存储后保留App选择`() = runBlocking {
        val file = temporaryFolder.newFile("recommendation.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = RecommendationSettingsStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
        try {
            assertEquals(RecommendationSource.WEB, firstStore.source.first())
            firstStore.setSource(RecommendationSource.APP)
            assertEquals(RecommendationSource.APP, firstStore.source.first())
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        val secondStore = RecommendationSettingsStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
        try {
            assertEquals(RecommendationSource.APP, secondStore.source.first())
            secondStore.setSource(RecommendationSource.WEB)
            assertEquals(RecommendationSource.WEB, secondStore.source.first())
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test
    fun `未知来源兼容回退网页端且不改动其他设置`() = runBlocking {
        val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { temporaryFolder.newFile("unknown.preferences_pb") })
        try {
            preferences.edit {
                it[stringPreferencesKey("recommendation_source")] = "future"
                it[stringPreferencesKey("other")] = "unchanged"
            }
            val store = RecommendationSettingsStore(preferences)
            assertEquals(RecommendationSource.WEB, store.source.first())
            store.setSource(RecommendationSource.APP)
            assertEquals("unchanged", preferences.data.first()[stringPreferencesKey("other")])
        } finally {
            job.cancelAndJoin()
        }
    }
}
