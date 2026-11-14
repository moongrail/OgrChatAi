package com.ogrchatai.app.ui.screen.modelbrowser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.ui.viewmodel.DownloadedModelsAction
import com.ogrchatai.app.ui.viewmodel.DownloadedModelsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedModelsScreen(
    onBackClick: () -> Unit,
    onBrowseModels: () -> Unit,
    viewModel: DownloadedModelsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    uiState.modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { viewModel.onAction(DownloadedModelsAction.DismissDelete) },
            title = { Text("Delete model?") },
            text = {
                Text(
                    "The file for \"${model.name}\" will be removed from this device. " +
                        "Chats that use it will show a missing-model error until another model is selected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onAction(DownloadedModelsAction.ConfirmDelete) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onAction(DownloadedModelsAction.DismissDelete) }) {
                    Text("Cancel")
                }
            }
        )
    }

    uiState.settingsModel?.let { model ->
        uiState.settingsForEdit?.let { settings ->
            ModelSettingsSheet(
                modelId = model.id,
                modelName = model.name,
                settings = settings,
                onSave = { viewModel.onAction(DownloadedModelsAction.SaveSettings(it)) },
                onDismiss = { viewModel.onAction(DownloadedModelsAction.DismissSettings) }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Downloaded models", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${uiState.models.size} models · ${formatBytes(uiState.totalSizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.models.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.size(12.dp))
                Text("No downloaded models", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Download a GGUF model to start chatting.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(16.dp))
                Button(onClick = onBrowseModels) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Browse models")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.models, key = { it.id }) { model ->
                    DownloadedModelCard(
                        model = model,
                        onToggle = { enabled ->
                            viewModel.onAction(DownloadedModelsAction.ToggleModel(model.id, enabled))
                        },
                        onSettings = {
                            viewModel.onAction(DownloadedModelsAction.OpenSettings(model))
                        },
                        onDelete = {
                            viewModel.onAction(DownloadedModelsAction.RequestDelete(model))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedModelCard(
    model: DownloadedModel,
    onToggle: (Boolean) -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        model.quantization.takeIf { it.isNotBlank() },
                        model.formattedSize.takeIf { it.isNotBlank() }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Model settings")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete model",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Switch(checked = model.isEnabled, onCheckedChange = onToggle)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        else -> "%.0f MB".format(mb)
    }
}
