package com.ogrchatai.app.domain.model

/** Observable download state for the optional Supertonic 3 voice model. */
data class NeuralVoiceStatus(
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val isExtracting: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val error: String? = null
)
