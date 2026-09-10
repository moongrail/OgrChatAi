package com.ogrchatai.app.ui.screen.modelbrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ogrchatai.app.domain.model.ModelInfo
import com.ogrchatai.app.ui.components.ModelChip
import com.ogrchatai.app.ui.viewmodel.DownloadState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelCard(
    model: ModelInfo,
    downloadState: DownloadState?,
    isDownloaded: Boolean,
    deviceRamMb: Long,
    onDownloadClick: () -> Unit,
    onCancelDownload: () -> Unit = {},
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadState?.isDownloading == true
    val isComplete = downloadState?.isComplete == true || isDownloaded
    val progress by animateFloatAsState(
        targetValue = downloadState?.progress ?: 0f,
        label = "download_progress"
    )

    val modelSizeMb = model.totalSize / (1024 * 1024)
    val fitsInRam = if (deviceRamMb > 0 && modelSizeMb > 0) {
        modelSizeMb < deviceRamMb * 0.75
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onModelClick)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                when {
                    isComplete -> {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Downloaded",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                    isDownloading -> {
                        Box(
                            modifier = Modifier.size(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.size(36.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            IconButton(
                                onClick = onCancelDownload,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    icon = Icons.Filled.CloudDownload,
                    value = model.formattedDownloads
                )
                StatItem(
                    icon = Icons.Filled.ThumbUp,
                    value = model.likes.toString()
                )
                if (model.totalSize > 0) {
                    StatItem(
                        icon = Icons.Filled.Memory,
                        value = model.formattedSize
                    )
                }
            }

            if (fitsInRam == false && modelSizeMb > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Memory,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Model ~${modelSizeMb}MB may exceed available RAM (${deviceRamMb}MB)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (fitsInRam == true && modelSizeMb > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Filled.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Compatible with your device (${modelSizeMb}MB)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading && progress > 0f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val speed = downloadState?.speedBytesPerSec ?: 0L
                        if (speed > 0) {
                            val speedKb = speed / 1024
                            Text(
                                text = if (speedKb > 1024) "${speedKb / 1024} MB/s" else "$speedKb KB/s",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            val quantTags = model.quantizationTags
            if (quantTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quantTags.forEach { tag ->
                        ModelChip(
                            label = tag,
                            color = getQuantizationColor(tag)
                        )
                    }
                }
            }

            if (model.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    model.tags.take(5).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getQuantizationColor(quantization: String): androidx.compose.ui.graphics.Color {
    return when {
        quantization.contains("Q4") -> androidx.compose.ui.graphics.Color(0xFF818CF8)
        quantization.contains("Q5") -> androidx.compose.ui.graphics.Color(0xFFA78BFA)
        quantization.contains("Q8") -> androidx.compose.ui.graphics.Color(0xFF2DD4BF)
        quantization.contains("F16") -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
        quantization.contains("F32") -> androidx.compose.ui.graphics.Color(0xFFEF4444)
        quantization.contains("GGUF") -> androidx.compose.ui.graphics.Color(0xFF22C55E)
        else -> androidx.compose.ui.graphics.Color(0xFF6B7280)
    }
}
