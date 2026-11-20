package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.data.ml.InferenceEngine
import com.ogrchatai.app.data.tts.NeuralVoiceRepository
import com.ogrchatai.app.domain.model.AppSettings
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.model.TtsEngine
import com.ogrchatai.app.domain.model.VoiceGender
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.domain.usecase.settings.GetSettingsUseCase
import com.ogrchatai.app.util.TextToSpeechManager
import com.ogrchatai.app.util.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

enum class VoiceCallPhase {
    MODEL_SELECTION,
    LOADING,
    READY,
    LISTENING,
    THINKING,
    SPEAKING
}

data class VoiceCallUiState(
    val models: List<DownloadedModel> = emptyList(),
    val selectedModelId: String = "",
    val phase: VoiceCallPhase = VoiceCallPhase.MODEL_SELECTION,
    val isCallActive: Boolean = false,
    val voiceLabel: String = "Android system voice",
    val status: String = "",
    val userTranscript: String = "",
    val partialTranscript: String = "",
    val assistantTranscript: String = "",
    val error: String? = null
) {
    val selectedModel: DownloadedModel?
        get() = models.firstOrNull { it.id == selectedModelId }
}

@HiltViewModel
class VoiceCallViewModel @Inject constructor(
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine,
    private val voiceInputManager: VoiceInputManager,
    private val textToSpeechManager: TextToSpeechManager,
    private val getSettingsUseCase: GetSettingsUseCase,
    private val neuralVoiceRepository: NeuralVoiceRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceCallUiState())
    val uiState: StateFlow<VoiceCallUiState> = _uiState.asStateFlow()

    private var appSettings = AppSettings()
    private var generationJob: Job? = null
    private var autoListenJob: Job? = null
    private var listenTimeoutJob: Job? = null
    private var voiceSessionActive = false

    private val turns = mutableListOf<CallTurn>()

    init {
        observeModels()
        observeSettings()
        observeNeuralVoice()
    }

    private fun observeModels() {
        modelRepository.getDownloadedModels()
            .onEach { models ->
                _uiState.update { state ->
                    val selected = when {
                        models.isEmpty() -> ""
                        models.any { it.id == state.selectedModelId } -> state.selectedModelId
                        else -> models.first().id
                    }
                    state.copy(models = models, selectedModelId = selected)
                }
            }
            .catch { throwable ->
                _uiState.update {
                    it.copy(error = throwable.message ?: "Failed to load models")
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeSettings() {
        getSettingsUseCase()
            .onEach { settings ->
                appSettings = settings
                _uiState.update {
                    it.copy(
                        voiceLabel = predictedVoiceLabel(
                            settings,
                            neuralVoiceRepository.status.value.isInstalled
                        )
                    )
                }
            }
            .catch { }
            .launchIn(viewModelScope)
    }

    private fun observeNeuralVoice() {
        neuralVoiceRepository.status
            .onEach { status ->
                _uiState.update {
                    it.copy(
                        voiceLabel = predictedVoiceLabel(appSettings, status.isInstalled)
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    private fun predictedVoiceLabel(settings: AppSettings, neuralInstalled: Boolean): String {
        return when {
            settings.ttsEngine == TtsEngine.SYSTEM ->
                "Android system · ${settings.voiceGender.label()}"
            neuralInstalled ->
                "Supertonic 3 · ${settings.ttsVoice.displayName}"
            else ->
                "Android system · ${settings.voiceGender.label()}"
        }
    }

    private fun VoiceGender.label(): String = when (this) {
        VoiceGender.FEMALE -> "Female"
        VoiceGender.MALE -> "Male"
    }

    fun selectModel(modelId: String) {
        if (_uiState.value.isCallActive) return
        _uiState.update { it.copy(selectedModelId = modelId, error = null) }
    }

    fun startCall() {
        val state = _uiState.value
        if (state.isCallActive || state.phase == VoiceCallPhase.LOADING) return
        val model = state.selectedModel ?: run {
            _uiState.update { it.copy(error = "Choose a downloaded model first") }
            return
        }

        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.LOADING,
                isCallActive = true,
                status = "Preparing voice...",
                error = null,
                userTranscript = "",
                partialTranscript = "",
                assistantTranscript = ""
            )
        }

        viewModelScope.launch {
            val ttsResult = textToSpeechManager.prepare(
                locale = Locale.getDefault(),
                gender = appSettings.voiceGender,
                engine = appSettings.ttsEngine,
                voice = appSettings.ttsVoice
            )
            if (ttsResult.isFailure) {
                failCall(ttsResult.exceptionOrNull()?.message ?: "Text-to-speech is unavailable")
                return@launch
            }

            val actualVoiceLabel = if (textToSpeechManager.isUsingNeural) {
                "Supertonic 3 · ${appSettings.ttsVoice.displayName}"
            } else {
                "Android system · ${appSettings.voiceGender.label()}"
            }
            _uiState.update {
                it.copy(
                    voiceLabel = actualVoiceLabel,
                    status = "Loading ${model.name}..."
                )
            }

            val modelPath = modelRepository.getModelPath(model.id)
            if (modelPath == null) {
                failCall("Model file is missing. Download it again.")
                return@launch
            }

            inferenceEngine.cancelGeneration()
            val loadResult = inferenceEngine.loadModel(model.id, modelPath)
            if (loadResult.isFailure) {
                failCall(loadResult.exceptionOrNull()?.message ?: "Failed to load model")
                return@launch
            }
            inferenceEngine.invalidateConversation("voice-call")
            turns.clear()

            _uiState.update {
                it.copy(
                    phase = VoiceCallPhase.READY,
                    status = "Model is ready. Tap the microphone to speak.",
                    error = null
                )
            }
            scheduleAutoListen()
        }
    }

    fun startListening() {
        val state = _uiState.value
        if (!state.isCallActive || state.phase == VoiceCallPhase.LOADING) return

        if (state.phase == VoiceCallPhase.LISTENING) {
            stopListening()
            return
        }

        // Barge-in: if the assistant is speaking, stop playback and generation.
        if (state.phase == VoiceCallPhase.SPEAKING) {
            cancelActiveGeneration()
            textToSpeechManager.stop()
        } else if (state.phase == VoiceCallPhase.THINKING) {
            return
        }

        autoListenJob?.cancel()
        listenTimeoutJob?.cancel()
        textToSpeechManager.stop()
        voiceInputManager.cancel()
        voiceSessionActive = true

        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.LISTENING,
                status = "Listening...",
                partialTranscript = "",
                error = null
            )
        }

        val started = voiceInputManager.start(
            languageTag = Locale.getDefault().toLanguageTag(),
            listener = object : VoiceInputManager.Listener {
                override fun onReadyForSpeech() {
                    if (!voiceSessionActive) return
                    _uiState.update { it.copy(status = "Listening...") }
                }

                override fun onPartialResult(text: String) {
                    if (!voiceSessionActive) return
                    _uiState.update { it.copy(partialTranscript = text) }
                }

                override fun onFinalResult(text: String) {
                    if (!voiceSessionActive) return
                    finishListening(text)
                }

                override fun onEndOfSpeech() {
                    if (!voiceSessionActive) return
                    _uiState.update { it.copy(status = "Processing speech...") }
                    scheduleListenTimeout()
                }

                override fun onNoSpeechDetected(message: String) {
                    if (!voiceSessionActive) return
                    val partial = _uiState.value.partialTranscript.trim()
                    if (partial.isNotEmpty()) {
                        finishListening(partial)
                    } else {
                        endListeningWithMessage(message)
                    }
                }

                override fun onError(message: String) {
                    if (!voiceSessionActive) return
                    endListeningWithMessage(message)
                }
            }
        )

        if (!started) {
            voiceSessionActive = false
        }
    }

    fun stopListening() {
        if (!voiceSessionActive || _uiState.value.phase != VoiceCallPhase.LISTENING) {
            return
        }
        _uiState.update { it.copy(status = "Processing speech...") }
        voiceInputManager.stop()
        scheduleListenTimeout()
    }

    fun endCall() {
        cancelActiveGeneration()
        autoListenJob?.cancel()
        autoListenJob = null
        listenTimeoutJob?.cancel()
        listenTimeoutJob = null
        voiceSessionActive = false
        voiceInputManager.cancel()
        textToSpeechManager.stop()
        inferenceEngine.invalidateConversation("voice-call")
        turns.clear()
        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.MODEL_SELECTION,
                isCallActive = false,
                voiceLabel = predictedVoiceLabel(
                    appSettings,
                    neuralVoiceRepository.status.value.isInstalled
                ),
                status = "",
                userTranscript = "",
                partialTranscript = "",
                assistantTranscript = "",
                error = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun finishListening(text: String) {
        val message = text.trim()
        if (message.isEmpty()) {
            endListeningWithMessage(null)
            return
        }

        voiceSessionActive = false
        listenTimeoutJob?.cancel()
        listenTimeoutJob = null
        voiceInputManager.release()

        turns.add(CallTurn(CallRole.USER, message))
        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.THINKING,
                userTranscript = message,
                partialTranscript = "",
                assistantTranscript = "",
                status = "Thinking...",
                error = null
            )
        }
        generateAnswer(message)
    }

    private fun endListeningWithMessage(message: String?) {
        voiceSessionActive = false
        listenTimeoutJob?.cancel()
        listenTimeoutJob = null
        voiceInputManager.cancel()
        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.READY,
                status = message ?: "Tap the microphone to speak.",
                error = null
            )
        }
        if (message == null) scheduleAutoListen()
    }

    private fun generateAnswer(userText: String) {
        cancelActiveGeneration()
        textToSpeechManager.startStreaming()
        textToSpeechManager.onAllDone = { onAssistantSpeechFinished() }

        generationJob = viewModelScope.launch {
            try {
                val contextSeed = buildContextSeed()
                val config = InferenceEngine.GenerationConfig(
                    maxTokens = appSettings.generationConfig.maxTokens.coerceAtMost(512),
                    temperature = appSettings.generationConfig.temperature,
                    topP = appSettings.generationConfig.topP,
                    topK = appSettings.generationConfig.topK,
                    repeatPenalty = appSettings.generationConfig.repeatPenalty,
                    contextSize = 4096
                )

                val response = StringBuilder()
                var lastTranscriptUpdateNanos = 0L
                inferenceEngine.generateTextStream(
                    modelId = _uiState.value.selectedModelId,
                    prompt = userText,
                    systemPrompt = CALL_SYSTEM_PROMPT,
                    config = config,
                    conversationKey = "voice-call",
                    contextSeed = contextSeed
                ).collect { token ->
                    if (token.isReasoning) return@collect
                    if (response.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                phase = VoiceCallPhase.SPEAKING,
                                status = "Speaking...",
                                error = null
                            )
                        }
                    }
                    response.append(token.text)
                    textToSpeechManager.appendStreamingToken(token.text)

                    // Updating the whole transcript for every model token forces
                    // Compose to rebuild the call screen dozens of times per
                    // second. Keep the spoken stream immediate but coalesce the
                    // visual transcript.
                    val now = System.nanoTime()
                    if (now - lastTranscriptUpdateNanos >= TRANSCRIPT_UPDATE_NANOS) {
                        lastTranscriptUpdateNanos = now
                        val snapshot = response.toString()
                        _uiState.update { it.copy(assistantTranscript = snapshot) }
                    }
                }

                val answer = response.toString().trim()
                if (answer.isBlank()) {
                    textToSpeechManager.stop()
                    failCall("The model returned an empty response")
                    return@launch
                }

                turns.add(CallTurn(CallRole.ASSISTANT, answer))
                _uiState.update { it.copy(assistantTranscript = answer) }
                textToSpeechManager.finishStreaming()

                if (!textToSpeechManager.isReady()) {
                    onAssistantSpeechFinished()
                }
            } catch (e: CancellationException) {
                textToSpeechManager.stop()
                throw e
            } catch (e: Exception) {
                textToSpeechManager.stop()
                failCall(e.message ?: "Failed to generate an answer")
            } finally {
                generationJob = null
            }
        }
    }

    private fun onAssistantSpeechFinished() {
        if (!_uiState.value.isCallActive) return
        if (_uiState.value.phase != VoiceCallPhase.SPEAKING) return
        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.READY,
                status = "Tap the microphone to speak.",
                partialTranscript = ""
            )
        }
        scheduleAutoListen()
    }

    private fun scheduleAutoListen() {
        if (!_uiState.value.isCallActive) return
        autoListenJob?.cancel()
        autoListenJob = viewModelScope.launch {
            delay(450)
            if (_uiState.value.isCallActive && _uiState.value.phase == VoiceCallPhase.READY) {
                startListening()
            }
        }
    }

    private fun scheduleListenTimeout() {
        listenTimeoutJob?.cancel()
        listenTimeoutJob = viewModelScope.launch {
            delay(12_000)
            if (voiceSessionActive) {
                endListeningWithMessage("Speech recognition timed out. Tap the microphone again.")
            }
        }
    }

    private fun cancelActiveGeneration() {
        if (generationJob?.isActive == true) {
            inferenceEngine.cancelGeneration()
        }
        generationJob?.cancel()
        generationJob = null
    }

    private fun failCall(message: String) {
        cancelActiveGeneration()
        autoListenJob?.cancel()
        autoListenJob = null
        voiceSessionActive = false
        voiceInputManager.cancel()
        textToSpeechManager.stop()
        _uiState.update {
            it.copy(
                phase = VoiceCallPhase.MODEL_SELECTION,
                isCallActive = false,
                voiceLabel = predictedVoiceLabel(
                    appSettings,
                    neuralVoiceRepository.status.value.isInstalled
                ),
                status = "",
                error = message
            )
        }
    }

    private fun buildContextSeed(): String {
        val priorTurns = turns.dropLast(1).takeLast(MAX_TURNS)
        if (priorTurns.isEmpty()) return ""
        return buildString {
            appendLine("[Earlier voice-call conversation - background only]")
            priorTurns.forEach { turn ->
                append(if (turn.role == CallRole.USER) "User: " else "Assistant: ")
                appendLine(turn.content)
            }
        }.trim()
    }

    override fun onCleared() {
        cancelActiveGeneration()
        super.onCleared()
        autoListenJob?.cancel()
        listenTimeoutJob?.cancel()
        voiceSessionActive = false
        voiceInputManager.cancel()
        textToSpeechManager.shutdown()
    }

    private data class CallTurn(val role: CallRole, val content: String)

    private enum class CallRole { USER, ASSISTANT }

    companion object {
        private const val MAX_TURNS = 20
        private const val TRANSCRIPT_UPDATE_NANOS = 80_000_000L
        private const val CALL_SYSTEM_PROMPT =
            "You are a friendly voice assistant in a live phone call. " +
                "Answer naturally and concisely in one to three short sentences, " +
                "in the same language as the user. Your reply will be spoken aloud, " +
                "so do not use markdown, lists, code blocks, emojis, URLs or special formatting."
    }
}
