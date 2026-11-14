package com.ogrchatai.app.domain.model

data class Chat(
    val id: Long = 0,
    val title: String,
    val modelId: String,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Messages at or before this timestamp are no longer sent to the model.
     * They remain visible in the chat. Zero means no memory reset was requested.
     */
    val memoryClearedAt: Long = 0L
)
