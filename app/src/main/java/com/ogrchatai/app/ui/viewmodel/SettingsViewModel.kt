package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.domain.model.AppSettings
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.Language
import com.ogrchatai.app.domain.model.ThemeMode
import com.ogrchatai.app.domain.usecase.settings.GetSettingsUseCase
import com.ogrchatai.app.domain.usecase.settings.UpdateSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val language: Language = Language.ENGLISH,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val generationConfig: GenerationConfig = GenerationConfig(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface SettingsEvent {
    data class ShowError(val message: String) : SettingsEvent
    data object HistoryCleared : SettingsEvent
    data object SettingsSaved : SettingsEvent
}

sealed interface SettingsAction {
    data class UpdateLanguage(val language: Language) : SettingsAction
    data class UpdateTheme(val theme: ThemeMode) : SettingsAction
    data class UpdateTemperature(val temperature: Float) : SettingsAction
    data class UpdateTopP(val topP: Float) : SettingsAction
    data class UpdateMaxTokens(val maxTokens: Int) : SettingsAction
    data object ClearChatHistory : SettingsAction
    data object SaveSettings : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            getSettingsUseCase()
                .onEach { settings ->
                    _uiState.update {
                        it.copy(
                            language = settings.language,
                            theme = settings.theme,
                            generationConfig = settings.generationConfig,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Failed to load settings"
                        )
                    }
                    _events.emit(SettingsEvent.ShowError(throwable.message ?: "Failed to load settings"))
                }
                .launchIn(viewModelScope)
        }
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.UpdateLanguage -> updateLanguage(action.language)
            is SettingsAction.UpdateTheme -> updateTheme(action.theme)
            is SettingsAction.UpdateTemperature -> updateTemperature(action.temperature)
            is SettingsAction.UpdateTopP -> updateTopP(action.topP)
            is SettingsAction.UpdateMaxTokens -> updateMaxTokens(action.maxTokens)
            is SettingsAction.ClearChatHistory -> clearChatHistory()
            is SettingsAction.SaveSettings -> saveSettings()
        }
    }

    private fun updateLanguage(language: Language) {
        _uiState.update { it.copy(language = language) }
    }

    private fun updateTheme(theme: ThemeMode) {
        _uiState.update { it.copy(theme = theme) }
    }

    private fun updateTemperature(temperature: Float) {
        val clamped = temperature.coerceIn(0f, 2f)
        _uiState.update { state ->
            state.copy(
                generationConfig = state.generationConfig.copy(temperature = clamped)
            )
        }
    }

    private fun updateTopP(topP: Float) {
        val clamped = topP.coerceIn(0f, 1f)
        _uiState.update { state ->
            state.copy(
                generationConfig = state.generationConfig.copy(topP = clamped)
            )
        }
    }

    private fun updateMaxTokens(maxTokens: Int) {
        val clamped = maxTokens.coerceIn(1, 32768)
        _uiState.update { state ->
            state.copy(
                generationConfig = state.generationConfig.copy(maxTokens = clamped)
            )
        }
    }

    private fun clearChatHistory() {
        viewModelScope.launch {
            val state = _uiState.value
            val settings = AppSettings(
                language = state.language,
                theme = state.theme,
                generationConfig = state.generationConfig
            )
            updateSettingsUseCase(settings, clearHistory = true)
                .onSuccess {
                    _events.emit(SettingsEvent.HistoryCleared)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = throwable.message ?: "Failed to clear history")
                    }
                    _events.emit(SettingsEvent.ShowError(throwable.message ?: "Failed to clear history"))
                }
        }
    }

    private fun saveSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val state = _uiState.value
            val settings = AppSettings(
                language = state.language,
                theme = state.theme,
                generationConfig = state.generationConfig
            )
            updateSettingsUseCase(settings)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.emit(SettingsEvent.SettingsSaved)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = throwable.message ?: "Failed to save settings"
                        )
                    }
                    _events.emit(SettingsEvent.ShowError(throwable.message ?: "Failed to save settings"))
                }
        }
    }
}
