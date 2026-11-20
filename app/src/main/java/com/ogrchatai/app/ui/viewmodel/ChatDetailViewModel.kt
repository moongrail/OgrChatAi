package com.ogrchatai.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.data.files.AttachmentProcessor
import com.ogrchatai.app.data.ml.InferenceEngine
import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.model.Message
import com.ogrchatai.app.domain.model.MessageRole
import com.ogrchatai.app.domain.model.SendMessageResponse
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.domain.usecase.chat.GetMessagesUseCase
import com.ogrchatai.app.domain.usecase.chat.SendMessageException
import com.ogrchatai.app.domain.usecase.chat.SendMessageUseCase
import com.ogrchatai.app.util.TokenEstimator
import com.ogrchatai.app.util.VoiceInputManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import java.util.Locale
import javax.inject.Inject

data class ChatDetailUiState(
    val chatId: Long = 0,
    val messages: List<Message> = emptyList(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val isAttaching: Boolean = false,
    val isListening: Boolean = false,
    val isVoiceProcessing: Boolean = false,
    val streamingToken: String = "",
    val reasoningToken: String = "",
    val contextUsedTokens: Int = 0,
    val contextWindowTokens: Int = 4096,
    val contextStartTimestamp: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val modelStatus: String = "",
    val chatTitle: String = "Chat",
    val modelId: String = "",
    val modelName: String = "No model selected",
    val availableModels: List<DownloadedModel> = emptyList(),
    val showModelPicker: Boolean = false
)

sealed interface ChatDetailEvent {
    data object NavigateBack : ChatDetailEvent
    data class ShowError(val message: String) : ChatDetailEvent
    data class ShowMessage(val message: String) : ChatDetailEvent
}

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase,
    private val attachmentProcessor: AttachmentProcessor,
    private val voiceInputManager: VoiceInputManager,
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine
) : ViewModel() {

    private val chatId: Long = savedStateHandle.get<Long>("chatId") ?: 0L

    private val _uiState = MutableStateFlow(ChatDetailUiState(chatId = chatId))
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatDetailEvent>()
    val events: SharedFlow<ChatDetailEvent> = _events.asSharedFlow()

    private var generationJob: Job? = null
    private var voiceTimeoutJob: Job? = null
    private var voiceBaseText: String = ""
    private var voiceSessionActive: Boolean = false

    private var currentChat: Chat? = null

    private val streamBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var streamFlushJob: Job? = null

    init {
        if (chatId > 0) {
            observeChat()
            observeModels()
            loadMessages()
        }
    }

    private fun observeChat() {
        viewModelScope.launch {
            chatRepository.getChatById(chatId).collect { chat ->
                currentChat = chat
                val modelName = chat?.modelId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id -> modelRepository.getDownloadedModelById(id)?.name ?: id.substringAfter('/') }
                    ?: "No model selected"
                val contextWindow = chat?.modelId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id -> modelRepository.getModelSettingsOnce(id).contextSize }
                    ?.coerceIn(1024, 8192)
                    ?: DEFAULT_CONTEXT_WINDOW
                val memoryStart = chat?.memoryClearedAt ?: 0L
                _uiState.update {
                    it.copy(
                        chatTitle = chat?.title?.takeIf { value -> value.isNotBlank() } ?: "Chat",
                        modelId = chat?.modelId.orEmpty(),
                        modelName = modelName,
                        contextWindowTokens = contextWindow,
                        contextStartTimestamp = memoryStart,
                        contextUsedTokens = estimateContext(it.messages, memoryStart)
                    )
                }
            }
        }
    }

    private fun observeModels() {
        viewModelScope.launch {
            modelRepository.getDownloadedModels().collect { models ->
                _uiState.update { it.copy(availableModels = models.filter { model -> model.isEnabled }) }
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                getMessagesUseCase(chatId.toString())
                    .onSuccess { chatMessages ->
                        val messages = chatMessages.map { cm ->
                            Message(
                                id = cm.id.toString(),
                                chatId = cm.chatId.toString(),
                                content = cm.content,
                                role = when (cm.role) {
                                    com.ogrchatai.app.domain.model.MessageRole.USER -> Message.Role.USER
                                    com.ogrchatai.app.domain.model.MessageRole.ASSISTANT -> Message.Role.ASSISTANT
                                    com.ogrchatai.app.domain.model.MessageRole.SYSTEM -> Message.Role.SYSTEM
                                },
                                timestamp = cm.timestamp,
                                attachments = cm.attachments
                            )
                        }
                        _uiState.update {
                            it.copy(
                                messages = messages,
                                isLoading = false,
                                contextUsedTokens = estimateContext(
                                    messages,
                                    it.contextStartTimestamp
                                )
                            )
                        }
                    }
                    .onFailure { throwable ->
                        _uiState.update {
                            it.copy(isLoading = false, error = throwable.message)
                        }
                    }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message)
                }
            }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(currentInput = text) }
    }

    fun openModelPicker() {
        _uiState.update { it.copy(showModelPicker = true) }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    fun selectModel(modelId: String) {
        val chat = currentChat ?: return
        viewModelScope.launch {
            chatRepository.updateChat(chat.copy(modelId = modelId))
            val contextWindow = runCatching {
                modelRepository.getModelSettingsOnce(modelId).contextSize
            }.getOrDefault(DEFAULT_CONTEXT_WINDOW)
            _uiState.update {
                it.copy(
                    showModelPicker = false,
                    contextWindowTokens = contextWindow
                )
            }
        }
    }

    fun deleteMessage(messageId: String) {
        if (messageId == "streaming") return
        viewModelScope.launch {
            val numericId = messageId.toLongOrNull()
            if (numericId != null) {
                chatRepository.deleteMessage(numericId)
                inferenceEngine.invalidateConversation("chat:$chatId")
            }
            _uiState.update { state ->
                val messages = state.messages.filterNot { it.id == messageId }
                state.copy(
                    messages = messages,
                    contextUsedTokens = estimateContext(messages, state.contextStartTimestamp)
                )
            }
        }
    }

    fun clearMessages() {
        viewModelScope.launch {
            chatRepository.deleteMessagesByChatId(chatId)
            inferenceEngine.invalidateConversation("chat:$chatId")
            streamBuffer.clear()
            reasoningBuffer.clear()
            _uiState.update {
                it.copy(
                    messages = emptyList(),
                    streamingToken = "",
                    reasoningToken = "",
                    contextUsedTokens = 0
                )
            }
        }
    }

    /**
     * Stops sending earlier turns to the model without deleting the visible
     * messages. The current chat stays readable, but the model starts with a
     * clean memory from now on.
     */
    fun clearMemory() {
        viewModelScope.launch {
            val clearedAt = System.currentTimeMillis()
            chatRepository.clearChatMemory(chatId, clearedAt)
            inferenceEngine.invalidateConversation("chat:$chatId")
            _uiState.update { state ->
                state.copy(
                    contextStartTimestamp = clearedAt,
                    contextUsedTokens = estimateContext(state.messages, clearedAt)
                )
            }
            _events.emit(
                ChatDetailEvent.ShowMessage(
                    "Memory cleared. Earlier messages are hidden from the model."
                )
            )
        }
    }

    fun deleteChat() {
        viewModelScope.launch {
            inferenceEngine.invalidateConversation("chat:$chatId")
            chatRepository.deleteChat(chatId)
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return
        val existing = _uiState.value.messages.find { it.id == messageId } ?: return
        if (existing.role != Message.Role.USER) return

        viewModelScope.launch {
            val numericId = messageId.toLongOrNull()
            if (numericId != null) {
                val chatMessage = ChatMessage(
                    id = numericId,
                    chatId = chatId,
                    role = MessageRole.USER,
                    content = trimmed,
                    timestamp = existing.timestamp,
                    attachments = existing.attachments
                )
                chatRepository.updateMessage(chatMessage)
                inferenceEngine.invalidateConversation("chat:$chatId")
            }
            _uiState.update { state ->
                val messages = state.messages.map {
                    if (it.id == messageId) it.copy(content = trimmed) else it
                }
                state.copy(
                    messages = messages,
                    contextUsedTokens = estimateContext(messages, state.contextStartTimestamp)
                )
            }
        }
    }

    fun sendMessage() {
        val state = _uiState.value
        val content = state.currentInput.trim()
        val pendingAttachments = state.attachments

        if (content.isEmpty() && pendingAttachments.isEmpty()) return
        if (state.isGenerating) return

        cancelVoiceSession(keepText = true)

        val userMessage = Message(
            id = "msg_${System.currentTimeMillis()}",
            chatId = chatId.toString(),
            content = content,
            role = Message.Role.USER,
            attachments = pendingAttachments,
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            val messages = it.messages + userMessage
            it.copy(
                messages = messages,
                currentInput = "",
                attachments = emptyList(),
                isGenerating = true,
                streamingToken = "",
                reasoningToken = "",
                contextUsedTokens = estimateContext(messages, it.contextStartTimestamp),
                error = null,
                modelStatus = "Loading model..."
            )
        }
        streamBuffer.clear()
        reasoningBuffer.clear()

        generationJob = viewModelScope.launch {
            try {
                sendMessageUseCase(chatId, content, null, pendingAttachments)
                    .collect { result ->
                        result.onSuccess { response ->
                            handleGenerationResponse(response)
                        }
                        result.onFailure { throwable ->
                            failGeneration(
                                errorMessage = throwable.message ?: "Unknown error"
                            )
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failGeneration(errorMessage = e.message ?: "Unknown error")
            }
        }
    }

    private suspend fun handleGenerationResponse(response: SendMessageResponse) {
        when {
            response.isStreaming -> {
                if (response.token.isEmpty() && response.reasoningToken.isEmpty()) {
                    if (_uiState.value.modelStatus.startsWith("Loading")) {
                        _uiState.update { it.copy(modelStatus = "Thinking...") }
                    }
                    return
                }
                if (response.reasoningToken.isNotEmpty()) {
                    reasoningBuffer.append(response.reasoningToken)
                    scheduleStreamFlush()
                }
                if (response.token.isNotEmpty()) {
                    streamBuffer.append(response.token)
                    scheduleStreamFlush()
                }
            }

            response.isComplete -> {
                flushBufferedStream()
                val content = response.fullContent.ifBlank { _uiState.value.streamingToken }
                val assistantMessage = Message(
                    id = response.messageId ?: "msg_${System.currentTimeMillis()}",
                    chatId = chatId.toString(),
                    content = content,
                    role = Message.Role.ASSISTANT,
                    timestamp = System.currentTimeMillis()
                )
                _uiState.update { currentState ->
                    val messages = currentState.messages + assistantMessage
                    currentState.copy(
                        messages = messages,
                        isGenerating = false,
                        streamingToken = "",
                        reasoningToken = "",
                        contextUsedTokens = estimateContext(
                            messages,
                            currentState.contextStartTimestamp
                        ),
                        modelStatus = ""
                    )
                }
                generationJob = null
            }

            response.isError -> {
                failGeneration(
                    errorMessage = response.errorMessage ?: "Generation failed"
                )
            }
        }
    }

    private fun scheduleStreamFlush() {
        if (streamFlushJob?.isActive == true) return
        streamFlushJob = viewModelScope.launch {
            delay(STREAM_FLUSH_INTERVAL_MS)
            flushBufferedStream()
        }
    }

    private fun flushBufferedStream() {
        streamFlushJob?.cancel()
        streamFlushJob = null
        if (streamBuffer.isEmpty() && reasoningBuffer.isEmpty()) return

        val answer = streamBuffer.toString()
        val reasoning = reasoningBuffer.toString()
        streamBuffer.clear()
        reasoningBuffer.clear()

        _uiState.update { currentState ->
            val streamingToken = currentState.streamingToken + answer
            val reasoningToken = currentState.reasoningToken + reasoning
            currentState.copy(
                streamingToken = streamingToken,
                reasoningToken = reasoningToken,
                modelStatus = when {
                    streamingToken.isNotEmpty() -> ""
                    reasoningToken.isNotEmpty() -> "Reasoning..."
                    else -> currentState.modelStatus
                }
            )
        }
    }

    private fun clearStreamingBuffers() {
        streamBuffer.clear()
        reasoningBuffer.clear()
        streamFlushJob?.cancel()
        streamFlushJob = null
    }

    private suspend fun failGeneration(errorMessage: String?) {
        flushBufferedStream()
        val partial = _uiState.value.streamingToken.trim()
        clearStreamingBuffers()

        if (partial.isNotEmpty()) {
            runCatching { sendMessageUseCase.saveAssistantMessage(chatId, partial) }
        }

        _uiState.update { currentState ->
            val messages = if (partial.isNotEmpty()) {
                currentState.messages + Message(
                    id = "msg_${System.currentTimeMillis()}",
                    chatId = chatId.toString(),
                    content = partial,
                    role = Message.Role.ASSISTANT,
                    isPartial = true,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                currentState.messages
            }

            currentState.copy(
                messages = messages,
                isGenerating = false,
                streamingToken = "",
                reasoningToken = "",
                contextUsedTokens = estimateContext(
                    messages,
                    currentState.contextStartTimestamp
                ),
                modelStatus = "",
                error = errorMessage
            )
        }
        generationJob = null
    }

    fun stopGeneration() {
        val state = _uiState.value
        if (!state.isGenerating) return

        // Cancel the native llama.cpp loop first. Cancelling only the UI
        // collector can leave the model generating in the background, which
        // blocks the next request and keeps CPU/memory busy.
        inferenceEngine.cancelGeneration()
        generationJob?.cancel()
        generationJob = null
        flushBufferedStream()

        val partial = _uiState.value.streamingToken.trim()
        clearStreamingBuffers()
        if (partial.isNotEmpty()) {
            _uiState.update { currentState ->
                val messages = currentState.messages + Message(
                    id = "msg_${System.currentTimeMillis()}",
                    chatId = chatId.toString(),
                    content = partial,
                    role = Message.Role.ASSISTANT,
                    isPartial = true,
                    timestamp = System.currentTimeMillis()
                )
                currentState.copy(
                    messages = messages,
                    isGenerating = false,
                    streamingToken = "",
                    reasoningToken = "",
                    contextUsedTokens = estimateContext(
                        messages,
                        currentState.contextStartTimestamp
                    ),
                    modelStatus = ""
                )
            }
            viewModelScope.launch {
                runCatching { sendMessageUseCase.saveAssistantMessage(chatId, partial) }
            }
        } else {
            _uiState.update {
                it.copy(
                    isGenerating = false,
                    streamingToken = "",
                    reasoningToken = "",
                    modelStatus = ""
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun addAttachment(uri: Uri) {
        if (_uiState.value.isAttaching) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAttaching = true, error = null) }

            attachmentProcessor.importAttachment(uri)
                .onSuccess { attachment ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            attachments = currentState.attachments + attachment,
                            isAttaching = false
                        )
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            isAttaching = false,
                            error = throwable.message ?: "Failed to attach file"
                        )
                    }
                }
        }
    }

    fun removeAttachment(attachmentId: String) {
        val attachment = _uiState.value.attachments.find { it.id == attachmentId } ?: return

        _uiState.update { currentState ->
            currentState.copy(
                attachments = currentState.attachments.filter { it.id != attachmentId }
            )
        }
        viewModelScope.launch {
            attachmentProcessor.deleteAttachment(attachment)
        }
    }

    fun startVoiceInput() {
        val state = _uiState.value
        if (state.isGenerating || state.isListening || state.isVoiceProcessing) return
        if (voiceSessionActive) return

        voiceBaseText = state.currentInput.trimEnd().let { existing ->
            if (existing.isEmpty()) "" else "$existing "
        }
        voiceSessionActive = true

        _uiState.update {
            it.copy(
                isListening = true,
                isVoiceProcessing = false,
                error = null
            )
        }

        val started = voiceInputManager.start(
            languageTag = Locale.getDefault().toLanguageTag(),
            listener = object : VoiceInputManager.Listener {
                override fun onReadyForSpeech() {
                    if (!voiceSessionActive) return
                    _uiState.update {
                        it.copy(isListening = true, isVoiceProcessing = false)
                    }
                }

                override fun onPartialResult(text: String) {
                    if (!voiceSessionActive) return
                    _uiState.update {
                        it.copy(currentInput = voiceBaseText + text)
                    }
                }

                override fun onFinalResult(text: String) {
                    if (!voiceSessionActive) return
                    finishVoiceInput(text)
                }

                override fun onEndOfSpeech() {
                    if (!voiceSessionActive) return
                    _uiState.update { it.copy(isVoiceProcessing = true) }
                    scheduleVoiceTimeout()
                }

                override fun onNoSpeechDetected(message: String) {
                    if (!voiceSessionActive) return
                    endVoiceSession(message)
                }

                override fun onError(message: String) {
                    if (!voiceSessionActive) return
                    endVoiceSession(message)
                }
            }
        )

        if (!started) {
            voiceSessionActive = false
            voiceTimeoutJob?.cancel()
            voiceTimeoutJob = null
            _uiState.update {
                it.copy(isListening = false, isVoiceProcessing = false)
            }
        }
    }

    fun stopVoiceInput() {
        if (!voiceSessionActive) return

        _uiState.update {
            it.copy(isListening = false, isVoiceProcessing = true)
        }
        voiceInputManager.stop()
        scheduleVoiceTimeout()
    }

    private fun finishVoiceInput(text: String) {
        val finalText = (voiceBaseText + text.trim()).trim()
        voiceSessionActive = false
        voiceTimeoutJob?.cancel()
        voiceTimeoutJob = null
        voiceInputManager.release()

        _uiState.update {
            it.copy(
                currentInput = finalText,
                isListening = false,
                isVoiceProcessing = false
            )
        }
    }

    private fun endVoiceSession(errorMessage: String?) {
        val state = _uiState.value
        val hasPartialText = state.currentInput.trim() != voiceBaseText.trim()

        voiceSessionActive = false
        voiceTimeoutJob?.cancel()
        voiceTimeoutJob = null
        voiceInputManager.cancel()

        _uiState.update {
            it.copy(
                isListening = false,
                isVoiceProcessing = false,
                error = if (hasPartialText) null else errorMessage
            )
        }
    }

    private fun cancelVoiceSession(keepText: Boolean) {
        if (!voiceSessionActive) return

        voiceSessionActive = false
        voiceTimeoutJob?.cancel()
        voiceTimeoutJob = null
        voiceInputManager.cancel()

        _uiState.update {
            it.copy(
                isListening = false,
                isVoiceProcessing = false,
                currentInput = if (keepText) it.currentInput else voiceBaseText.trim()
            )
        }
    }

    private fun scheduleVoiceTimeout() {
        voiceTimeoutJob?.cancel()
        voiceTimeoutJob = viewModelScope.launch {
            delay(VOICE_TIMEOUT_MS)
            if (voiceSessionActive) {
                endVoiceSession("Speech recognition timed out. Please try again.")
            }
        }
    }

    override fun onCleared() {
        if (_uiState.value.isGenerating) {
            inferenceEngine.cancelGeneration()
        }
        generationJob?.cancel()
        super.onCleared()
        streamFlushJob?.cancel()
        voiceTimeoutJob?.cancel()
        streamBuffer.clear()
        reasoningBuffer.clear()
        voiceSessionActive = false
        voiceInputManager.release()
    }

    private fun estimateContext(messages: List<Message>, startTimestamp: Long): Int {
        if (startTimestamp <= 0) {
            return TokenEstimator.estimateUiMessages(messages)
        }
        return TokenEstimator.estimateUiMessages(
            messages.filter { it.timestamp > startTimestamp }
        )
    }

    companion object {
        private const val VOICE_TIMEOUT_MS = 8_000L
        private const val STREAM_FLUSH_INTERVAL_MS = 55L
        private const val DEFAULT_CONTEXT_WINDOW = 4096
    }
}
