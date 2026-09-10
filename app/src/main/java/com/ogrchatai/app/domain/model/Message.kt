package com.ogrchatai.app.domain.model

import android.net.Uri

data class Message(
    val id: String,
    val chatId: String,
    val content: String,
    val role: Role,
    val timestamp: Long = System.currentTimeMillis(),
    val attachments: List<String> = emptyList(),
    val isPartial: Boolean = false
) {
    enum class Role {
        USER,
        ASSISTANT,
        SYSTEM
    }
}

data class MessageAttachment(
    val id: String,
    val uri: Uri,
    val name: String,
    val mimeType: String
)

data class SendMessageResponse(
    val isStreaming: Boolean = false,
    val token: String = "",
    val isComplete: Boolean = false,
    val fullContent: String = "",
    val messageId: String? = null,
    val isError: Boolean = false,
    val errorMessage: String? = null
)
