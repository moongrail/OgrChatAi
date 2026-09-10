package com.ogrchatai.app.domain.model

data class ModelFilter(
    val quantization: String? = null,
    val architecture: String? = null,
    val minDownloads: Long = 0,
    val sortBy: SortBy = SortBy.DOWNLOADS
)

enum class SortBy(val label: String) {
    DOWNLOADS("Downloads"),
    LIKES("Likes"),
    RECENT("Recent")
}
