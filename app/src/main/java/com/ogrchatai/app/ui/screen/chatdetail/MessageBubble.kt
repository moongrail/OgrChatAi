package com.ogrchatai.app.ui.screen.chatdetail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ogrchatai.app.domain.model.Message
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isUser = message.role == Message.Role.USER
    val isSystem = message.role == Message.Role.SYSTEM

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
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        copyToClipboard(context, message.content)
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    }
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

                if (isUser) {
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

                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun RenderMarkdownText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = buildAnnotatedString {
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
                text[i] == '\n' -> {
                    append("\n")
                    i++
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }

    Text(
        text = annotatedString,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor,
        modifier = modifier
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Message", text)
    clipboard.setPrimaryClip(clip)
}

private fun formatMessageTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
