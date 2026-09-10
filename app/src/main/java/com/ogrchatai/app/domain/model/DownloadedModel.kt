package com.ogrchatai.app.domain.model

import java.io.File

data class DownloadedModel(
    val id: String,
    val name: String,
    val repository: String,
    val fileName: String,
    val fileSize: Long,
    val quantization: String = "",
    val downloadedAt: Long = System.currentTimeMillis(),
    val file: File? = null
) {
    val formattedSize: String
        get() {
            val kb = fileSize / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                else -> "%.1f KB".format(kb)
            }
        }
}
