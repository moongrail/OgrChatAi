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
        get() = mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS

    val isDocument: Boolean
        get() = isTextBased || mimeType.startsWith("application/")

    val isTextBased: Boolean
        get() = mimeType.startsWith("text/") ||
            mimeType in TEXT_MIME_TYPES ||
            extension in TEXT_EXTENSIONS

    val formattedSize: String
        get() {
            if (size <= 0L) return "0 B"
            val kb = size / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.2f GB".format(gb)
                mb >= 1.0 -> "%.1f MB".format(mb)
                kb >= 1.0 -> "%.1f KB".format(kb)
                else -> "$size B"
            }
        }

    val extension: String
        get() = fileName.substringAfterLast('.', "").lowercase()

    sealed class Type(val mimeType: String) {
        data object Image : Type("image/*")
        data object Text : Type("text/plain")
        data object Pdf : Type("application/pdf")
        data object Code : Type("text/plain")
    }

    companion object {
        private val IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif"
        )

        private val TEXT_MIME_TYPES = setOf(
            "application/json",
            "application/xml",
            "application/javascript",
            "application/x-javascript",
            "application/x-yaml",
            "application/yaml",
            "application/csv",
            "application/x-sh",
            "application/sql",
            "application/x-httpd-php"
        )

        private val TEXT_EXTENSIONS = setOf(
            "txt", "text", "md", "markdown", "csv", "tsv", "json", "jsonl",
            "xml", "yaml", "yml", "html", "htm", "css", "scss", "less",
            "js", "jsx", "ts", "tsx", "kt", "kts", "java", "py", "rb", "php",
            "c", "h", "cpp", "hpp", "cc", "cs", "go", "rs", "swift", "sh",
            "bash", "zsh", "bat", "cmd", "ps1", "sql", "log", "ini", "cfg",
            "conf", "toml", "properties", "gradle", "pro", "gitignore", "env"
        )
    }
}
