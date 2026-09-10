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
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.ogrchatai.app.domain.model.Language
import com.ogrchatai.app.domain.model.ThemeMode
import com.ogrchatai.app.ui.components.ConfirmDialog
import com.ogrchatai.app.ui.components.LoadingIndicator
import com.ogrchatai.app.ui.components.SectionHeader
import com.ogrchatai.app.ui.components.SettingsItem
import com.ogrchatai.app.ui.viewmodel.SettingsAction
import com.ogrchatai.app.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }
    var tempTemperature by remember(uiState.generationConfig.temperature) {
        mutableFloatStateOf(uiState.generationConfig.temperature)
    }
    var tempTopP by remember(uiState.generationConfig.topP) {
        mutableFloatStateOf(uiState.generationConfig.topP)
    }
    var tempMaxTokens by remember(uiState.generationConfig.maxTokens) {
        mutableIntStateOf(uiState.generationConfig.maxTokens)
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

    Scaffold(
        modifier = Modifier,
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
                        onClick = { /* TODO: Navigate to downloaded models screen */ }
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
                            subtitle = "1.0.0",
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
