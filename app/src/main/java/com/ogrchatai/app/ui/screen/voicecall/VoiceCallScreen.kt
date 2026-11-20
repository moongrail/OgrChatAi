package com.ogrchatai.app.ui.screen.voicecall

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.ui.viewmodel.VoiceCallPhase
import com.ogrchatai.app.ui.viewmodel.VoiceCallViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallScreen(
    viewModel: VoiceCallViewModel,
    onBackClick: () -> Unit,
    onBrowseModels: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingStartAfterPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && pendingStartAfterPermission) {
            viewModel.startCall()
        } else if (!granted) {
            viewModel.clearError()
            pendingStartAfterPermission = false
        }
        pendingStartAfterPermission = false
    }

    LaunchedEffect(uiState.error) {
        val error = uiState.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Voice call",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isCallActive) viewModel.endCall()
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isCallActive) {
                        IconButton(onClick = { viewModel.endCall() }) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "End call",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (!uiState.isCallActive) {
            ModelSelectionContent(
                models = uiState.models,
                selectedModelId = uiState.selectedModelId,
                voiceLabel = uiState.voiceLabel,
                onSelectModel = viewModel::selectModel,
                onStart = {
                    val hasPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPermission) {
                        viewModel.startCall()
                    } else {
                        pendingStartAfterPermission = true
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onBrowseModels = onBrowseModels,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        } else {
            ActiveCallContent(
                uiState = uiState,
                onMicClick = viewModel::startListening,
                onEndCall = viewModel::endCall,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}

@Composable
private fun ModelSelectionContent(
    models: List<DownloadedModel>,
    selectedModelId: String,
    voiceLabel: String,
    onSelectModel: (String) -> Unit,
    onStart: () -> Unit,
    onBrowseModels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(8.dp))
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Talk to your model",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose a downloaded model. Its answer is spoken live.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Model",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (models.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No downloaded models yet.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onBrowseModels) {
                        Text("Browse models")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(models, key = { it.id }) { model ->
                    ModelChoiceCard(
                        model = model,
                        selected = model.id == selectedModelId,
                        onClick = { onSelectModel(model.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Speaking voice: $voiceLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "The selected model writes the answer; this voice reads it aloud. " +
                "Change the voice in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onStart,
            enabled = models.isNotEmpty() && selectedModelId.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.PhoneInTalk, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start voice call", fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ModelChoiceCard(
    model: DownloadedModel,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = listOfNotNull(
                        model.repository.takeIf { it.isNotBlank() },
                        model.formattedSize
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
            if (!model.isEnabled) {
                Text(
                    text = "disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ActiveCallContent(
    uiState: com.ogrchatai.app.ui.viewmodel.VoiceCallUiState,
    onMicClick: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val transcript = buildList {
        if (uiState.userTranscript.isNotBlank()) add("You: ${uiState.userTranscript}")
        if (uiState.assistantTranscript.isNotBlank()) add("AI: ${uiState.assistantTranscript}")
        if (uiState.partialTranscript.isNotBlank()) add("You: ${uiState.partialTranscript}")
    }

    LaunchedEffect(transcript.size, uiState.assistantTranscript.length) {
        if (transcript.isNotEmpty()) {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            // Never fight the user's scroll position, and do not restart an
            // animation for every streaming update.
            if (totalItems == 0 || lastVisible >= totalItems - 2) {
                listState.scrollToItem(transcript.lastIndex)
            }
        }
    }

    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = uiState.selectedModel?.name ?: "Voice call",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Speaking voice: ${uiState.voiceLabel}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (transcript.isEmpty()) {
                item {
                    Text(
                        text = uiState.status.ifBlank {
                            if (uiState.phase == VoiceCallPhase.LOADING) {
                                "Preparing the call..."
                            } else {
                                "Say something to start the conversation."
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    )
                }
            } else {
                items(transcript) { line ->
                    val isUser = line.startsWith("You:")
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = if (isUser) 16.dp else 4.dp,
                            topEnd = if (isUser) 4.dp else 16.dp,
                            bottomStart = 16.dp,
                            bottomEnd = 16.dp
                        ),
                        color = if (isUser) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        CallStatusPill(phase = uiState.phase, status = uiState.status)
        Spacer(modifier = Modifier.height(14.dp))
        CallMicButton(
            phase = uiState.phase,
            onClick = onMicClick
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = when (uiState.phase) {
                VoiceCallPhase.LOADING -> "Loading model..."
                VoiceCallPhase.LISTENING -> "Tap to finish speaking"
                VoiceCallPhase.THINKING -> "The model is thinking..."
                VoiceCallPhase.SPEAKING -> "Tap to interrupt and speak"
                VoiceCallPhase.READY -> "Tap to speak"
                VoiceCallPhase.MODEL_SELECTION -> ""
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onEndCall,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.CallEnd, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("End call")
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CallStatusPill(phase: VoiceCallPhase, status: String) {
    val color = when (phase) {
        VoiceCallPhase.LISTENING -> MaterialTheme.colorScheme.primary
        VoiceCallPhase.THINKING -> MaterialTheme.colorScheme.tertiary
        VoiceCallPhase.SPEAKING -> MaterialTheme.colorScheme.secondary
        VoiceCallPhase.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = status.ifBlank {
                when (phase) {
                    VoiceCallPhase.LISTENING -> "Listening..."
                    VoiceCallPhase.THINKING -> "Thinking..."
                    VoiceCallPhase.SPEAKING -> "Speaking..."
                    else -> "Ready"
                }
            },
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CallMicButton(phase: VoiceCallPhase, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (phase == VoiceCallPhase.LISTENING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulseScale"
    )

    val containerColor = when (phase) {
        VoiceCallPhase.LISTENING -> MaterialTheme.colorScheme.primary
        VoiceCallPhase.SPEAKING -> MaterialTheme.colorScheme.secondary
        VoiceCallPhase.THINKING, VoiceCallPhase.LOADING -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val contentColor = when (phase) {
        VoiceCallPhase.LISTENING, VoiceCallPhase.SPEAKING -> MaterialTheme.colorScheme.onPrimary
        VoiceCallPhase.THINKING, VoiceCallPhase.LOADING -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Box(
        modifier = Modifier
            .size(104.dp)
            .scale(pulse)
            .background(containerColor.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        FilledIconButton(
            onClick = onClick,
            enabled = phase != VoiceCallPhase.LOADING && phase != VoiceCallPhase.THINKING,
            modifier = Modifier.size(76.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (phase == VoiceCallPhase.THINKING || phase == VoiceCallPhase.LOADING) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    containerColor
                },
                contentColor = contentColor
            )
        ) {
            when (phase) {
                VoiceCallPhase.LOADING, VoiceCallPhase.THINKING -> CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.5.dp,
                    color = contentColor
                )

                VoiceCallPhase.LISTENING -> Icon(
                    Icons.Default.Mic,
                    contentDescription = "Stop listening",
                    modifier = Modifier.size(34.dp)
                )

                VoiceCallPhase.SPEAKING -> Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Interrupt and speak",
                    modifier = Modifier.size(34.dp)
                )

                else -> Icon(
                    Icons.Default.Mic,
                    contentDescription = "Start speaking",
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}
