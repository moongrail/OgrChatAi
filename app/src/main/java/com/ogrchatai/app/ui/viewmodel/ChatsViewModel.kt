package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.domain.repository.ModelRepository
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
    val error: String? = null,
    val showModelPicker: Boolean = false,
    val downloadedModels: List<Pair<String, String>> = emptyList(),
    val activeFilter: ChatFilter = ChatFilter.ALL
)

enum class ChatFilter(val label: String) {
    ALL("All"), RECENT("Recent"), FAVORITES("Favorites")
}

sealed interface ChatsEvent {
    data class SearchQueryChanged(val query: String) : ChatsEvent
    data class ChatCreated(val chatId: String) : ChatsEvent
    data object ClearError : ChatsEvent
}

sealed interface ChatsAction {
    data object CreateChat : ChatsAction
    data class DeleteChat(val chatId: String) : ChatsAction
    data class SelectModel(val modelId: String) : ChatsAction
    data class DismissModelPicker(val createWithSelected: Boolean) : ChatsAction
    data class FilterChats(val filter: ChatFilter) : ChatsAction
}

@HiltViewModel
class ChatsViewModel @Inject constructor(
    private val getChatsUseCase: GetChatsUseCase,
    private val createChatUseCase: CreateChatUseCase,
    private val deleteChatUseCase: DeleteChatUseCase,
    private val modelRepository: ModelRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatsUiState())
    val uiState: StateFlow<ChatsUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<List<ChatsEvent>>(emptyList())
    val events: StateFlow<List<ChatsEvent>> = _events.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    private var pendingSelectedModelId: String = ""

    init {
        observeChats()
        observeDownloadedModels()
    }

    private fun observeChats() {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            combine(
                getChatsUseCase(),
                _searchQuery
            ) { chats, query ->
                val filter = _uiState.value.activeFilter
                val filtered = chats.filter { chat ->
                    val matchesQuery = query.isBlank() || chat.title.contains(query, ignoreCase = true)
                    val matchesFilter = when (filter) {
                        ChatFilter.ALL -> true
                        ChatFilter.RECENT -> System.currentTimeMillis() - chat.updatedAt < 7 * 24 * 60 * 60 * 1000L
                        ChatFilter.FAVORITES -> chat.folderId != null
                    }
                    matchesQuery && matchesFilter
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
                .launchIn(this)
        }
    }

    private fun observeDownloadedModels() {
        viewModelScope.launch {
            modelRepository.getDownloadedModels().collect { models ->
                _uiState.update {
                    it.copy(
                        downloadedModels = models.map { m -> m.id to m.name }
                    )
                }
            }
        }
    }

    fun onAction(action: ChatsAction) {
        when (action) {
            is ChatsAction.CreateChat -> createChat()
            is ChatsAction.DeleteChat -> deleteChat(action.chatId)
            is ChatsAction.SelectModel -> selectModel(action.modelId)
            is ChatsAction.DismissModelPicker -> dismissModelPicker(action.createWithSelected)
            is ChatsAction.FilterChats -> _uiState.update { it.copy(activeFilter = action.filter) }
        }
    }

    fun onEvent(event: ChatsEvent) {
        when (event) {
            is ChatsEvent.SearchQueryChanged -> {
                _searchQuery.value = event.query
                _uiState.update { it.copy(searchQuery = event.query) }
            }
            is ChatsEvent.ChatCreated -> {
                _events.update { currentEvents -> currentEvents + event }
            }
            is ChatsEvent.ClearError -> {
                _uiState.update { it.copy(error = null) }
            }
        }
    }

    private fun createChat() {
        val downloadedModels = _uiState.value.downloadedModels
        if (downloadedModels.isEmpty()) {
            viewModelScope.launch {
                val chatId = createChatUseCase("New Chat", "TinyLlama/TinyLlama-1.1B-Chat-v1.0")
                _events.update { it + ChatsEvent.ChatCreated(chatId.toString()) }
            }
        } else {
            pendingSelectedModelId = downloadedModels.firstOrNull()?.first ?: ""
            _uiState.update { it.copy(showModelPicker = true) }
        }
    }

    private fun selectModel(modelId: String) {
        pendingSelectedModelId = modelId
        viewModelScope.launch {
            val modelName = modelRepository.getDownloadedModelById(modelId)?.name ?: modelId.substringAfter('/')
            val chatId = createChatUseCase("Chat with $modelName", modelId)
            _events.update { it + ChatsEvent.ChatCreated(chatId.toString()) }
            _uiState.update { it.copy(showModelPicker = false) }
        }
    }

    private fun dismissModelPicker(createWithSelected: Boolean) {
        if (createWithSelected && pendingSelectedModelId.isNotBlank()) {
            selectModel(pendingSelectedModelId)
        } else {
            _uiState.update { it.copy(showModelPicker = false) }
        }
    }

    private fun deleteChat(chatId: String) {
        viewModelScope.launch {
            val id = chatId.toLongOrNull() ?: return@launch
            deleteChatUseCase(id)
        }
    }

    fun consumeEvent(event: ChatsEvent) {
        _events.update { currentEvents -> currentEvents.filter { it !== event } }
    }
}
