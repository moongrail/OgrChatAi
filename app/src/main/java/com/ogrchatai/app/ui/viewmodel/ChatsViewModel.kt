package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.domain.usecase.chat.CreateChatUseCase
import com.ogrchatai.app.domain.usecase.chat.DeleteChatUseCase
import com.ogrchatai.app.domain.usecase.chat.GetChatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatsUiState(
    val chats: List<Chat> = emptyList(),
    val filteredChats: List<Chat> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface ChatsEvent {
    data class SearchQueryChanged(val query: String) : ChatsEvent
    data class ChatCreated(val chatId: String) : ChatsEvent
    data object ClearError : ChatsEvent
}

sealed interface ChatsAction {
    data object CreateChat : ChatsAction
    data class DeleteChat(val chatId: String) : ChatsAction
}

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val getChatsUseCase: GetChatsUseCase,
    private val createChatUseCase: CreateChatUseCase,
    private val deleteChatUseCase: DeleteChatUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<ChatsEvent>>(emptyList())
    val events: StateFlow<List<ChatsEvent>> = _events.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    init {
        observeChats()
    }

    private fun observeChats() {
        _uiState.update { it.copy(isLoading = true) }

        combine(
            getChatsUseCase(),
            _searchQuery
        ) { chats, query ->
            val filtered = if (query.isBlank()) {
                chats
            } else {
                chats.filter { chat ->
                    chat.title.contains(query, ignoreCase = true)
                }
            }
            chats to filtered
        }
            .onEach { (chats, filtered) ->
                _uiState.update {
                    it.copy(
                        chats = chats,
                        filteredChats = filtered,
                        isLoading = false,
                        error = null
                    )
                }
            }
            .catch { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = throwable.message ?: "Failed to load chats"
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ChatsAction) {
        when (action) {
            is ChatsAction.CreateChat -> createChat()
            is ChatsAction.DeleteChat -> deleteChat(action.chatId)
        }
    }

    fun onEvent(event: ChatsEvent) {
        when (event) {
            is ChatsEvent.SearchQueryChanged -> {
                _searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is ChatsEvent.ChatCreated -> {
                _events.update { currentEvents ->
                    currentEvents + event
                }
            }
            is ChatsEvent.ClearError -> {
                _uiState.update { it.copy(error = null) }
            }
        }
    }

    private fun createChat() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Use a default model ID - in real app this would come from settings or user selection
            val defaultModelId = "mistralai/Mistral-7B-Instruct-v0.1"
            val chatId = createChatUseCase("New Chat", defaultModelId)
            _events.update { currentEvents ->
                currentEvents + ChatsEvent.ChatCreated(chatId.toString())
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun deleteChat(chatId: String) {
        viewModelScope.launch {
            val id = chatId.toLongOrNull() ?: return@launch
            deleteChatUseCase(id)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun consumeEvent(event: ChatsEvent) {
        _events.update { currentEvents ->
            currentEvents.filter { it !== event }
        }
    }
}
