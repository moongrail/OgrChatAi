package com.ogrchatai.app.ui.screen.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ogrchatai.app.ui.screen.modelbrowser.ModelSettingsSheet
import com.ogrchatai.app.ui.viewmodel.ChatsAction
import com.ogrchatai.app.ui.viewmodel.ChatsEvent
import com.ogrchatai.app.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreenWrapper(
    viewModel: ChatsViewModel,
    onChatClick: (Long) -> Unit,
    onModelBrowserClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val events by viewModel.events.collectAsState()

    LaunchedEffect(events) {
        events.forEach { event ->
            when (event) {
                is ChatsEvent.ChatCreated -> {
                    val chatId = event.chatId.toLongOrNull() ?: 0L
                    if (chatId > 0) {
                        onChatClick(chatId)
                    }
                    viewModel.consumeEvent(event)
                }
                else -> {}
            }
        }
    }

    ChatsScreen(
        chats = uiState.filteredChats,
        searchQuery = uiState.searchQuery,
        activeFilter = uiState.activeFilter,
        onSearchQueryChange = { viewModel.onEvent(ChatsEvent.SearchQueryChanged(it)) },
        onChatClick = onChatClick,
        onNewChat = { viewModel.onAction(ChatsAction.CreateChat) },
        onDeleteChat = { chatId -> viewModel.onAction(ChatsAction.DeleteChat(chatId.toString())) },
        onModelBrowserClick = onModelBrowserClick,
        onSettingsClick = onSettingsClick,
        onFilterChange = { filter -> viewModel.onAction(ChatsAction.FilterChats(filter)) },
        downloadedModels = uiState.downloadedModels
    )

    if (uiState.showModelPicker) {
        ModelPickerDialog(
            models = uiState.downloadedModels,
            onSelect = { modelId -> viewModel.onAction(ChatsAction.SelectModel(modelId)) },
            onToggleEnabled = { modelId -> viewModel.onAction(ChatsAction.ToggleModelEnabled(modelId)) },
            onModelSettingsClick = { modelId -> viewModel.onAction(ChatsAction.OpenModelSettings(modelId)) },
            onDismiss = { viewModel.onAction(ChatsAction.DismissModelPicker(false)) }
        )
    }

    if (uiState.showModelSettings && uiState.modelSettingsForEdit != null) {
        ModelSettingsSheet(
            modelId = uiState.selectedModelIdForSettings,
            modelName = uiState.selectedModelNameForSettings,
            settings = uiState.modelSettingsForEdit!!,
            onSave = { settings -> viewModel.onAction(ChatsAction.SaveModelSettings(settings)) },
            onDismiss = { viewModel.onAction(ChatsAction.DismissModelSettings) }
        )
    }
}
