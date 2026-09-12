package com.ogrchatai.app.ui.viewmodel

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.domain.model.ModelFilter
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.domain.usecase.model.DownloadModelUseCase
import com.ogrchatai.app.domain.usecase.model.SearchModelsUseCase
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
import java.io.File
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
    val downloadState: Map<String, DownloadState> = emptyMap(),
    val downloadedModelIds: Set<String> = emptySet(),
    val deviceRamMb: Long = 0L
)

data class DownloadState(
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L
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
    data class UpdateFilter(val filter: ModelFilter) : ModelBrowserAction
    data object ClearFilters : ModelBrowserAction
}

@HiltViewModel
class ModelBrowserViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val searchModelsUseCase: SearchModelsUseCase,
    private val downloadModelUseCase: DownloadModelUseCase,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelBrowserUiState())
    val uiState: StateFlow<ModelBrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ModelBrowserEvent>()
    val events: SharedFlow<ModelBrowserEvent> = _events.asSharedFlow()

    private val _downloadJobs = mutableMapOf<String, Job>()
    private var searchJob: Job? = null

    companion object {
        private const val DEBOUNCE_MS = 500L
    }

    init {
        loadDeviceRam()
        observeDownloadedModels()
    }

    private fun loadDeviceRam() {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        _uiState.update { it.copy(deviceRamMb = totalRamMb) }
    }

    private fun observeDownloadedModels() {
        viewModelScope.launch {
            modelRepository.getDownloadedModels().collect { models ->
                _uiState.update {
                    it.copy(downloadedModelIds = models.map { m -> m.id }.toSet())
                }
            }
        }
    }

    fun onAction(action: ModelBrowserAction) {
        when (action) {
            is ModelBrowserAction.UpdateSearchQuery -> updateSearchQuery(action.query)
            is ModelBrowserAction.Search -> search()
            is ModelBrowserAction.LoadMore -> loadMore()
            is ModelBrowserAction.DownloadModel -> downloadModel(action.modelId)
            is ModelBrowserAction.CancelDownload -> cancelDownload(action.modelId)
            is ModelBrowserAction.UpdateFilter -> updateFilter(action.filter)
            is ModelBrowserAction.ClearFilters -> clearFilters()
        }
    }

    private fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length >= 2) {
            searchJob = viewModelScope.launch {
                delay(DEBOUNCE_MS)
                search()
            }
        }
    }

    private fun search() {
        val state = _uiState.value
        val query = state.searchQuery.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), error = null) }
            return
        }

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

        val estimatedSizeMb = state.searchResults.find { it.id == modelId }?.totalSize?.div(1024 * 1024) ?: 0L
        val availableRamMb = state.deviceRamMb * 0.75
        if (estimatedSizeMb > availableRamMb && estimatedSizeMb > 0) {
            viewModelScope.launch {
                _events.emit(ModelBrowserEvent.ShowSnackbar("Model (~${estimatedSizeMb}MB) may exceed available RAM (${state.deviceRamMb}MB)"))
            }
        }

        _uiState.update { currentState ->
            currentState.copy(
                downloadState = currentState.downloadState + (modelId to DownloadState(isDownloading = true))
            )
        }

        val startTime = System.currentTimeMillis()
        val job = viewModelScope.launch {
            downloadModelUseCase(modelId)
                .collect { result ->
                    result.onSuccess { progress ->
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0f
                        val speed = if (elapsed > 0) (progress * (estimatedSizeMb * 1024 * 1024) / elapsed).toLong() else 0L

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
                                        isDownloading = true,
                                        speedBytesPerSec = speed
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

    private fun cancelDownload(modelId: String) {
        _downloadJobs[modelId]?.cancel()
        _downloadJobs.remove(modelId)
        _uiState.update { currentState ->
            currentState.copy(
                downloadState = currentState.downloadState - modelId
            )
        }
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
