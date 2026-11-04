package com.ogrchatai.app.ui.screen.chatdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ogrchatai.app.R
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
    isListening: Boolean = false,
    isVoiceProcessing: Boolean = false,
    isAttaching: Boolean = false,
    onVoiceClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val canSend = inputText.isNotBlank() || attachments.isNotEmpty()
    val voiceBusy = isListening || isVoiceProcessing

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

            if (isAttaching) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.chat_attachment_processing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onAttachFile,
                    modifier = Modifier.size(40.dp),
                    enabled = !isGenerating && !isAttaching && !voiceBusy
                ) {
                    if (isAttaching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = stringResource(R.string.chat_attach),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp),
                    placeholder = {
                        Text(
                            text = when {
                                isListening -> stringResource(R.string.chat_voice_listening)
                                isVoiceProcessing -> stringResource(R.string.chat_voice_processing)
                                else -> stringResource(R.string.chat_input_hint)
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4,
                    enabled = !isGenerating && !voiceBusy
                )

                when {
                    isVoiceProcessing -> {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(48.dp),
                            enabled = false
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    isListening -> {
                        FilledIconButton(
                            onClick = onVoiceClick,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = stringResource(R.string.chat_voice_stop)
                            )
                        }
                    }

                    else -> {
                        IconButton(
                            onClick = onVoiceClick,
                            modifier = Modifier.size(48.dp),
                            enabled = !isGenerating && !isAttaching
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = stringResource(R.string.chat_voice_input),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

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
                            contentDescription = stringResource(R.string.chat_stop),
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                } else {
                    FilledIconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp),
                        enabled = canSend && !voiceBusy && !isAttaching
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringResource(R.string.chat_send),
                            tint = if (canSend && !voiceBusy && !isAttaching)
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
