package com.ogrchatai.app.ui.screen.chatdetail

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.ui.text.style.TextOverflow
import com.ogrchatai.app.R
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.ui.viewmodel.ChatDetailEvent
import com.ogrchatai.app.ui.viewmodel.ChatDetailViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    viewModel: ChatDetailViewModel,
    chatId: Long,
    onBackClick: () -> Unit,
    onBrowseModels: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showClearMessagesDialog by remember { mutableStateOf(false) }
    var showClearMemoryDialog by remember { mutableStateOf(false) }
    var showDeleteChatDialog by remember { mutableStateOf(false) }
    val streamingStartedAt = remember(uiState.isGenerating) { System.currentTimeMillis() }

    val permissionDeniedMessage = stringResource(R.string.voice_error_permission)

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(viewModel::addAttachment)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let(viewModel::addAttachment)
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            viewModel.startVoiceInput()
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = permissionDeniedMessage,
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val onVoiceClick = {
        when {
            uiState.isListening -> viewModel.stopVoiceInput()
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> viewModel.startVoiceInput()

            else -> recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    // Keep the streaming answer visible while tokens are appended.
    LaunchedEffect(uiState.streamingToken.length, uiState.reasoningToken.length) {
        if (uiState.streamingToken.isNotEmpty() || uiState.reasoningToken.isNotEmpty()) {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (totalItems > 0 && lastVisible >= totalItems - 2) {
                listState.scrollToItem(totalItems - 1)
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatDetailEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                is ChatDetailEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.message, duration = SnackbarDuration.Short)
                ChatDetailEvent.NavigateBack -> onBackClick()
            }
        }
    }

    val isModelAvailable = uiState.availableModels.any { it.id == uiState.modelId }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.chatTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Text(
                            text = if (isModelAvailable) uiState.modelName
                            else "Model unavailable — tap to choose",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isModelAvailable) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openModelPicker) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = "Choose model"
                        )
                    }
                    Box {
                        IconButton(onClick = { showChatMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Chat actions"
                            )
                        }
                        DropdownMenu(
                            expanded = showChatMenu,
                            onDismissRequest = { showChatMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Clear memory") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Memory,
                                        contentDescription = null
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    showClearMemoryDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear messages") },
                                onClick = {
                                    showChatMenu = false
                                    showClearMessagesDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete chat") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showChatMenu = false
                                    showDeleteChatDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding()) {
                if (!isModelAvailable) {
                    ModelUnavailableBanner(
                        hasModels = uiState.availableModels.isNotEmpty(),
                        onChooseModel = viewModel::openModelPicker
                    )
                }
                ContextUsageBar(
                    usedTokens = uiState.contextUsedTokens,
                    maxTokens = uiState.contextWindowTokens
                )
                ChatInputBar(
                    inputText = uiState.currentInput,
                    onInputChange = viewModel::updateInput,
                    onSend = viewModel::sendMessage,
                    onStopGeneration = viewModel::stopGeneration,
                    isGenerating = uiState.isGenerating,
                    attachments = uiState.attachments,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onAttachFile = { showAttachSheet = true },
                    isListening = uiState.isListening,
                    isVoiceProcessing = uiState.isVoiceProcessing,
                    isAttaching = uiState.isAttaching,
                    onVoiceClick = onVoiceClick,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val totalItems = uiState.messages.size +
                (if (uiState.streamingToken.isNotEmpty()) 1 else 0) +
                (if (uiState.reasoningToken.isNotEmpty()) 1 else 0)

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else if (uiState.messages.isEmpty() &&
                uiState.streamingToken.isEmpty() &&
                uiState.reasoningToken.isEmpty()
            ) {
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
                        text = "Send a message or attach a file to begin chatting with the AI",
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
                        MessageBubble(
                            message = message,
                            onEdit = { content -> viewModel.editMessage(message.id, content) },
                            onDelete = { viewModel.deleteMessage(message.id) }
                        )
                    }

                    if (uiState.reasoningToken.isNotEmpty()) {
                        item(key = "reasoning") {
                            ReasoningPanel(text = uiState.reasoningToken)
                        }
                    }

                    if (uiState.streamingToken.isNotEmpty()) {
                        item(key = "streaming") {
                            MessageBubble(
                                message = com.ogrchatai.app.domain.model.Message(
                                    id = "streaming",
                                    chatId = chatId.toString(),
                                    content = uiState.streamingToken,
                                    role = com.ogrchatai.app.domain.model.Message.Role.ASSISTANT,
                                    isPartial = true,
                                    timestamp = streamingStartedAt
                                )
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.isGenerating || uiState.modelStatus.isNotEmpty(),
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
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = uiState.modelStatus.ifEmpty { "Generating..." },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = totalItems > 3 && listState.firstVisibleItemIndex < totalItems - 3,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem((totalItems - 1).coerceAtLeast(0))
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

    if (showClearMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearMemoryDialog = false },
            title = { Text("Clear model memory?") },
            text = {
                Text(
                    "The model will stop using earlier messages in this chat. " +
                        "The messages stay visible, and new replies will only use " +
                        "the conversation after this point."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMemory()
                        showClearMemoryDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear memory")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMemoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearMessagesDialog) {
        AlertDialog(
            onDismissRequest = { showClearMessagesDialog = false },
            title = { Text("Clear messages?") },
            text = { Text("All messages in this chat will be permanently deleted. The chat itself will stay.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearMessages()
                        showClearMessagesDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearMessagesDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteChatDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteChatDialog = false },
            title = { Text("Delete chat?") },
            text = { Text("This chat and all of its messages will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatDialog = false
                        viewModel.deleteChat()
                        onBackClick()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showModelPicker) {
        ModelPickerDialog(
            models = uiState.availableModels,
            selectedModelId = uiState.modelId,
            onSelect = { modelId ->
                viewModel.selectModel(modelId)
                viewModel.dismissModelPicker()
            },
            onBrowseModels = {
                viewModel.dismissModelPicker()
                onBrowseModels()
            },
            onDismiss = viewModel::dismissModelPicker
        )
    }

    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.chat_attach_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_attach_photo)) },
                    supportingContent = { Text("Choose an image from your gallery") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text(stringResource(R.string.chat_attach_document)) },
                    supportingContent = { Text("Documents, text, code and other files") },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    },
                    modifier = Modifier.clickable {
                        showAttachSheet = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    }
                )
            }
        }
    }
}

@Composable
private fun ModelUnavailableBanner(
    hasModels: Boolean,
    onChooseModel: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChooseModel)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "Model is not available",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (hasModels) "Tap to choose another downloaded model"
                    else "Tap to pick a model after downloading one",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ReasoningPanel(text: String) {
    var expanded by remember { mutableStateOf(true) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Reasoning",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (expanded) "hide" else "show",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContextUsageBar(usedTokens: Int, maxTokens: Int) {
    if (usedTokens <= 0) return

    val safeMax = maxTokens.coerceAtLeast(1)
    val fraction = (usedTokens.toFloat() / safeMax).coerceIn(0f, 1f)
    val isNearLimit = fraction >= 0.85f

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Context",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "~$usedTokens / $safeMax tokens",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isNearLimit) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = if (isNearLimit) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun ModelPickerDialog(
    models: List<DownloadedModel>,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    onBrowseModels: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chat model") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (models.isEmpty()) {
                    Text(
                        "No downloaded models are enabled. Download a model to start chatting.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Choose which model answers in this chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    models.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(model.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedModelId == model.id,
                                onClick = { onSelect(model.id) }
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = model.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        model.quantization.takeIf { it.isNotBlank() },
                                        model.formattedSize
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onBrowseModels) { Text("Browse models") }
        }
    )
}
