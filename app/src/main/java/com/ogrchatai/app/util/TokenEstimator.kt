package com.ogrchatai.app.util

import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.Message
import kotlin.math.ceil

/**
 * Lightweight, dependency-free token estimate used to trim prompts and show the
 * context meter. It is intentionally conservative: Cyrillic and CJK text produce
 * more tokens per character than Latin text in most sentencepiece vocabularies.
 */
object TokenEstimator {

    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var latin = 0
        var nonLatin = 0
        var other = 0
        text.forEach { ch ->
            when {
                ch.code < 128 && (ch.isLetterOrDigit() || ch == ' ' || ch in ".,!?;:'\"-()[]{}") -> latin++
                ch.isLetterOrDigit() -> nonLatin++
                else -> other++
            }
        }
        val tokens = latin / 4.0 + nonLatin / 1.9 + other / 3.0
        return ceil(tokens).toInt().coerceAtLeast(1)
    }

    fun estimateMessage(message: ChatMessage): Int {
        var total = estimate(message.content) + MESSAGE_OVERHEAD_TOKENS
        message.attachments.forEach { attachment ->
            total += estimate(attachment.fileName) + ATTACHMENT_OVERHEAD_TOKENS
            attachment.content?.let { total += estimate(it) }
        }
        return total
    }

    fun estimateMessages(messages: List<ChatMessage>): Int =
        messages.sumOf { estimateMessage(it) }

    fun estimateUiMessages(messages: List<Message>): Int =
        messages.sumOf { message ->
            estimate(message.content) + MESSAGE_OVERHEAD_TOKENS +
                message.attachments.sumOf { attachment ->
                    estimate(attachment.fileName) + ATTACHMENT_OVERHEAD_TOKENS +
                        (attachment.content?.let { estimate(it) } ?: 0)
                }
        }

    private const val MESSAGE_OVERHEAD_TOKENS = 6
    private const val ATTACHMENT_OVERHEAD_TOKENS = 8
}
