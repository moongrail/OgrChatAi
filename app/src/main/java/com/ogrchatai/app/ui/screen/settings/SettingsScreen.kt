package com.ogrchatai.app.ui.screen.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ogrchatai.app.domain.model.Language
import com.ogrchatai.app.domain.model.ThemeMode
import com.ogrchatai.app.domain.model.TtsEngine
import com.ogrchatai.app.domain.model.TtsVoice
import com.ogrchatai.app.domain.model.VoiceGender
import com.ogrchatai.app.ui.components.ConfirmDialog
import com.ogrchatai.app.ui.components.LoadingIndicator
import com.ogrchatai.app.ui.components.SectionHeader
import com.ogrchatai.app.ui.components.SettingsItem
import com.ogrchatai.app.ui.viewmodel.SettingsAction
import com.ogrchatai.app.ui.viewmodel.SettingsEvent
import com.ogrchatai.app.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onDownloadedModelsClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var showVoiceLicenseDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var tempTemperature by remember(uiState.generationConfig.temperature) {
        mutableFloatStateOf(uiState.generationConfig.temperature)
    }
    var tempTopP by remember(uiState.generationConfig.topP) {
        mutableFloatStateOf(uiState.generationConfig.topP)
    }
    var tempMaxTokens by remember(uiState.generationConfig.maxTokens) {
        mutableIntStateOf(uiState.generationConfig.maxTokens)
    }
    val neuralInstalled = uiState.neuralVoice.isInstalled
    val neuralActive = neuralInstalled && uiState.ttsEngine == TtsEngine.AUTO
    val activeGender = if (neuralInstalled) uiState.ttsVoice.gender else uiState.voiceGender

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowError -> snackbarHostState.showSnackbar(
                    event.message,
                    duration = SnackbarDuration.Short
                )
                is SettingsEvent.ShowMessage -> snackbarHostState.showSnackbar(
                    event.message,
                    duration = SnackbarDuration.Short
                )
                SettingsEvent.SettingsSaved -> snackbarHostState.showSnackbar(
                    "Settings saved",
                    duration = SnackbarDuration.Short
                )
                SettingsEvent.HistoryCleared -> snackbarHostState.showSnackbar(
                    "Chat history and downloaded models were cleared",
                    duration = SnackbarDuration.Short
                )
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    if (showClearDialog) {
        ConfirmDialog(
            title = "Clear All Data",
            message = "This will delete all chat history and downloaded models. This action cannot be undone.",
            confirmText = "Clear",
            dismissText = "Cancel",
            isDestructive = true,
            onConfirm = {
                viewModel.onAction(SettingsAction.ClearChatHistory)
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }

    if (showVoiceLicenseDialog) {
        AlertDialog(
            onDismissRequest = { showVoiceLicenseDialog = false },
            title = { Text("Download neural voices?") },
            text = {
                Text(
                    "Supertonic 3 provides 10 expressive voices and works fully on-device. " +
                        "The download is about 139 MB and installs directly without unpacking an " +
                        "archive; allow about 200 MB of free storage. The model is licensed under " +
                        "OpenRAIL-M. By downloading you agree to that model license. Audio is " +
                        "generated locally and never uploaded."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showVoiceLicenseDialog = false
                        viewModel.onAction(SettingsAction.DownloadNeuralVoice)
                    }
                ) {
                    Text("Download")
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoiceLicenseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
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
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingIndicator(modifier = Modifier.padding(innerPadding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                SectionHeader(title = "Language")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Column {
                        Language.entries.forEach { language ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.language == language,
                                    onClick = {
                                        viewModel.onAction(SettingsAction.UpdateLanguage(language))
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = language.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "Theme")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Column {
                        ThemeMode.entries.forEach { theme ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.theme == theme,
                                    onClick = {
                                        viewModel.onAction(SettingsAction.UpdateTheme(theme))
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (theme) {
                                        ThemeMode.SYSTEM -> "System Default"
                                        ThemeMode.LIGHT -> "Light"
                                        ThemeMode.DARK -> "Dark"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "Voice")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Neural voice · Supertonic 3",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "10 emotional voices · 5 languages · on-device",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = com.ogrchatai.app.domain.model.TtsLanguages.displayList,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TtsEngine.entries.forEach { engine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.ttsEngine == engine,
                                    onClick = {
                                        viewModel.onAction(SettingsAction.UpdateTtsEngine(engine))
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (engine) {
                                        TtsEngine.AUTO -> "Supertonic when installed (recommended)"
                                        TtsEngine.SYSTEM -> "Android system voice only"
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        ActiveVoiceSummary(
                            title = if (neuralActive) {
                                "Supertonic 3 · ${uiState.ttsVoice.displayName}"
                            } else {
                                "Android system voice · ${activeGender.label()}"
                            },
                            subtitle = when {
                                neuralActive -> "This is the voice used in calls. If Supertonic " +
                                    "cannot load, Android falls back to its ${activeGender.label()} voice."
                                uiState.ttsEngine == TtsEngine.SYSTEM && neuralInstalled ->
                                    "Neural voices are installed but switched off. Android speaks " +
                                        "every reply with the selected ${uiState.voiceGender.label()} voice."
                                else -> "Supertonic is not installed, so Android speaks every reply " +
                                    "with the selected ${uiState.voiceGender.label()} voice."
                            }
                        )

                        if (neuralInstalled) {
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Neural voice · Supertonic 3",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            VoiceSelector(
                                selectedVoice = uiState.ttsVoice,
                                enabled = uiState.ttsEngine == TtsEngine.AUTO,
                                onVoiceSelected = {
                                    viewModel.onAction(SettingsAction.UpdateTtsVoice(it))
                                }
                            )
                            if (uiState.ttsEngine == TtsEngine.SYSTEM) {
                                Text(
                                    text = "Switch to “Supertonic when installed” above to use " +
                                        "this voice.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val status = uiState.neuralVoice
                        when {
                            status.isDownloading -> {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = { status.progress.coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val downloadedMb = status.downloadedBytes / (1024 * 1024)
                                    val totalMb = status.totalBytes / (1024 * 1024)
                                    Text(
                                        text = "Downloading neural voices… " +
                                            "${(status.progress * 100).roundToInt()}% · " +
                                            "$downloadedMb / $totalMb MB",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            status.isInstalled -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (uiState.ttsEngine == TtsEngine.AUTO) {
                                            "Installed and ready for offline use"
                                        } else {
                                            "Installed, but currently disabled by the system engine"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.onAction(SettingsAction.DeleteNeuralVoice)
                                        }
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }

                            else -> {
                                Column(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Download once (~139 MB) for natural, expressive " +
                                            "speech. Files install directly without an unpacking step, " +
                                            "and the Android system voice is used until the pack is ready.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { showVoiceLicenseDialog = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download neural voices")
                                    }
                                    status.error?.let { error ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = error,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (neuralActive) {
                            Text(
                                text = "Android fallback voice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Text(
                                text = "${activeGender.label()} · the fallback follows the gender of " +
                                    "the selected neural voice and is used only if Supertonic cannot load.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        } else {
                            Text(
                                text = "Android system voice",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                            Text(
                                text = if (uiState.ttsEngine == TtsEngine.SYSTEM) {
                                    "Used for every spoken reply while the system engine is selected."
                                } else {
                                    "Used until the Supertonic voice pack is installed."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            VoiceGender.entries.forEach { gender ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = uiState.voiceGender == gender,
                                        onClick = {
                                            viewModel.onAction(SettingsAction.UpdateVoiceGender(gender))
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = gender.label(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = { viewModel.onAction(SettingsAction.TestVoice) },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text("Test voice")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "Generation Parameters")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Thermostat,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Temperature",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Text(
                                text = "%.1f".format(tempTemperature),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = tempTemperature,
                            onValueChange = { tempTemperature = it },
                            onValueChangeFinished = {
                                viewModel.onAction(SettingsAction.UpdateTemperature(tempTemperature))
                            },
                            valueRange = 0f..2f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Straighten,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Top P",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Text(
                                text = "%.2f".format(tempTopP),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = tempTopP,
                            onValueChange = { tempTopP = it },
                            onValueChangeFinished = {
                                viewModel.onAction(SettingsAction.UpdateTopP(tempTopP))
                            },
                            valueRange = 0f..1f,
                            steps = 19,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Token,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Max Tokens",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            Text(
                                text = tempMaxTokens.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = tempMaxTokens.toFloat(),
                            onValueChange = { tempMaxTokens = it.roundToInt() },
                            onValueChangeFinished = {
                                viewModel.onAction(SettingsAction.UpdateMaxTokens(tempMaxTokens))
                            },
                            valueRange = 64f..4096f,
                            steps = 0,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.tertiary,
                                activeTrackColor = MaterialTheme.colorScheme.tertiary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "Storage")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    SettingsItem(
                        icon = Icons.Filled.SdStorage,
                        title = "Downloaded Models",
                        subtitle = "Manage your downloaded models",
                        onClick = onDownloadedModelsClick
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "About")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.dp
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Filled.Info,
                            title = "App Version",
                            subtitle = com.ogrchatai.app.BuildConfig.VERSION_NAME,
                            onClick = { }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "Danger Zone")

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shadowElevation = 1.dp
                ) {
                    SettingsItem(
                        icon = Icons.Filled.DeleteForever,
                        title = "Clear All Data",
                        subtitle = "Delete all chats, models, and settings",
                        onClick = { showClearDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ActiveVoiceSummary(
    title: String,
    subtitle: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "ACTIVE VOICE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun VoiceGender.label(): String = when (this) {
    VoiceGender.FEMALE -> "Female"
    VoiceGender.MALE -> "Male"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSelector(
    selectedVoice: TtsVoice,
    enabled: Boolean,
    onVoiceSelected: (TtsVoice) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Surface(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (enabled) 0.45f else 0.22f
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedVoice.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (enabled) 1f else 0.55f
                        )
                    )
                    Text(
                        text = selectedVoice.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.55f
                        )
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = if (enabled) "Choose voice" else "Voice selection disabled",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (enabled) 1f else 0.4f
                    )
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Text(
                text = "Female voices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
            )
            TtsVoice.entries.filter { it.gender == VoiceGender.FEMALE }.forEach { voice ->
                VoiceMenuItem(
                    voice = voice,
                    selected = selectedVoice == voice,
                    onClick = {
                        onVoiceSelected(voice)
                        expanded = false
                    }
                )
            }
            HorizontalDivider()
            Text(
                text = "Male voices",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
            )
            TtsVoice.entries.filter { it.gender == VoiceGender.MALE }.forEach { voice ->
                VoiceMenuItem(
                    voice = voice,
                    selected = selectedVoice == voice,
                    onClick = {
                        onVoiceSelected(voice)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun VoiceMenuItem(
    voice: TtsVoice,
    selected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = voice.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    text = voice.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onClick,
        leadingIcon = {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    )
}
