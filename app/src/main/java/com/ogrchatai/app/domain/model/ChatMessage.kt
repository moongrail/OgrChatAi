package com.ogrchatai.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: Long = 0,
    val chatId: Long,
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<Attachment> = emptyList()
)

@Serializable
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
