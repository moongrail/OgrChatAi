package com.ogrchatai.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object ChatUtils {

    fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatTimeOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatDateOnly(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
            else -> formatTimestamp(timestamp)
        }
    }

    fun generateChatTitle(firstMessage: String): String {
        val cleaned = firstMessage
            .replace(Regex("[\\n\\r]+"), " ")
            .trim()

        if (cleaned.isEmpty()) return "New Chat"

        val words = cleaned.split("\\s+".toRegex())
        val title = if (words.size <= 6) {
            cleaned
        } else {
            words.take(6).joinToString(" ") + "..."
        }

        return title.take(Constants.MAX_MESSAGE_LENGTH).trim()
    }

    fun generateMessageId(): String {
        return UUID.randomUUID().toString()
    }

    fun generateConversationId(): String {
        return UUID.randomUUID().toString()
    }

    fun isSameDay(timestamp1: Long, timestamp2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = timestamp1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timestamp2 }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    fun isToday(timestamp: Long): Boolean {
        return isSameDay(timestamp, System.currentTimeMillis())
    }

    fun isYesterday(timestamp: Long): Boolean {
        val yesterday = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        return isSameDay(timestamp, yesterday)
    }

    fun truncateMessage(message: String, maxLength: Int = 100): String {
        if (message.length <= maxLength) return message
        return message.take(maxLength - 3) + "..."
    }

    fun stripMarkdown(text: String): String {
        return text
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("__(.*?)__"), "$1")
            .replace(Regex("_(.*?)_"), "$1")
            .replace(Regex("~~(.*?)~~"), "$1")
            .replace(Regex("`{3}(.*?)`{3}", RegexOption.DOT_MATCHES_ALL), "$1")
            .replace(Regex("`{1}(.*?)`{1}"), "$1")
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            .trim()
    }

    fun estimateReadingTime(text: String): String {
        val wordsPerMinute = 200
        val words = text.split("\\s+".toRegex()).size
        val minutes = (words.toDouble() / wordsPerMinute).coerceAtLeast(1.0).toInt()

        return when {
            minutes < 1 -> "Less than 1 min"
            minutes == 1 -> "1 min"
            else -> "$minutes min"
        }
    }

    fun countTokens(text: String): Int {
        return text.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
    }

    fun formatTokenCount(count: Int): String {
        return when {
            count < 1000 -> "$count tokens"
            count < 1000000 -> "${count / 1000}k tokens"
            else -> "${count / 1000000}M tokens"
        }
    }

    fun sanitizeInput(input: String): String {
        return input
            .replace(Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]"), "")
            .trim()
    }

    fun extractUrls(text: String): List<String> {
        val urlPattern = Regex(
            "(https?://)?(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}" +
                    "\\.[a-zA-Z0-9()]{1,6}\\b[-a-zA-Z0-9()@:%_+.~#?&/=]*"
        )
        return urlPattern.findAll(text).map { it.value }.toList()
    }

    fun extractCodeBlocks(text: String): List<CodeBlock> {
        val codeBlockPattern = Regex("```(\\w+)?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        return codeBlockPattern.findAll(text).map { match ->
            CodeBlock(
                language = match.groupValues[1].ifEmpty { null },
                code = match.groupValues[2].trim()
            )
        }.toList()
    }

    fun formatConversationSummary(messageCount: Int, lastActivity: Long): String {
        val timeStr = formatRelativeTime(lastActivity)
        return "$messageCount messages · Last active $timeStr"
    }

    fun shouldAutoTitle(messageCount: Int, currentTitle: String): Boolean {
        return messageCount == 1 && currentTitle == "New Chat"
    }

    data class CodeBlock(
        val language: String?,
        val code: String
    )
}
