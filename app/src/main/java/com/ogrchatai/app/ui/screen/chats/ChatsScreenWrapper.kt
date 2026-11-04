package com.ogrchatai.app.ui.screen.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ogrchatai.app.ui.screen.modelbrowser.ModelSettingsSheet
import com.ogrchatai.app.ui.viewmodel.ChatsAction
import com.ogrchatai.app.ui.viewmodel.ChatsEvent
import com.ogrchatai.app.ui.viewmodel.ChatsViewModel

@Composable
fun ChatsScreenWrapper(
    viewModel: ChatsViewModel,
    onChatClick: (Long) -> Unit,
    onModelBrowserClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onVoiceCallClick: () -> Unit,
    onManageModelsClick: () -> Unit = {}
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
                is ChatsEvent.NeedModelDownload -> {
                    viewModel.consumeEvent(event)
                    onModelBrowserClick()
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
        onVoiceCallClick = onVoiceCallClick,
        onManageModelsClick = onManageModelsClick,
        onFilterChange = { filter -> viewModel.onAction(ChatsAction.FilterChats(filter)) },
        downloadedModels = uiState.downloadedModels,
        onModelSelected = { modelId -> viewModel.onAction(ChatsAction.SelectModel(modelId)) },
        onModelToggled = { modelId -> viewModel.onAction(ChatsAction.ToggleModelEnabled(modelId)) },
        onModelSettingsClick = { modelId -> viewModel.onAction(ChatsAction.OpenModelSettings(modelId)) }
    )

    if (uiState.showModelPicker) {
        NewChatModelPickerDialog(
            models = uiState.downloadedModels.filter { it.third },
            initialModelId = uiState.pendingSelectedModelId,
            onStart = { modelId ->
                if (modelId.isNotBlank()) {
                    viewModel.onAction(ChatsAction.SelectModel(modelId))
                }
            },
            onBrowseModels = {
                viewModel.onAction(ChatsAction.DismissModelPicker(createWithSelected = false))
                onModelBrowserClick()
            },
            onDismiss = {
                viewModel.onAction(ChatsAction.DismissModelPicker(createWithSelected = false))
            }
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

@Composable
private fun NewChatModelPickerDialog(
    models: List<Triple<String, String, Boolean>>,
    initialModelId: String,
    onStart: (String) -> Unit,
    onBrowseModels: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedModelId by remember(models, initialModelId) {
        mutableStateOf(
            models.firstOrNull { it.first == initialModelId }?.first
                ?: models.firstOrNull()?.first.orEmpty()
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a model") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "The model stays attached to this chat. You can change it later from the chat header.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                models.forEach { (modelId, modelName, _) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedModelId = modelId }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedModelId == modelId,
                            onClick = { selectedModelId = modelId }
                        )
                        Column(modifier = Modifier.padding(start = 8.dp)) {
                            Text(modelName, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onStart(selectedModelId) },
                enabled = selectedModelId.isNotBlank()
            ) {
                Text("Start chat")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBrowseModels) {
                    Text("Browse models")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
