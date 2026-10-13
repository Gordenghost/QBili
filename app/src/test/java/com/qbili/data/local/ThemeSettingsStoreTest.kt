package com.qbili.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.qbili.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class ThemeSettingsStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `读取主题发生IO错误时回退系统而不阻止主页面加载`() = runBlocking {
        val preferences = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("unavailable") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                error("此测试不写入设置")
        }
        assertEquals(ThemeMode.SYSTEM, ThemeSettingsStore(preferences).mode.first())
    }

    @Test
    fun `默认跟随系统且重建存储后保留清爽白选择`() = runBlocking {
        val file = temporaryFolder.newFile("theme.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = ThemeSettingsStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(firstJob + Dispatchers.IO), produceFile = { file }))
        try {
            assertEquals(ThemeMode.SYSTEM, firstStore.mode.first())
            firstStore.setMode(ThemeMode.LIGHT)
            assertEquals(ThemeMode.LIGHT, firstStore.mode.first())
        } finally {
            firstJob.cancelAndJoin()
        }
        val secondJob = SupervisorJob()
        val secondStore = ThemeSettingsStore(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(secondJob + Dispatchers.IO), produceFile = { file }))
        try {
            assertEquals(ThemeMode.LIGHT, secondStore.mode.first())
            secondStore.setMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, secondStore.mode.first())
            secondStore.setMode(ThemeMode.SYSTEM)
            assertEquals(ThemeMode.SYSTEM, secondStore.mode.first())
        } finally {
            secondJob.cancelAndJoin()
        }
    }

    @Test
    fun `未知主题回退系统且更新不改动其他设置`() = runBlocking {
        val job = SupervisorJob()
        val preferences = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { temporaryFolder.newFile("unknown.preferences_pb") })
        try {
            preferences.edit {
                it[stringPreferencesKey("theme_mode")] = "future"
                it[stringPreferencesKey("other")] = "unchanged"
            }
            val store = ThemeSettingsStore(preferences)
            assertEquals(ThemeMode.SYSTEM, store.mode.first())
            store.setMode(ThemeMode.LIGHT)
            assertEquals("light", preferences.data.first()[stringPreferencesKey("theme_mode")])
            assertEquals("unchanged", preferences.data.first()[stringPreferencesKey("other")])
        } finally {
            job.cancelAndJoin()
        }
    }
}
