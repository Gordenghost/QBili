package com.qbili.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.player.coursePlaybackSpeeds
import com.qbili.domain.player.validCourseSpeed
import kotlinx.coroutines.flow.map

private val Context.coursePlaybackSettingsDataStore by preferencesDataStore(name = "course_playback_settings")

class CoursePlaybackSettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.coursePlaybackSettingsDataStore)
    private val speedKey = floatPreferencesKey("speed")
    val speeds = store.data.map { ((it.asMap()[speedKey]) as? Float).validCourseSpeed() }

    suspend fun setSpeed(speed: Float) {
        require(speed in coursePlaybackSpeeds) { "课程倍速无效" }
        store.edit { it[speedKey] = speed }
    }
}
