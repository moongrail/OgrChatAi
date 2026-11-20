package com.ogrchatai.app.domain.model

/**
 * Observable state for a model file download.
 *
 * The download itself is owned by an application-scoped manager, so this state
 * survives navigation away from the model browser.
 */
data class ModelDownloadState(
    val modelId: String = "",
    val modelName: String = "",
    val progress: Float = 0f,
    val isDownloading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L
)
