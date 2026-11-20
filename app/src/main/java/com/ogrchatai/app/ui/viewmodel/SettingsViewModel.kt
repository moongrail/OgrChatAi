package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.data.tts.NeuralVoiceRepository
import com.ogrchatai.app.domain.model.AppSettings
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.Language
import com.ogrchatai.app.domain.model.NeuralVoiceStatus
import com.ogrchatai.app.domain.model.ThemeMode
import com.ogrchatai.app.domain.model.TtsEngine
import com.ogrchatai.app.domain.model.TtsVoice
import com.ogrchatai.app.domain.model.VoiceGender
import com.ogrchatai.app.domain.usecase.settings.GetSettingsUseCase
import com.ogrchatai.app.domain.usecase.settings.UpdateSettingsUseCase
import com.ogrchatai.app.util.TextToSpeechManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
import java.util.Locale
import javax.inject.Inject

data class SettingsUiState(
    val language: Language = Language.ENGLISH,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val generationConfig: GenerationConfig = GenerationConfig(),
    val voiceGender: VoiceGender = VoiceGender.FEMALE,
    val ttsEngine: TtsEngine = TtsEngine.AUTO,
    val ttsVoice: TtsVoice = TtsVoice.F1,
    val neuralVoice: NeuralVoiceStatus = NeuralVoiceStatus(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface SettingsEvent {
    data class ShowError(val message: String) : SettingsEvent
    data class ShowMessage(val message: String) : SettingsEvent
    data object HistoryCleared : SettingsEvent
    data object SettingsSaved : SettingsEvent
}

sealed interface SettingsAction {
    data class UpdateLanguage(val language: Language) : SettingsAction
    data class UpdateTheme(val theme: ThemeMode) : SettingsAction
    data class UpdateTemperature(val temperature: Float) : SettingsAction
    data class UpdateTopP(val topP: Float) : SettingsAction
    data class UpdateMaxTokens(val maxTokens: Int) : SettingsAction
    data class UpdateVoiceGender(val gender: VoiceGender) : SettingsAction
    data class UpdateTtsEngine(val engine: TtsEngine) : SettingsAction
    data class UpdateTtsVoice(val voice: TtsVoice) : SettingsAction
    data object DownloadNeuralVoice : SettingsAction
    data object DeleteNeuralVoice : SettingsAction
    data object TestVoice : SettingsAction
    data object ClearChatHistory : SettingsAction
    data object SaveSettings : SettingsAction
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getSettingsUseCase: GetSettingsUseCase,
    private val updateSettingsUseCase: UpdateSettingsUseCase,
    private val textToSpeechManager: TextToSpeechManager,
    private val neuralVoiceRepository: NeuralVoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(neuralVoice = neuralVoiceRepository.status.value)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    private var downloadJob: Job? = null
    private var voiceSettingsNormalized = false

    init {
        observeSettings()
        observeNeuralVoice()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            getSettingsUseCase()
                .onEach { settings ->
                    val normalized = normalizeVoiceSettings(settings)
                    if (normalized != settings && !voiceSettingsNormalized) {
                        voiceSettingsNormalized = true
                        updateSettingsUseCase(normalized)
                    }
                    _uiState.update {
                        it.copy(
                            language = normalized.language,
                            theme = normalized.theme,
                            generationConfig = normalized.generationConfig,
                            voiceGender = normalized.voiceGender,
                            ttsEngine = normalized.ttsEngine,
                            ttsVoice = normalized.ttsVoice,
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

    private fun observeNeuralVoice() {
        neuralVoiceRepository.status
            .onEach { status ->
                _uiState.update { it.copy(neuralVoice = status) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.UpdateLanguage -> updateLanguage(action.language)
            is SettingsAction.UpdateTheme -> updateTheme(action.theme)
            is SettingsAction.UpdateTemperature -> updateTemperature(action.temperature)
            is SettingsAction.UpdateTopP -> updateTopP(action.topP)
            is SettingsAction.UpdateMaxTokens -> updateMaxTokens(action.maxTokens)
            is SettingsAction.UpdateVoiceGender -> updateVoiceGender(action.gender)
            is SettingsAction.UpdateTtsEngine -> updateTtsEngine(action.engine)
            is SettingsAction.UpdateTtsVoice -> updateTtsVoice(action.voice)
            SettingsAction.DownloadNeuralVoice -> downloadNeuralVoice()
            SettingsAction.DeleteNeuralVoice -> deleteNeuralVoice()
            SettingsAction.TestVoice -> testVoice()
            SettingsAction.ClearChatHistory -> clearChatHistory()
            SettingsAction.SaveSettings -> saveSettings()
        }
    }

    private fun updateLanguage(language: Language) {
        _uiState.update { it.copy(language = language) }
        persistCurrentSettings()
    }

    private fun updateTheme(theme: ThemeMode) {
        _uiState.update { it.copy(theme = theme) }
        persistCurrentSettings()
    }

    private fun updateTemperature(temperature: Float) {
        val clamped = temperature.coerceIn(0f, 2f)
        _uiState.update { state ->
            state.copy(
                generationConfig = state.generationConfig.copy(temperature = clamped)
            )
        }
        persistCurrentSettings()
    }

    private fun updateTopP(topP: Float) {
        val clamped = topP.coerceIn(0f, 1f)
        _uiState.update { state ->
            state.copy(
                generationConfig = state.generationConfig.copy(topP = clamped)
            )
        }
        persistCurrentSettings()
    }

    private fun updateMaxTokens(maxTokens: Int) {
        val clamped = maxTokens.coerceIn(1, 32768)
        _uiState.update { state ->
            state.copy(
                generationConfig = state.generationConfig.copy(maxTokens = clamped)
            )
        }
        persistCurrentSettings()
    }

    private fun updateVoiceGender(gender: VoiceGender) {
        _uiState.update {
            it.copy(
                voiceGender = gender,
                ttsVoice = if (it.neuralVoice.isInstalled) {
                    TtsVoice.forGender(gender)
                } else {
                    it.ttsVoice
                }
            )
        }
        persistCurrentSettings()
    }

    private fun updateTtsEngine(engine: TtsEngine) {
        _uiState.update { it.copy(ttsEngine = engine) }
        if (engine == TtsEngine.SYSTEM) {
            textToSpeechManager.shutdownNeural()
        }
        persistCurrentSettings()
    }

    private fun updateTtsVoice(voice: TtsVoice) {
        _uiState.update {
            it.copy(ttsVoice = voice, voiceGender = voice.gender)
        }
        persistCurrentSettings()
    }

    private fun downloadNeuralVoice() {
        if (downloadJob?.isActive == true || _uiState.value.neuralVoice.isDownloading) return
        neuralVoiceRepository.clearError()
        textToSpeechManager.shutdownNeural()
        downloadJob = viewModelScope.launch {
            neuralVoiceRepository.download()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            ttsEngine = TtsEngine.AUTO,
                            ttsVoice = TtsVoice.forGender(it.voiceGender),
                            neuralVoice = neuralVoiceRepository.status.value
                        )
                    }
                    persistCurrentSettings()
                    _events.emit(SettingsEvent.ShowMessage("Neural voices are ready"))
                }
                .onFailure { throwable ->
                    _events.emit(
                        SettingsEvent.ShowError(
                            throwable.message ?: "Failed to download neural voices"
                        )
                    )
                }
        }
    }

    private fun deleteNeuralVoice() {
        if (_uiState.value.neuralVoice.isDownloading) return
        viewModelScope.launch {
            textToSpeechManager.shutdownNeural()
            neuralVoiceRepository.deleteModel()
                .onSuccess {
                    _uiState.update { it.copy(ttsEngine = TtsEngine.SYSTEM) }
                    persistCurrentSettings()
                    _events.emit(SettingsEvent.ShowMessage("Neural voices were removed"))
                }
                .onFailure { throwable ->
                    _events.emit(
                        SettingsEvent.ShowError(
                            throwable.message ?: "Failed to remove neural voices"
                        )
                    )
                }
        }
    }

    private fun testVoice() {
        viewModelScope.launch {
            val state = _uiState.value
            textToSpeechManager.prepare(
                locale = Locale.getDefault(),
                gender = state.voiceGender,
                engine = state.ttsEngine,
                voice = state.ttsVoice
            )
                .onSuccess {
                    val example = if (textToSpeechManager.isUsingNeural) {
                        "Hi! This is ${state.ttsVoice.displayName}. I can sound lively, " +
                            "warm and expressive. <laugh> How does that sound?"
                    } else {
                        val gender = if (state.voiceGender == VoiceGender.FEMALE) "female" else "male"
                        "This is the selected $gender voice. Download the neural voice pack " +
                            "in Settings for a more natural sound."
                    }
                    textToSpeechManager.speakPreview(example)
                }
                .onFailure { throwable ->
                    _events.emit(
                        SettingsEvent.ShowError(
                            throwable.message ?: "Text-to-speech is not available"
                        )
                    )
                }
        }
    }

    /**
     * Older builds allowed choosing a fallback gender while the neural pack was
     * not installed. After installing the pack, the stored neural voice could
     * therefore disagree with the stored gender. The gender was the explicit
     * user choice in that flow, so bring the neural voice in line with it.
     */
    private fun normalizeVoiceSettings(settings: AppSettings): AppSettings {
        if (!neuralVoiceRepository.status.value.isInstalled) return settings
        if (settings.ttsVoice.gender == settings.voiceGender) return settings
        return settings.copy(ttsVoice = TtsVoice.forGender(settings.voiceGender))
    }

    private fun persistCurrentSettings() {
        val settings = currentSettings()
        viewModelScope.launch {
            updateSettingsUseCase(settings)
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = throwable.message ?: "Failed to save settings")
                    }
                }
        }
    }

    private fun currentSettings(): AppSettings {
        val state = _uiState.value
        return AppSettings(
            language = state.language,
            theme = state.theme,
            generationConfig = state.generationConfig,
            voiceGender = state.voiceGender,
            ttsEngine = state.ttsEngine,
            ttsVoice = state.ttsVoice
        )
    }

    private fun clearChatHistory() {
        viewModelScope.launch {
            updateSettingsUseCase(currentSettings(), clearHistory = true)
                .onSuccess {
                    textToSpeechManager.shutdownNeural()
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
            updateSettingsUseCase(currentSettings())
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

    override fun onCleared() {
        super.onCleared()
        downloadJob?.cancel()
        // A neural preview loads ~139 MB of model weights. Release them when
        // the user leaves Settings so they do not compete with the chat model.
        textToSpeechManager.shutdownNeural()
    }
}
