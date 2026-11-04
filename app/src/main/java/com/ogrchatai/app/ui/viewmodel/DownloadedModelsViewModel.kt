package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.data.ml.InferenceEngine
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.model.ModelSettings
import com.ogrchatai.app.domain.repository.ModelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadedModelsUiState(
    val models: List<DownloadedModel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val modelToDelete: DownloadedModel? = null,
    val settingsModel: DownloadedModel? = null,
    val settingsForEdit: ModelSettings? = null
) {
    val totalSizeBytes: Long get() = models.sumOf { it.fileSize }
}

sealed interface DownloadedModelsAction {
    data class ToggleModel(val modelId: String, val enabled: Boolean) : DownloadedModelsAction
    data class RequestDelete(val model: DownloadedModel) : DownloadedModelsAction
    data object ConfirmDelete : DownloadedModelsAction
    data object DismissDelete : DownloadedModelsAction
    data class OpenSettings(val model: DownloadedModel) : DownloadedModelsAction
    data object DismissSettings : DownloadedModelsAction
    data class SaveSettings(val settings: ModelSettings) : DownloadedModelsAction
    data object ClearError : DownloadedModelsAction
}

@HiltViewModel
class DownloadedModelsViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadedModelsUiState())
    val uiState: StateFlow<DownloadedModelsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            modelRepository.getDownloadedModels().collect { models ->
                _uiState.update { it.copy(models = models, isLoading = false) }
            }
        }
    }

    fun onAction(action: DownloadedModelsAction) {
        when (action) {
            is DownloadedModelsAction.ToggleModel -> toggleModel(action.modelId, action.enabled)
            is DownloadedModelsAction.RequestDelete -> _uiState.update { it.copy(modelToDelete = action.model) }
            is DownloadedModelsAction.ConfirmDelete -> confirmDelete()
            is DownloadedModelsAction.DismissDelete -> _uiState.update { it.copy(modelToDelete = null) }
            is DownloadedModelsAction.OpenSettings -> openSettings(action.model)
            is DownloadedModelsAction.DismissSettings -> _uiState.update {
                it.copy(settingsModel = null, settingsForEdit = null)
            }
            is DownloadedModelsAction.SaveSettings -> saveSettings(action.settings)
            is DownloadedModelsAction.ClearError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun toggleModel(modelId: String, enabled: Boolean) {
        viewModelScope.launch {
            modelRepository.setModelEnabled(modelId, enabled)
        }
    }

    private fun confirmDelete() {
        val model = _uiState.value.modelToDelete ?: return
        viewModelScope.launch {
            // Never keep a deleted file mapped in the native runtime.
            inferenceEngine.unloadModel(model.id)
            val result = modelRepository.deleteModel(model.id)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(error = result.exceptionOrNull()?.message ?: "Failed to delete model")
                }
            }
            _uiState.update { it.copy(modelToDelete = null) }
        }
    }

    private fun openSettings(model: DownloadedModel) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    settingsModel = model,
                    settingsForEdit = modelRepository.getModelSettingsOnce(model.id)
                )
            }
        }
    }

    private fun saveSettings(settings: ModelSettings) {
        viewModelScope.launch {
            modelRepository.saveModelSettings(settings)
            _uiState.update { it.copy(settingsModel = null, settingsForEdit = null) }
        }
    }
}
