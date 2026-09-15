package com.ogrchatai.app.ui.screen.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.ui.viewmodel.ChatFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    chats: List<Chat>,
    searchQuery: String,
    activeFilter: ChatFilter,
    onSearchQueryChange: (String) -> Unit,
    onChatClick: (Long) -> Unit,
    onNewChat: () -> Unit,
    onDeleteChat: (Long) -> Unit,
    onModelBrowserClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onFilterChange: (ChatFilter) -> Unit,
    downloadedModels: List<Triple<String, String, Boolean>> = emptyList(),
    onModelSelected: (String) -> Unit = {},
    onModelToggled: (String) -> Unit = {},
    onModelSettingsClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<Long?>(null) }
    var modelsExpanded by remember { mutableStateOf(true) }

    val filteredChats = remember(chats, searchQuery) {
        if (searchQuery.isBlank()) chats
        else chats.filter { chat ->
            chat.title.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OgrChatAi",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onModelBrowserClick) {
                        Icon(Icons.Default.Shop, contentDescription = "Model Browser")
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewChat,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search chats...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChatFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = activeFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = if (activeFilter == filter) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (filteredChats.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = if (searchQuery.isNotBlank()) "No matching chats" else "No chats yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap + to start a conversation",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredChats,
                    key = { it.id }
                ) { chat ->
                    ChatListItem(
                        chat = chat,
                        onClick = { onChatClick(chat.id) },
                        onDelete = {
                            chatToDelete = chat.id
                            showDeleteDialog = true
                        }
                    )
                }
            }

            if (downloadedModels.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { modelsExpanded = !modelsExpanded }
                            .animateContentSize(),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Models",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${downloadedModels.count { it.third }} active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                if (modelsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (modelsExpanded) {
                    items(
                        items = downloadedModels,
                        key = { it.first }
                    ) { (modelId, modelName, isEnabled) ->
                        ModelCardInline(
                            modelId = modelId,
                            modelName = modelName,
                            isEnabled = isEnabled,
                            onSelect = { onModelSelected(modelId) },
                            onToggle = { onModelToggled(modelId) },
                            onSettingsClick = { onModelSettingsClick(modelId) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog && chatToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                chatToDelete = null
            },
            title = { Text("Delete Chat") },
            text = { Text("Are you sure you want to delete this chat?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        chatToDelete?.let { onDeleteChat(it) }
                        showDeleteDialog = false
                        chatToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    chatToDelete = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ModelCardInline(
    modelId: String,
    modelName: String,
    isEnabled: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        onClick = if (isEnabled) onSelect else {{}}
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = modelId.substringBefore('/'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                modifier = Modifier.padding(start = 4.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                )
            )
        }
    }
}
