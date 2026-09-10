package com.ogrchatai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,

    @ColumnInfo(name = "color")
    val color: String? = null,

    @ColumnInfo(name = "icon")
    val icon: String? = null,

    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
