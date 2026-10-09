package com.qbili.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
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
fun PushFilterManagementScreen(group: RecommendationFilterGroup, onBack: () -> Unit) {
    val store = LocalAppContainer.current.recommendationFilterStore
    val filters by store.filters.collectAsStateWithLifecycle(initialValue = RecommendationFilters())
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var input by rememberSaveable(group) { mutableStateOf("") }
    var search by rememberSaveable(group) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val keywords = group.keywords(filters)
    val visibleKeywords = remember(keywords, search) {
        keywords.filter { it.contains(search.trim(), ignoreCase = true) }.sorted()
    }

    fun submit() {
        val keyword = input.trim()
        if (keyword.isEmpty() || busy) return
        busy = true
        scope.launch {
            try {
                val added = when (group) {
                    RecommendationFilterGroup.TITLE -> store.addTitleKeyword(keyword)
                    RecommendationFilterGroup.TAG -> store.addTagKeyword(keyword)
                    RecommendationFilterGroup.CHANNEL -> store.addChannel(keyword)
                }
                if (added) input = "" else snackbar.showSnackbar("该关键词已添加")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                snackbar.showSnackbar("保存失败：${error.friendlyMessage()}")
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(group.title) }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text(group.description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = input, onValueChange = { input = it },
                        label = { Text(if (group == RecommendationFilterGroup.CHANNEL) "输入频道名称" else "输入关键词") },
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = ::submit, enabled = input.isNotBlank() && !busy) { Text("添加") }
                }
            }
            item {
                OutlinedTextField(value = search, onValueChange = { search = it },
                    label = { Text("搜索已添加的屏蔽词") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item { Text("共 ${keywords.size} 条", style = MaterialTheme.typography.bodySmall) }
            if (visibleKeywords.isEmpty()) {
                item { Text(if (keywords.isEmpty()) "尚未添加屏蔽词" else "没有匹配的屏蔽词",
                    modifier = Modifier.padding(vertical = 16.dp)) }
            }
            items(visibleKeywords, key = { it }) { keyword ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(keyword, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    IconButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            try {
                                when (group) {
                                    RecommendationFilterGroup.TITLE -> store.removeTitleKeyword(keyword)
                                    RecommendationFilterGroup.TAG -> store.removeTagKeyword(keyword)
                                    RecommendationFilterGroup.CHANNEL -> store.removeChannel(keyword)
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                snackbar.showSnackbar("删除失败：${error.friendlyMessage()}")
                            } finally {
                                busy = false
                            }
                        }
                    }) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除 $keyword") }
                }
                HorizontalDivider()
            }
        }
    }
}
