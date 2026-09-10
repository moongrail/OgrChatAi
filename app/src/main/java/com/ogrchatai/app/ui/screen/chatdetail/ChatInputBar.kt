package com.ogrchatai.app.ui.screen.chatdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ogrchatai.app.domain.model.Attachment

@Composable
fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    isGenerating: Boolean,
    attachments: List<Attachment> = emptyList(),
    onRemoveAttachment: (String) -> Unit = {},
    onAttachFile: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            AnimatedVisibility(visible = attachments.isNotEmpty()) {
                AttachmentPreview(
                    attachments = attachments,
                    onRemove = onRemoveAttachment,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onAttachFile,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AttachFile,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp),
                    placeholder = {
                        Text(
                            text = "Type a message...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    enabled = !isGenerating
                )

                if (isGenerating) {
                    FilledIconButton(
                        onClick = onStopGeneration,
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generation",
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp),
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank())
                                MaterialTheme.colorScheme.onPrimary
                            else
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}
