package com.ogrchatai.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HuggingFaceModel(
    val id: String,
    val modelId: String = id,
    val name: String = modelId.substringAfter('/'),
    val author: String = modelId.substringBefore('/'),
    val downloads: Long = 0,
    val likes: Int = 0,
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    @SerialName("lastModified")
    val lastModified: String? = null,
    val siblings: List<Sibling> = emptyList()
) {
    val isGGUF: Boolean
        get() = siblings.any { it.rfilename.endsWith(".gguf") }

    val ggufFiles: List<Sibling>
        get() = siblings.filter { it.rfilename.endsWith(".gguf") }

    val formattedDownloads: String
        get() {
            val dl = downloads.toDouble()
            return when {
                dl >= 1_000_000 -> "%.1fM".format(dl / 1_000_000)
                dl >= 1_000 -> "%.1fK".format(dl / 1_000)
                else -> downloads.toString()
            }
        }
}

@Serializable
data class Sibling(
    val filename: String = "",
    val rfilename: String = filename,
    val size: Long? = null
)
