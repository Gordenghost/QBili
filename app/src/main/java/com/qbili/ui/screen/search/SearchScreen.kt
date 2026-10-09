package com.qbili.ui.screen.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.qbili.core.BiliRiskControlException
import com.qbili.domain.model.ArticleItem
import com.qbili.domain.model.DurationFilter
import com.qbili.domain.model.LiveRoomItem
import com.qbili.domain.model.PartitionFilter
import com.qbili.domain.model.SearchOrder
import com.qbili.domain.model.SearchType
import com.qbili.domain.model.SeasonItem
import com.qbili.domain.model.UserItem
import com.qbili.domain.model.VideoItem
import com.qbili.ui.LocalAppContainer
import com.qbili.ui.component.ArticleListItem
import com.qbili.ui.component.FullScreenError
import com.qbili.ui.component.FullScreenLoading
import com.qbili.ui.component.GeetestDialog
import com.qbili.ui.component.LiveRoomListItem
import com.qbili.ui.component.LoadStateFooter
import com.qbili.ui.component.SeasonListItem
import com.qbili.ui.component.UserListItem
import com.qbili.ui.component.VideoListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onUserClick: (UserItem) -> Unit,
    onLiveClick: (LiveRoomItem) -> Unit,
    onSeasonClick: (SeasonItem) -> Unit,
    onArticleClick: (ArticleItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: SearchViewModel = viewModel(factory = remember { SearchViewModel.factory(container) })
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(state.gaiaError) {
        state.gaiaError?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissGaiaError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 自定义顶栏不像 TopAppBar 那样自动消费 inset，
                    // 少了这一行搜索框会被状态栏压住
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("搜索视频、UP 主、直播间") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = viewModel::clearQuery) {
                                Icon(Icons.Filled.Close, contentDescription = "清空")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.submit()
                            keyboard?.hide()
                        },
                    ),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.submittedQuery.isEmpty()) {
                IdleContent(
                    state = state,
                    onKeywordClick = { keyword ->
                        viewModel.submit(keyword)
                        keyboard?.hide()
                    },
                    onRemoveHistory = viewModel::removeHistory,
                    onClearHistory = viewModel::clearHistory,
                )
            } else {
                ResultContent(
                    viewModel = viewModel,
                    state = state,
                    onVideoClick = onVideoClick,
                    onUserClick = onUserClick,
                    onLiveClick = onLiveClick,
                    onSeasonClick = onSeasonClick,
                    onArticleClick = onArticleClick,
                )
            }
        }
    }

    // 复用登录用的同一套极验组件来过风控
    state.pendingGaia?.let { challenge ->
        GeetestDialog(
            gt = challenge.gt,
            challenge = challenge.challenge,
            onSuccess = viewModel::onGaiaCaptchaSuccess,
            onError = viewModel::onGaiaCaptchaError,
            onDismiss = viewModel::onGaiaDismiss,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdleContent(
    state: SearchViewModel.UiState,
    onKeywordClick: (String) -> Unit,
    onRemoveHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    // 正在输入时优先展示联想词
    if (state.query.isNotBlank() && state.suggestions.isNotEmpty()) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.suggestions.size) { index ->
                val suggestion = state.suggestions[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeywordClick(suggestion) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(suggestion, style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (state.history.isNotEmpty()) {
            item {
                SectionHeader(
                    icon = Icons.Outlined.History,
                    title = "搜索历史",
                    action = { TextButton(onClick = onClearHistory) { Text("清空") } },
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.history.forEach { keyword ->
                        AssistChip(
                            onClick = { onKeywordClick(keyword) },
                            label = { Text(keyword) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "删除「$keyword」",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onRemoveHistory(keyword) },
                                )
                            },
                        )
                    }
                }
            }
        }

        if (state.hotSearch.isNotEmpty()) {
            item { SectionHeader(icon = Icons.Outlined.Whatshot, title = "热门搜索") }
            items(state.hotSearch.size) { index ->
                val hot = state.hotSearch[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onKeywordClick(hot.keyword) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (index < 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(28.dp),
                    )
                    Text(hot.showName, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
private fun ResultContent(
    viewModel: SearchViewModel,
    state: SearchViewModel.UiState,
    onVideoClick: (VideoItem) -> Unit,
    onUserClick: (UserItem) -> Unit,
    onLiveClick: (LiveRoomItem) -> Unit,
    onSeasonClick: (SeasonItem) -> Unit,
    onArticleClick: (ArticleItem) -> Unit,
) {
    val types = SearchType.entries

    Column(modifier = Modifier.fillMaxSize()) {
        // 六个 Tab 塞不进等宽的 TabRow，用可滚动版本
        ScrollableTabRow(
            selectedTabIndex = types.indexOf(state.activeType),
            edgePadding = 8.dp,
        ) {
            types.forEach { type ->
                Tab(
                    selected = state.activeType == type,
                    onClick = { viewModel.onTypeChange(type) },
                    text = { Text(type.label) },
                )
            }
        }

        if (state.activeType == SearchType.VIDEO) {
            VideoFilterRow(
                order = state.order,
                duration = state.duration,
                partition = state.partition,
                onOrderChange = viewModel::onOrderChange,
                onDurationChange = viewModel::onDurationChange,
                onPartitionChange = viewModel::onPartitionChange,
            )
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when (state.activeType) {
                SearchType.VIDEO -> {
                    val items = viewModel.videoResults.collectAsLazyPagingItems()
                    PagedList(items, state, viewModel) { index ->
                        items[index]?.let { video ->
                            VideoListItem(video, onClick = { onVideoClick(video) })
                        }
                    }
                }

                SearchType.BANGUMI, SearchType.MOVIE -> {
                    val items = viewModel.seasonResults.collectAsLazyPagingItems()
                    PagedList(items, state, viewModel) { index ->
                        items[index]?.let { season ->
                            SeasonListItem(season, onClick = { onSeasonClick(season) })
                        }
                    }
                }

                SearchType.USER -> {
                    val items = viewModel.userResults.collectAsLazyPagingItems()
                    PagedList(items, state, viewModel) { index ->
                        items[index]?.let { user ->
                            UserListItem(user, onClick = { onUserClick(user) })
                        }
                    }
                }

                SearchType.LIVE_ROOM -> {
                    val items = viewModel.liveResults.collectAsLazyPagingItems()
                    PagedList(items, state, viewModel) { index ->
                        items[index]?.let { room ->
                            LiveRoomListItem(room, onClick = { onLiveClick(room) })
                        }
                    }
                }

                SearchType.ARTICLE -> {
                    val items = viewModel.articleResults.collectAsLazyPagingItems()
                    PagedList(items, state, viewModel) { index ->
                        items[index]?.let { article ->
                            ArticleListItem(article, onClick = { onArticleClick(article) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分页列表的统一骨架。
 *
 * 刻意不给 items 设置 key：搜索结果跨页可能返回重复条目，
 * 重复 key 会让 Compose 直接抛 "Key was already used" 崩溃。
 */
@Composable
private fun <T : Any> PagedList(
    items: LazyPagingItems<T>,
    state: SearchViewModel.UiState,
    viewModel: SearchViewModel,
    itemContent: @Composable (index: Int) -> Unit,
) {
    // 风控验证通过后自动重发请求，不用让用户再手点一次重试
    LaunchedEffect(state.gaiaPassCount) {
        if (state.gaiaPassCount > 0) items.retry()
    }

    val refresh = items.loadState.refresh
    val riskControl = (refresh as? LoadState.Error)?.error as? BiliRiskControlException

    when {
        riskControl != null && items.itemCount == 0 -> RiskControlPane(
            verifying = state.gaiaBusy,
            onVerify = { viewModel.startGaiaVerification(riskControl.voucher) },
        )

        refresh is LoadState.Loading && items.itemCount == 0 -> FullScreenLoading()

        refresh is LoadState.Error && items.itemCount == 0 ->
            FullScreenError(refresh.error, onRetry = { items.retry() })

        refresh is LoadState.NotLoading && items.itemCount == 0 -> EmptyResult()

        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items.itemCount) { index -> itemContent(index) }
            item {
                val append = items.loadState.append
                LoadStateFooter(
                    isLoading = append is LoadState.Loading,
                    error = (append as? LoadState.Error)?.error,
                    onRetry = { items.retry() },
                )
            }
        }
    }
}

/**
 * 风控拦截的专用界面。
 *
 * 不能显示成「没有找到相关内容」——那会让用户一直换关键词，永远搜不出来。
 */
@Composable
private fun RiskControlPane(
    verifying: Boolean,
    onVerify: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.Shield,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text("需要完成安全验证", style = MaterialTheme.typography.titleMedium)
        Text(
            "B 站对这次搜索要求了人机验证，完成后即可看到结果。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onVerify, enabled = !verifying) {
            if (verifying) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("开始验证")
            }
        }
    }
}

@Composable
private fun EmptyResult() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("没有找到相关内容", style = MaterialTheme.typography.titleMedium)
        Text(
            "换个关键词或调整筛选条件试试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun VideoFilterRow(
    order: SearchOrder,
    duration: DurationFilter,
    partition: PartitionFilter,
    onOrderChange: (SearchOrder) -> Unit,
    onDurationChange: (DurationFilter) -> Unit,
    onPartitionChange: (PartitionFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDropdown(order.label, SearchOrder.entries, { it.label }, onOrderChange)
        FilterDropdown(duration.label, DurationFilter.entries, { it.label }, onDurationChange)
        FilterDropdown(partition.label, PartitionFilter.entries, { it.label }, onPartitionChange)
    }
}

@Composable
private fun <T> FilterDropdown(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
            trailingIcon = {
                Icon(
                    Icons.Outlined.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
