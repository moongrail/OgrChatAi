package com.ogrchatai.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_models")
data class DownloadedModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "hf_model_id")
    val hfModelId: String,

    val name: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,

    val quantization: String,

    @ColumnInfo(name = "downloaded_at")
    val downloadedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false
)
