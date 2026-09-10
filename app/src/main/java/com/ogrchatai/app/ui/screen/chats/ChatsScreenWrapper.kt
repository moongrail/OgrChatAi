package com.ogrchatai.app.ui.screen.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    // Handle events
    LaunchedEffect(events) {
        events.forEach { event ->
            when (event) {
                is com.ogrchatai.app.ui.viewmodel.ChatsEvent.ChatCreated -> {
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
        onSearchQueryChange = { viewModel.onEvent(com.ogrchatai.app.ui.viewmodel.ChatsEvent.SearchQueryChanged(it)) },
        onChatClick = onChatClick,
        onNewChat = { viewModel.onAction(com.ogrchatai.app.ui.viewmodel.ChatsAction.CreateChat) },
        onDeleteChat = { chatId -> viewModel.onAction(com.ogrchatai.app.ui.viewmodel.ChatsAction.DeleteChat(chatId.toString())) },
        onModelBrowserClick = onModelBrowserClick,
        onSettingsClick = onSettingsClick
    )
}
