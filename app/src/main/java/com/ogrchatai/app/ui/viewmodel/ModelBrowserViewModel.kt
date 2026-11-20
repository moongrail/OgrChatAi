package com.ogrchatai.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.data.download.ModelDownloadManager
import com.ogrchatai.app.domain.model.ModelDownloadState
import com.ogrchatai.app.domain.model.ModelFilter
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.domain.usecase.model.SearchModelsUseCase
import com.ogrchatai.app.util.DeviceMemory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ModelBrowserUiState(
    val searchResults: List<ModelInfo> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val filters: ModelFilter = ModelFilter(),
    val currentPage: Int = 1,
    val hasMore: Boolean = true,
    val downloadState: Map<String, ModelDownloadState> = emptyMap(),
    val downloadedModelIds: Set<String> = emptySet(),
    val deviceRamMb: Long = 0L
)

sealed interface ModelBrowserEvent {
    data class ShowError(val message: String) : ModelBrowserEvent
    data class ShowSnackbar(val message: String) : ModelBrowserEvent
    data class DownloadComplete(val modelId: String) : ModelBrowserEvent
    data class DownloadFailed(val modelId: String, val error: String) : ModelBrowserEvent
}

sealed interface ModelBrowserAction {
    data class UpdateSearchQuery(val query: String) : ModelBrowserAction
    data object Search : ModelBrowserAction
    data object LoadMore : ModelBrowserAction
    data class DownloadModel(val modelId: String) : ModelBrowserAction
    data class CancelDownload(val modelId: String) : ModelBrowserAction
    data object CancelAllDownloads : ModelBrowserAction
    data class UpdateFilter(val filter: ModelFilter) : ModelBrowserAction
    data object ClearFilters : ModelBrowserAction
}

@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchModelsUseCase: SearchModelsUseCase,
    private val downloadManager: ModelDownloadManager,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ModelBrowserEvent>()
    val events: SharedFlow<ModelBrowserEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    companion object {
        private const val DEBOUNCE_MS = 500L
    }

    init {
        loadDeviceRam()
        observeDownloadedModels()
        observeDownloads()
    }

    private fun loadDeviceRam() {
        _uiState.update { it.copy(deviceRamMb = DeviceMemory.totalRamMb(context)) }
    }

    private fun observeDownloadedModels() {
        viewModelScope.launch {
            modelRepository.getDownloadedModels().collect { models ->
                val downloadedIds = models.map { m -> m.id }.toSet()
                downloadManager.retainCompleted(downloadedIds)
                _uiState.update {
                    it.copy(downloadedModelIds = downloadedIds)
                }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadManager.downloads.collect { downloads ->
                _uiState.update { it.copy(downloadState = downloads) }
            }
        }
    }

    fun onAction(action: ModelBrowserAction) {
        when (action) {
            is ModelBrowserAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is ModelBrowserAction.Search -> search()
            is ModelBrowserAction.LoadMore -> loadMore()
            is ModelBrowserAction.DownloadModel -> downloadModel(action.modelId)
            is ModelBrowserAction.CancelDownload -> downloadManager.cancel(action.modelId)
            ModelBrowserAction.CancelAllDownloads -> downloadManager.cancelAll()
            is ModelBrowserAction.UpdateFilter -> updateFilter(action.filter)
            is ModelBrowserAction.ClearFilters -> clearFilters()
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(DEBOUNCE_MS)
                performSearch(
                    query = _uiState.value.searchQuery.trim(),
                    filters = _uiState.value.filters
                )
            }
        } else {
            loadMoreJob?.cancel()
            _uiState.update {
                it.copy(searchResults = emptyList(), isLoading = false, error = null)
            }
        }
    }

    private fun search() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            loadMoreJob?.cancel()
            _uiState.update { it.copy(searchResults = emptyList(), error = null) }
            return
        }

        searchJob = viewModelScope.launch {
            performSearch(query, state.filters)
        }
    }

    /**
     * Runs one search on the already-cancelled [searchJob]. Keeping the network
     * request inside the debounce job prevents stale responses from an older
     * query from overwriting the current result list.
     */
    private suspend fun performSearch(
        query: String,
        filters: ModelFilter
    ) {
        if (query.isBlank()) return
        loadMoreJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                currentPage = 1,
                hasMore = true
            )
        }

        searchModelsUseCase(
            query = query,
            page = 1,
            filters = filters
        )
            .onSuccess { result ->
                if (_uiState.value.searchQuery.trim() != query) return@onSuccess
                _uiState.update {
                    it.copy(
                        searchResults = result.models,
                        isLoading = false,
                        hasMore = result.hasMore,
                        currentPage = 1
                    )
                }
            }
            .onFailure { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                if (_uiState.value.searchQuery.trim() != query) return@onFailure
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Search failed"
                    )
                }
                _events.emit(ModelBrowserEvent.ShowError(throwable.message ?: "Search failed"))
            }
    }

    private fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        val query = state.searchQuery.trim()
        val nextPage = state.currentPage + 1
        loadMoreJob?.cancel()
        loadMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            searchModelsUseCase(
                query = query,
                page = nextPage,
                filters = state.filters
            )
                .onSuccess { result ->
                    if (_uiState.value.searchQuery.trim() != query) return@onSuccess
                    _uiState.update {
                        it.copy(
                            searchResults = it.searchResults + result.models,
                            isLoadingMore = false,
                            hasMore = result.hasMore,
                            currentPage = nextPage
                        )
                    }
                }
                .onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    if (_uiState.value.searchQuery.trim() != query) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = throwable.message ?: "Failed to load more"
                        )
                    }
                }
        }
    }

    private fun downloadModel(modelId: String) {
        val state = _uiState.value
        if (downloadManager.isDownloading(modelId)) return

        val model = state.searchResults.find { it.id == modelId }
        val estimatedSizeBytes = model?.estimatedDownloadSize ?: 0L

        downloadManager.start(
            modelId = modelId,
            modelName = model?.name ?: modelId.substringAfter('/'),
            estimatedSizeBytes = estimatedSizeBytes
        )
    }

    private fun updateFilter(filter: ModelFilter) {
        _uiState.update { it.copy(filters = filter) }
        search()
    }

    private fun clearFilters() {
        _uiState.update {
            it.copy(filters = ModelFilter())
        }
        search()
    }
}
