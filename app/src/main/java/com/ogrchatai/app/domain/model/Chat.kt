package com.ogrchatai.app.domain.model

data class Chat(
    val id: Long = 0,
    val title: String,
    val modelId: String,
    val folderId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
