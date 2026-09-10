package com.ogrchatai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chats",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["folder_id"])]
)
data class ChatEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
