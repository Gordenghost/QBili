package com.qbili.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.qbili.domain.model.RecommendationFilters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.recommendationFilterDataStore by preferencesDataStore(name = "recommendation_filters")

/** 规则存放在本机，即使未登录也能编辑，且更新后首页能直接收到新规则。 */
class RecommendationFilterStore(private val context: Context) {

    val filters: Flow<RecommendationFilters> = context.recommendationFilterDataStore.data.map { prefs ->
        RecommendationFilters(
            titleKeywords = prefs[TITLE_KEY].orEmpty(),
            tagKeywords = prefs[TAG_KEY].orEmpty(),
            hiddenVideos = prefs[HIDDEN_KEY].orEmpty(),
            blockedAuthors = prefs[AUTHOR_KEY].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            blockedChannels = prefs[CHANNEL_KEY].orEmpty(),
        )
    }

    suspend fun addTitleKeyword(keyword: String): Boolean = add(TITLE_KEY, keyword)

    suspend fun addTagKeyword(keyword: String): Boolean = add(TAG_KEY, keyword)

    suspend fun removeTitleKeyword(keyword: String) = remove(TITLE_KEY, keyword)

    suspend fun removeTagKeyword(keyword: String) = remove(TAG_KEY, keyword)

    suspend fun hideVideo(key: String) { add(HIDDEN_KEY, key) }

    suspend fun showVideo(key: String) = remove(HIDDEN_KEY, key)

    suspend fun blockAuthor(mid: Long) { if (mid > 0) add(AUTHOR_KEY, mid.toString()) }

    suspend fun unblockAuthor(mid: Long) = remove(AUTHOR_KEY, mid.toString())

    suspend fun blockChannel(channel: String) { add(CHANNEL_KEY, channel) }

    suspend fun addChannel(channel: String): Boolean = add(CHANNEL_KEY, channel)

    suspend fun removeChannel(channel: String) = remove(CHANNEL_KEY, channel)

    private suspend fun add(key: Preferences.Key<Set<String>>, input: String): Boolean {
        val keyword = input.trim()
        if (keyword.isEmpty()) return false
        var added = false
        context.recommendationFilterDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            if (current.none { it.equals(keyword, ignoreCase = true) }) {
                prefs[key] = current + keyword
                added = true
            }
        }
        return added
    }

    private suspend fun remove(key: Preferences.Key<Set<String>>, keyword: String) {
        context.recommendationFilterDataStore.edit { prefs ->
            prefs[key] = prefs[key].orEmpty() - keyword
        }
    }

    private companion object {
        val TITLE_KEY = stringSetPreferencesKey("title_keywords")
        val TAG_KEY = stringSetPreferencesKey("tag_keywords")
        val HIDDEN_KEY = stringSetPreferencesKey("hidden_videos")
        val AUTHOR_KEY = stringSetPreferencesKey("blocked_authors")
        val CHANNEL_KEY = stringSetPreferencesKey("blocked_channels")
    }
}
