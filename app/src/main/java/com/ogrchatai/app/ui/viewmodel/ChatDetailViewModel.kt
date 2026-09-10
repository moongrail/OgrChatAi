package com.ogrchatai.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.domain.model.Message
import com.ogrchatai.app.domain.model.SendMessageResponse
import com.ogrchatai.app.domain.usecase.chat.GetMessagesUseCase
import com.ogrchatai.app.domain.usecase.chat.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatDetailUiState(
    val chatId: Long = 0,
    val messages: List<Message> = emptyList(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val streamingToken: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ChatDetailEvent {
    data object NavigateBack : ChatDetailEvent
    data class ShowError(val message: String) : ChatDetailEvent
}

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sendMessageUseCase: SendMessageUseCase,
    private val getMessagesUseCase: GetMessagesUseCase
) : ViewModel() {

    private val chatId: Long = savedStateHandle.get<Long>("chatId") ?: 0L

    private val _uiState = MutableStateFlow(ChatDetailUiState(chatId = chatId))
    val uiState: StateFlow<ChatDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ChatDetailEvent>()
    val events: SharedFlow<ChatDetailEvent> = _events.asSharedFlow()

    private var generationJob: Job? = null

    init {
        if (chatId > 0) {
            loadMessages()
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
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
                            attachments = cm.attachments.map { it.id }
                        )
                    }
                    _uiState.update {
                        it.copy(messages = messages, isLoading = false)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, error = throwable.message)
                    }
                }
        }
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(currentInput = text) }
    }

    fun sendMessage() {
        val state = _uiState.value
        val content = state.currentInput.trim()
        if (content.isEmpty() && state.attachments.isEmpty()) return
        if (state.isGenerating) return

        val userMessage = Message(
            id = "msg_${System.currentTimeMillis()}",
            chatId = chatId.toString(),
            content = content,
            role = Message.Role.USER,
            attachments = state.attachments.map { it.id },
            timestamp = System.currentTimeMillis()
        )

        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                currentInput = "",
                attachments = emptyList(),
                isGenerating = true,
                streamingToken = "",
                error = null
            )
        }

        generationJob = viewModelScope.launch {
            sendMessageUseCase(chatId, content, null, state.attachments)
                .collect { result ->
                    result.onSuccess { response ->
                        when {
                            response.isStreaming -> {
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        streamingToken = currentState.streamingToken + response.token
                                    )
                                }
                            }
                            response.isComplete -> {
                                val assistantMessage = Message(
                                    id = response.messageId ?: "msg_${System.currentTimeMillis()}",
                                    chatId = chatId.toString(),
                                    content = _uiState.value.streamingToken.ifEmpty { response.fullContent },
                                    role = Message.Role.ASSISTANT,
                                    timestamp = System.currentTimeMillis()
                                )
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        messages = currentState.messages + assistantMessage,
                                        isGenerating = false,
                                        streamingToken = ""
                                    )
                                }
                            }
                            response.isError -> {
                                _uiState.update { currentState ->
                                    currentState.copy(
                                        isGenerating = false,
                                        streamingToken = "",
                                        error = response.errorMessage
                                    )
                                }
                            }
                        }
                    }
                    result.onFailure { throwable ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                isGenerating = false,
                                streamingToken = "",
                                error = throwable.message
                            )
                        }
                    }
                }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        val state = _uiState.value
        if (state.streamingToken.isNotEmpty()) {
            val partialMessage = Message(
                id = "msg_${System.currentTimeMillis()}",
                chatId = chatId.toString(),
                content = state.streamingToken,
                role = Message.Role.ASSISTANT,
                isPartial = true,
                timestamp = System.currentTimeMillis()
            )
            _uiState.update { currentState ->
                currentState.copy(
                    messages = currentState.messages + partialMessage,
                    isGenerating = false,
                    streamingToken = ""
                )
            }
        } else {
            _uiState.update { it.copy(isGenerating = false) }
        }
    }

    fun addAttachment(uri: Uri, name: String, mimeType: String) {
        val attachment = Attachment(
            id = "att_${System.currentTimeMillis()}",
            fileName = name,
            mimeType = mimeType,
            size = 0,
            uri = uri.toString()
        )
        _uiState.update { currentState ->
            currentState.copy(attachments = currentState.attachments + attachment)
        }
    }

    fun removeAttachment(attachmentId: String) {
        _uiState.update { currentState ->
            currentState.copy(
                attachments = currentState.attachments.filter { it.id != attachmentId }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        generationJob?.cancel()
    }
}
