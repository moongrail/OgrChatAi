package com.ogrchatai.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val uri: String,
    val content: String? = null
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isDocument: Boolean
        get() = mimeType.startsWith("application/") || mimeType.startsWith("text/")

    val formattedSize: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> "%.1f MB".format(mb)
                else -> "%.1f KB".format(kb)
            }
        }

    sealed class Type(val mimeType: String) {
        data object Image : Type("image/*")
        data object Text : Type("text/plain")
        data object Pdf : Type("application/pdf")
        data object Code : Type("text/plain")
    }
}
