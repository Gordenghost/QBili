package com.qbili.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.qbili.core.friendlyMessage
import com.qbili.data.local.SearchHistoryStore
import com.qbili.data.paging.PageNumberPagingSource
import com.qbili.data.repository.GaiaRepository
import com.qbili.data.repository.SearchRepository
import com.qbili.di.AppContainer
import com.qbili.domain.model.ArticleItem
import com.qbili.domain.model.DurationFilter
import com.qbili.domain.model.GaiaChallenge
import com.qbili.domain.model.HotSearchItem
import com.qbili.domain.model.LiveRoomItem
import com.qbili.domain.model.PartitionFilter
import com.qbili.domain.model.SearchOrder
import com.qbili.domain.model.SearchType
import com.qbili.domain.model.SeasonItem
import com.qbili.domain.model.UserItem
import com.qbili.domain.model.VideoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val repository: SearchRepository,
    private val historyStore: SearchHistoryStore,
    private val gaiaRepository: GaiaRepository,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val submittedQuery: String = "",
        val activeType: SearchType = SearchType.VIDEO,
        val order: SearchOrder = SearchOrder.TOTAL_RANK,
        val duration: DurationFilter = DurationFilter.ALL,
        val partition: PartitionFilter = PartitionFilter.ALL,
        val suggestions: List<String> = emptyList(),
        val hotSearch: List<HotSearchItem> = emptyList(),
        val history: List<String> = emptyList(),
        val pendingGaia: GaiaChallenge? = null,
        val gaiaBusy: Boolean = false,
        val gaiaError: String? = null,
        val gaiaPassCount: Int = 0,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private data class VideoQuery(
        val keyword: String,
        val order: SearchOrder,
        val duration: DurationFilter,
        val partition: PartitionFilter,
    )

    private val videoQuery = MutableStateFlow<VideoQuery?>(null)
    private val userQuery = MutableStateFlow<String?>(null)
    private val liveQuery = MutableStateFlow<String?>(null)
    private val seasonQuery = MutableStateFlow<Pair<String, SearchType>?>(null)
    private val articleQuery = MutableStateFlow<String?>(null)

    private var activeGaia: GaiaChallenge? = null

    val videoResults: Flow<PagingData<VideoItem>> = videoQuery
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { q ->
            pagerFlow { page ->
                repository.searchVideos(q.keyword, q.order, q.duration, q.partition, page)
            }
        }
        .cachedIn(viewModelScope)

    val userResults: Flow<PagingData<UserItem>> = userQuery
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { keyword -> pagerFlow { page -> repository.searchUsers(keyword, page) } }
        .cachedIn(viewModelScope)

    val liveResults: Flow<PagingData<LiveRoomItem>> = liveQuery
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { keyword -> pagerFlow { page -> repository.searchLiveRooms(keyword, page) } }
        .cachedIn(viewModelScope)

    val seasonResults: Flow<PagingData<SeasonItem>> = seasonQuery
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { (keyword, type) ->
            pagerFlow { page -> repository.searchSeasons(keyword, type, page) }
        }
        .cachedIn(viewModelScope)

    val articleResults: Flow<PagingData<ArticleItem>> = articleQuery
        .filterNotNull()
        .distinctUntilChanged()
        .flatMapLatest { keyword -> pagerFlow { page -> repository.searchArticles(keyword, page) } }
        .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            historyStore.history.collect { list -> _uiState.update { it.copy(history = list) } }
        }
        viewModelScope.launch {
            runCatching { repository.hotSearch() }
                .onSuccess { hot -> _uiState.update { it.copy(hotSearch = hot) } }
        }
        viewModelScope.launch {
            _uiState
                .map { it.query }
                .distinctUntilChanged()
                .debounce(250)
                .collect { term ->
                    if (term.isBlank()) {
                        _uiState.update { it.copy(suggestions = emptyList()) }
                    } else {
                        runCatching { repository.suggest(term) }
                            .onSuccess { list -> _uiState.update { it.copy(suggestions = list) } }
                    }
                }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "", submittedQuery = "", suggestions = emptyList()) }
    }

    fun submit(keyword: String = _uiState.value.query) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(query = trimmed, submittedQuery = trimmed, suggestions = emptyList()) }
        viewModelScope.launch { historyStore.add(trimmed) }
        applyQueries()
    }

    fun onTypeChange(type: SearchType) {
        _uiState.update { it.copy(activeType = type) }
        applyQueries()
    }

    fun onOrderChange(order: SearchOrder) {
        _uiState.update { it.copy(order = order) }
        applyQueries()
    }

    fun onDurationChange(duration: DurationFilter) {
        _uiState.update { it.copy(duration = duration) }
        applyQueries()
    }

    fun onPartitionChange(partition: PartitionFilter) {
        _uiState.update { it.copy(partition = partition) }
        applyQueries()
    }

    fun removeHistory(keyword: String) {
        viewModelScope.launch { historyStore.remove(keyword) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyStore.clear() }
    }

    private fun applyQueries() {
        val state = _uiState.value
        val keyword = state.submittedQuery
        if (keyword.isEmpty()) return
        videoQuery.value = VideoQuery(keyword, state.order, state.duration, state.partition)
        userQuery.value = keyword
        liveQuery.value = keyword
        articleQuery.value = keyword
        if (state.activeType.isSeason) {
            seasonQuery.value = keyword to state.activeType
        }
    }

    private fun <T : Any> pagerFlow(
        loader: suspend (page: Int) -> com.qbili.domain.model.SearchPage<T>,
    ): Flow<PagingData<T>> = Pager(
        config = PagingConfig(
            pageSize = SearchRepository.PAGE_SIZE,
            initialLoadSize = SearchRepository.PAGE_SIZE,
            prefetchDistance = 5,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = { PageNumberPagingSource(loader = loader) },
    ).flow

    fun startGaiaVerification(voucher: String) {
        val state = _uiState.value
        if (state.gaiaBusy || state.pendingGaia != null) return

        viewModelScope.launch {
            _uiState.update { it.copy(gaiaBusy = true, gaiaError = null) }
            try {
                val challenge = gaiaRepository.register(voucher)
                _uiState.update { it.copy(gaiaBusy = false, pendingGaia = challenge) }
            } catch (e: Exception) {
                _uiState.update { it.copy(gaiaBusy = false, gaiaError = e.friendlyMessage()) }
            }
        }
    }

    fun onGaiaCaptchaSuccess(challenge: String, validate: String, seccode: String) {
        val pending = activeGaia
        if (pending == null) {
            _uiState.update {
                it.copy(
                    pendingGaia = null,
                    gaiaBusy = false,
                    gaiaError = "验证已通过，但本地状态已丢失，请重新点击开始验证",
                )
            }
            return
        }
        activeGaia = null
        _uiState.update { it.copy(pendingGaia = null, gaiaBusy = true, gaiaError = null) }

        viewModelScope.launch {
            try {
                val passed = gaiaRepository.validate(
                    token = pending.token,
                    challenge = challenge.ifBlank { pending.challenge },
                    validate = validate,
                    seccode = seccode,
                )
                _uiState.update {
                    if (passed) {
                        it.copy(gaiaBusy = false, gaiaPassCount = it.gaiaPassCount + 1)
                    } else {
                        it.copy(gaiaBusy = false, gaiaError = "验证未通过，请重试")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(gaiaBusy = false, gaiaError = e.friendlyMessage()) }
            }
        }
    }

    fun onGaiaCaptchaError(message: String) {
        _uiState.update { it.copy(pendingGaia = null, gaiaBusy = false, gaiaError = message) }
    }

    fun onGaiaDismiss() {
        _uiState.update { it.copy(pendingGaia = null, gaiaBusy = false) }
    }

    fun dismissGaiaError() {
        _uiState.update { it.copy(gaiaError = null) }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    repository = container.searchRepository,
                    historyStore = container.searchHistoryStore,
                    gaiaRepository = container.gaiaRepository,
                )
            }
        }
    }
}