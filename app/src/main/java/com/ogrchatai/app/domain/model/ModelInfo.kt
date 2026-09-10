package com.ogrchatai.app.domain.model

data class ModelInfo(
    val id: String,
    val modelId: String = id,
    val name: String = modelId.substringAfter('/'),
    val author: String = modelId.substringBefore('/'),
    val downloads: Long = 0,
    val likes: Int = 0,
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
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

    val totalSize: Long
        get() = siblings.sumOf { it.size ?: 0L }

    val formattedSize: String
        get() {
            val bytes = totalSize.toDouble()
            val mb = bytes / (1024.0 * 1024.0)
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> "%.1f GB".format(gb)
                mb >= 1.0 -> "%.0f MB".format(mb)
                else -> "%.0f KB".format(bytes / 1024.0)
            }
        }

    val quantizationTags: List<String>
        get() {
            val quants = mutableSetOf<String>()
            for (file in siblings) {
                val name = file.rfilename.lowercase()
                when {
                    name.contains("q4_k_m") -> quants.add("Q4_K_M")
                    name.contains("q4_0") -> quants.add("Q4_0")
                    name.contains("q4_1") -> quants.add("Q4_1")
                    name.contains("q5_k_m") -> quants.add("Q5_K_M")
                    name.contains("q5_0") -> quants.add("Q5_0")
                    name.contains("q5_1") -> quants.add("Q5_1")
                    name.contains("q8_0") -> quants.add("Q8_0")
                    name.contains("fp16") -> quants.add("F16")
                    name.contains("f16") -> quants.add("F16")
                    name.contains("fp32") -> quants.add("F32")
                    name.contains("f32") -> quants.add("F32")
                    name.endsWith(".gguf") -> quants.add("GGUF")
                }
            }
            return quants.toList()
        }

    fun toHuggingFaceModel(): HuggingFaceModel = HuggingFaceModel(
        id = id,
        modelId = modelId,
        name = name,
        author = author,
        downloads = downloads,
        likes = likes,
        tags = tags,
        pipelineTag = pipelineTag,
        lastModified = lastModified,
        siblings = siblings
    )
}

data class SearchResult(
    val models: List<ModelInfo>,
    val hasMore: Boolean
)
