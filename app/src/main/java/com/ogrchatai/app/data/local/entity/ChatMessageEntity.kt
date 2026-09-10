package com.ogrchatai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chat_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["chat_id"])]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "chat_id")
    val chatId: Long,

    val role: String,

    val content: String,

    @ColumnInfo(name = "encrypted_content")
    val encryptedContent: String? = null,

    @ColumnInfo(name = "content_nonce")
    val contentNonce: String? = null,

    val timestamp: Long = System.currentTimeMillis(),

    val attachments: String = "[]"
)
