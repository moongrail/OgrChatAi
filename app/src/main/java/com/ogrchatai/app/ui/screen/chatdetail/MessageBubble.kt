package com.ogrchatai.app.ui.screen.chatdetail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.domain.model.Message
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    onEdit: (String) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = LocalContext.current
    val isUser = message.role == Message.Role.USER
    val isSystem = message.role == Message.Role.SYSTEM
    var previewImageUri by remember { mutableStateOf<String?>(null) }
    var showActions by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editText by remember(message.content) { mutableStateOf(message.content) }
    val formattedTime = remember(message.timestamp) { formatMessageTime(message.timestamp) }

    val bubbleColor = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isSystem -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when {
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        isSystem -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    val bubbleShape = RoundedCornerShape(
        topStart = if (isUser) 16.dp else 4.dp,
        topEnd = if (isUser) 4.dp else 16.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isUser) 48.dp else 0.dp),
        contentAlignment = alignment
    ) {
        Box {
            Surface(
                shape = bubbleShape,
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showActions = true }
                    )
            ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isUser) {
                    Text(
                        text = when (message.role) {
                            Message.Role.ASSISTANT -> "Assistant"
                            Message.Role.SYSTEM -> "System"
                            else -> ""
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }

                if (message.content.isNotBlank()) {
                    if (isUser || message.isPartial) {
                        // Re-parsing the entire answer as Markdown on every
                        // streaming chunk makes long responses progressively
                        // slower. Partial messages are rendered as plain text;
                        // the final saved message is parsed once.
                        Text(
                            text = message.content,
                            style = MaterialTheme.typography.bodyLarge,
                            color = textColor
                        )
                    } else {
                        RenderMarkdownText(
                            text = message.content,
                            textColor = textColor
                        )
                    }
                }

                if (message.attachments.isNotEmpty()) {
                    MessageAttachments(
                        attachments = message.attachments,
                        textColor = textColor,
                        onImageClick = { uri -> previewImageUri = uri }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isPartial) {
                        Text(
                            text = "partial",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.5f)
                    )
                }
            }
            }

            DropdownMenu(
                expanded = showActions,
                onDismissRequest = { showActions = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = {
                        showActions = false
                        copyToClipboard(context, message.content)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
                )
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            showActions = false
                            editText = message.content
                            showEditDialog = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        showActions = false
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit message") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 8
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editText.isNotBlank()) {
                            onEdit(editText)
                            showEditDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete message?") },
            text = { Text("This message and its attachment references will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    previewImageUri?.let { uri ->
        Dialog(onDismissRequest = { previewImageUri = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable { previewImageUri = null }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Image preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun MessageAttachments(
    attachments: List<Attachment>,
    textColor: Color,
    onImageClick: (String) -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        attachments.forEach { attachment ->
            if (attachment.isImage) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(attachment.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onImageClick(attachment.uri) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = textColor.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openAttachment(context, attachment) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = textColor.copy(alpha = 0.8f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = attachment.fileName,
                                style = MaterialTheme.typography.labelMedium,
                                color = textColor,
                                maxLines = 1
                            )
                            Text(
                                text = attachment.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openAttachment(context: Context, attachment: Attachment) {
    try {
        val parsed = Uri.parse(attachment.uri)
        val contentUri = when (parsed.scheme) {
            "file" -> {
                val file = parsed.path?.let(::File)
                    ?: throw IllegalArgumentException("Invalid file path")
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }

            else -> parsed
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                contentUri,
                attachment.mimeType.takeIf { it.isNotBlank() } ?: "*/*"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open the file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun RenderMarkdownText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text) {
        markdownCache.get(text) ?: parseMarkdown(text).also { markdownCache.put(text, it) }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        modifier = modifier
    )
}

private fun parseMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && !text.startsWith("**", end)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("```", i) -> {
                val end = text.indexOf("```", i + 3)
                if (end != -1) {
                    val code = text.substring(i + 3, end).trim()
                    append("\n")
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = Color.Black.copy(alpha = 0.1f)
                        )
                    ) {
                        append(code)
                    }
                    append("\n")
                    i = end + 3
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = Color.Black.copy(alpha = 0.1f)
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Message", text)
    clipboard.setPrimaryClip(clip)
}

private fun formatMessageTime(timestamp: Long): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(TIME_FORMATTER)
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val markdownCache = LruCache<String, AnnotatedString>(32)
