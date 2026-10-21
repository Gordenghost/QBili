package com.qbili.ui.screen.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.qbili.core.formatCount
import com.qbili.core.formatDuration
import com.qbili.domain.model.RankingCategory
import com.qbili.domain.model.RankingTab
import com.qbili.domain.model.VideoItem
import com.qbili.domain.model.WeeklyIssue
import com.qbili.domain.model.SeasonRankingCategory
import com.qbili.domain.model.SeasonRankingPeriod
import com.qbili.domain.model.filterWeeklyIssues
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.util.biliThumbnail
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(onVideoClick: (VideoItem) -> Unit, onAuthorClick: (Long) -> Unit, onSeasonClick: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: RankingViewModel = viewModel(factory = remember(container) { RankingViewModel.factory(container) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var chooseWeek by remember { mutableStateOf(false) }
    val showTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 3 } }

    LaunchedEffect(viewModel) {
        if (viewModel.uiState.value.page == 0 && !viewModel.uiState.value.loading) viewModel.refresh()
    }
    LaunchedEffect(state.tab, state.category, state.weeklyNumber, state.seasonCategory, state.seasonPeriod) {
        listState.scrollToItem(0)
    }
    LaunchedEffect(state.tab, state.videos.size, state.loading, state.hasMore, state.error) {
        if (state.tab == RankingTab.POPULAR && !state.loading && state.hasMore && state.error == null) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .distinctUntilChanged().collect { index ->
                    if (index >= listState.layoutInfo.totalItemsCount - 3) viewModel.loadMore()
                }
        }
    }
    if (chooseWeek && state.tab == RankingTab.WEEKLY) {
        WeeklyIssuePicker(state.issues, state.weeklyNumber, onDismiss = { chooseWeek = false }, onSelect = {
            chooseWeek = false
            viewModel.selectWeek(it)
        })
    }

    Scaffold(
        topBar = {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(title = { Text("热门") }, actions = {
                    TextButton(onClick = { scope.launch { listState.scrollToItem(0) }; viewModel.refresh() },
                        enabled = !state.loading) { Text("刷新") }
                })
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RankingTab.entries.forEach { tab ->
                        FilterChip(selected = state.tab == tab, onClick = { chooseWeek = false; viewModel.selectTab(tab) },
                            label = { Text(tab.label) })
                    }
                }
            }
        },
        floatingActionButton = {
            if (showTop) FloatingActionButton(onClick = { scope.launch { listState.animateScrollToItem(0) } },
                containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "回到顶部")
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = state.refreshing && state.hasContent, onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "controls") {
                    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                        if (state.tab == RankingTab.PGC) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeasonRankingCategory.entries.forEach { category ->
                                    FilterChip(selected = state.seasonCategory == category,
                                        onClick = { viewModel.selectSeasonCategory(category) }, label = { Text(category.label) })
                                }
                            }
                            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SeasonRankingPeriod.entries.forEach { period ->
                                    FilterChip(selected = state.seasonPeriod == period,
                                        onClick = { viewModel.selectSeasonPeriod(period) }, label = { Text(period.label) })
                                }
                            }
                        }
                        if (state.tab == RankingTab.RANKING) Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RankingCategory.entries.forEach { category ->
                                FilterChip(selected = state.category == category, onClick = { viewModel.selectCategory(category) },
                                    label = { Text(category.label) })
                            }
                        }
                        if (state.tab == RankingTab.WEEKLY) {
                            val issue = state.issues.firstOrNull { it.number == state.weeklyNumber }
                            OutlinedButton(onClick = { chooseWeek = true }, enabled = state.issues.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                                Text(issue?.name ?: "选择往期期数", maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val heading = state.title.ifBlank {
                            when (state.tab) {
                                RankingTab.RANKING -> "${state.category.label}投稿排行榜"
                                RankingTab.PGC -> "${state.seasonCategory.label} · ${state.seasonPeriod.label}榜"
                                else -> state.tab.label
                            }
                        }
                        Text(heading, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.titleSmall)
                        Text(state.description.ifBlank {
                            when (state.tab) {
                                RankingTab.POPULAR -> "B站综合热门 · 下滑自动加载更多"
                                RankingTab.WEEKLY -> "精选每周视频，可搜索期数、日期或主题"
                                RankingTab.PRECIOUS -> "B站精选经典视频"
                                RankingTab.RANKING -> "按稿件质量与近期数据综合排序；不是个人推荐"
                                RankingTab.PGC -> "平台正片榜；点击作品进入详情。评分、更新与权限以平台返回为准"
                            }
                        }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                itemsIndexed(state.videos, key = { _, item -> item.aid }) { index, item ->
                    RankingVideoRow(item, rank = (index + 1).takeIf { state.tab == RankingTab.RANKING },
                        onClick = { onVideoClick(item) }, onAuthorClick = { onAuthorClick(item.authorMid) })
                }
                items(state.seasons, key = { "season:${it.seasonId}" }) { season ->
                    SeasonRankingCard(season, onClick = { onSeasonClick(season.seasonId) })
                }
                if (state.loading && !(state.refreshing && state.hasContent)) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                if (state.error != null) item(key = "error") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error.orEmpty(), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = viewModel::retry, modifier = Modifier.fillMaxWidth()) { Text("重试") }
                    }
                } else if (!state.loading && state.page > 0) {
                    item(key = "footer") {
                        if (state.hasMore) OutlinedButton(onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text("加载更多") }
                        else Text(if (!state.hasContent) "暂无内容" else if (state.tab == RankingTab.PGC)
                            "已显示全部 ${state.seasons.size} 部作品" else "已显示全部 ${state.videos.size} 个视频",
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingVideoRow(item: VideoItem, rank: Int?, onClick: () -> Unit, onAuthorClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(0.42f).aspectRatio(16f / 10f).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)) {
                    AsyncImage(model = item.cover.biliThumbnail(320, 200), contentDescription = null,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (rank != null) Text("$rank", style = MaterialTheme.typography.labelLarge, color = Color.White,
                        modifier = Modifier.align(Alignment.TopStart).background(
                            if (rank <= 3) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                            RoundedCornerShape(bottomEnd = 6.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                    if (item.durationSeconds > 0) Text(formatDuration(item.durationSeconds),
                        style = MaterialTheme.typography.labelSmall, color = Color.White,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp)).padding(3.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(item.authorName.ifBlank { "UP 主" }, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(enabled = item.authorMid > 0, onClick = onAuthorClick).padding(vertical = 4.dp))
                    Text("${formatCount(item.viewCount)}播放 · ${formatCount(item.danmakuCount)}弹幕",
                        style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item.recommendReason?.takeIf { it.isNotBlank() }?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeeklyIssuePicker(issues: List<WeeklyIssue>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(issues, query) { filterWeeklyIssues(issues, query) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp)) {
            Text("选择每周必看", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("搜索期数、日期或主题") }, singleLine = true)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.number }) { issue ->
                    TextButton(onClick = { onSelect(issue.number) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (issue.number == selected) "✓ " else "") + issue.name,
                                color = if (issue.number == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (issue.subject.isNotBlank()) Text(issue.subject, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text("没有匹配的期数", modifier = Modifier.padding(16.dp)) }
            }
        }
    }
}
