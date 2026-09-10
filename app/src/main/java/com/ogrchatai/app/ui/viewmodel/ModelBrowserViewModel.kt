package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.domain.model.ModelFilter
import com.ogrchatai.app.domain.usecase.model.DownloadModelUseCase
import com.ogrchatai.app.domain.usecase.model.SearchModelsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val downloadState: Map<String, DownloadState> = emptyMap()
)

data class DownloadState(
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
)

sealed interface ModelBrowserEvent {
    data class ShowError(val message: String) : ModelBrowserEvent
    data class DownloadComplete(val modelId: String) : ModelBrowserEvent
    data class DownloadFailed(val modelId: String, val error: String) : ModelBrowserEvent
}

sealed interface ModelBrowserAction {
    data class UpdateSearchQuery(val query: String) : ModelBrowserAction
    data object Search : ModelBrowserAction
    data object LoadMore : ModelBrowserAction
    data class DownloadModel(val modelId: String) : ModelBrowserAction
    data class UpdateFilter(val filter: ModelFilter) : ModelBrowserAction
    data object ClearFilters : ModelBrowserAction
}

@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    private val searchModelsUseCase: SearchModelsUseCase,
    private val downloadModelUseCase: DownloadModelUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ModelBrowserEvent>()
    val events: SharedFlow<ModelBrowserEvent> = _events.asSharedFlow()

    private val _downloadJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    fun onAction(action: ModelBrowserAction) {
        when (action) {
            is ModelBrowserAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is ModelBrowserAction.Search -> search()
            is ModelBrowserAction.LoadMore -> loadMore()
            is ModelBrowserAction.DownloadModel -> downloadModel(action.modelId)
            is ModelBrowserAction.UpdateFilter -> updateFilter(action.filter)
            is ModelBrowserAction.ClearFilters -> clearFilters()
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    private fun search() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
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
                filters = state.filters
            )
                .onSuccess { result ->
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
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Search failed"
                        )
                    }
                    _events.emit(ModelBrowserEvent.ShowError(throwable.message ?: "Search failed"))
                }
        }
    }

    private fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMore) return

        val nextPage = state.currentPage + 1

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }

            searchModelsUseCase(
                query = state.searchQuery,
                page = nextPage,
                filters = state.filters
            )
                .onSuccess { result ->
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
        if (state.downloadState[modelId]?.isDownloading == true) return

        _uiState.update { currentState ->
            currentState.copy(
                downloadState = currentState.downloadState + (modelId to DownloadState(isDownloading = true))
            )
        }

        val job = viewModelScope.launch {
            downloadModelUseCase(modelId)
                .collect { result ->
                    result.onSuccess { progress ->
                        if (progress >= 1f) {
                            _uiState.update { currentState ->
                                currentState.copy(
                                    downloadState = currentState.downloadState + (modelId to DownloadState(
                                        progress = 1f,
                                        isDownloading = false,
                                        isComplete = true
                                    ))
                                )
                            }
                            _events.emit(ModelBrowserEvent.DownloadComplete(modelId))
                        } else {
                            _uiState.update { currentState ->
                                currentState.copy(
                                    downloadState = currentState.downloadState + (modelId to DownloadState(
                                        progress = progress,
                                        isDownloading = true
                                    ))
                                )
                            }
                        }
                    }
                    result.onFailure { throwable ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                downloadState = currentState.downloadState + (modelId to DownloadState(
                                    isDownloading = false,
                                    error = throwable.message ?: "Download failed"
                                ))
                            )
                        }
                        _events.emit(ModelBrowserEvent.DownloadFailed(modelId, throwable.message ?: "Download failed"))
                    }
                }
        }
        _downloadJobs[modelId] = job
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

    override fun onCleared() {
        super.onCleared()
        _downloadJobs.values.forEach { it.cancel() }
        _downloadJobs.clear()
    }
}
