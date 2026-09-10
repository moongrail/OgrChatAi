package com.ogrchatai.app.ui.screen.chatdetail

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ogrchatai.app.ui.viewmodel.ChatDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatDetailViewModel,
    chatId: Long,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val name = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                c.getString(nameIndex)
            } ?: "unknown"
            val mimeType = context.contentResolver.getType(it) ?: "application/octet-stream"
            viewModel.addAttachment(it, name, mimeType)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val cursor = context.contentResolver.query(it, null, null, null, null)
            val name = cursor?.use { c ->
                val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                c.moveToFirst()
                c.getString(nameIndex)
            } ?: "image.jpg"
            viewModel.addAttachment(it, name, "image/*")
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = uiState.currentInput,
                onInputChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                onStopGeneration = viewModel::stopGeneration,
                isGenerating = uiState.isGenerating,
                attachments = uiState.attachments,
                onRemoveAttachment = viewModel::removeAttachment,
                onAttachFile = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.messages.isEmpty() && uiState.streamingToken.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Start a conversation",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Send a message to begin chatting with the AI",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = uiState.messages,
                        key = { it.id }
                    ) { message ->
                        MessageBubble(message = message)
                    }

                    if (uiState.streamingToken.isNotEmpty()) {
                        item {
                            MessageBubble(
                                message = com.ogrchatai.app.domain.model.Message(
                                    id = "streaming",
                                    chatId = chatId.toString(),
                                    content = uiState.streamingToken,
                                    role = com.ogrchatai.app.domain.model.Message.Role.ASSISTANT,
                                    isPartial = true,
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.isGenerating,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Generating...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = listState.firstVisibleItemIndex < uiState.messages.size - 3 && uiState.messages.size > 3,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(uiState.messages.lastIndex)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll to bottom",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}
