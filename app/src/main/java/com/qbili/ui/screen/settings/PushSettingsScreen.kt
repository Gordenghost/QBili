package com.qbili.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qbili.core.friendlyMessage
import com.qbili.domain.model.RecommendationFilterGroup
import com.qbili.domain.model.RecommendationFilters
import com.qbili.ui.LocalAppContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushSettingsScreen(onBack: () -> Unit, onManageKeywordsClick: (RecommendationFilterGroup) -> Unit) {
    val store = LocalAppContainer.current.recommendationFilterStore
    val filters by store.filters.collectAsStateWithLifecycle(initialValue = RecommendationFilters())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun remove(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                snackbar.showSnackbar("移除失败：${error.friendlyMessage()}")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("推送设置") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text("仅屏蔽首页推荐，不影响搜索及其他列表。点击下方入口管理对应屏蔽词。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp))
            }
            items(RecommendationFilterGroup.entries, key = { it.key }) { group ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onManageKeywordsClick(group) }
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium)
                        Text("已添加 ${group.keywords(filters).size} 条 · 点击管理",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
                }
                HorizontalDivider()
            }
            if (filters.hiddenVideos.isNotEmpty()) {
                item { Text("不再推荐的视频", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)) }
                items(filters.hiddenVideos.sorted(), key = { "hidden-$it" }) { key ->
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(key, modifier = Modifier.weight(1f))
                        IconButton(onClick = { remove { store.showVideo(key) } }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "恢复视频 $key")
                        }
                    }
                }
            }
            if (filters.blockedAuthors.isNotEmpty()) {
                item { Text("已拉黑 UP（UID）", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)) }
                items(filters.blockedAuthors.sorted(), key = { "author-$it" }) { mid ->
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(mid.toString(), modifier = Modifier.weight(1f))
                        IconButton(onClick = { remove { store.unblockAuthor(mid) } }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "移除本地屏蔽 $mid")
                        }
                    }
                }
                item { Text("这里只移除本机推荐屏蔽，不会取消 B 站账号的拉黑关系。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
