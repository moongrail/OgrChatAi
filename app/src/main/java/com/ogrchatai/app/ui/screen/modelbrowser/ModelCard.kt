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
import androidx.compose.material.icons.filled.Download
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
import com.ogrchatai.app.ui.theme.ErrorLight
import com.ogrchatai.app.ui.theme.SuccessLight
import com.ogrchatai.app.ui.viewmodel.DownloadState
import com.ogrchatai.app.util.ModelUtils

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModelCard(
    model: ModelInfo,
    downloadState: DownloadState?,
    onDownloadClick: () -> Unit,
    onModelClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = downloadState?.isDownloading == true
    val isComplete = downloadState?.isComplete == true
    val progress by animateFloatAsState(
        targetValue = downloadState?.progress ?: 0f,
        label = "download_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onModelClick),
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
                Column(modifier = Modifier.weight(1f)) {
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

                if (isComplete) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = SuccessLight.copy(alpha = 0.15f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Downloaded",
                                tint = SuccessLight,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                } else if (isDownloading) {
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
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                    Text(
                        text = model.formattedSize,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = isDownloading && progress > 0f,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            val quantTags = model.quantizationTags
            if (quantTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
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
                Spacer(modifier = Modifier.height(8.dp))
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
