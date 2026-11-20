package com.ogrchatai.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class HuggingFaceModel(
    val id: String,
    val modelId: String = "",
    val author: String = "",
    val sha: String = "",
    val lastModified: String = "",
    // Gson bypasses Kotlin default values when a field is omitted. The search
    // endpoint frequently omits tags/siblings, so these must be nullable and
    // mapped with orEmpty() instead of trusting the declared default.
    val tags: List<String>? = null,
    @SerializedName("pipeline_tag")
    val pipelineTag: String? = null,
    val siblings: List<HuggingFaceModelFile>? = null,
    val cardData: HuggingFaceCardData? = null,
    val downloads: Int = 0,
    val likes: Int = 0
)

@Serializable
data class HuggingFaceModelFile(
    @SerializedName("rfilename")
    val rfilename: String? = null,
    @Transient
    val filename: String? = null,
    val size: Long? = null,
    val lfs: HuggingFaceLfs? = null
) {
    val name: String
        get() = rfilename?.takeIf { it.isNotBlank() } ?: filename.orEmpty()
}

@Serializable
data class HuggingFaceLfs(
    val size: Long? = null,
    val sha256: String? = null
)

@Serializable
data class HuggingFaceCardData(
    val language: List<String>? = null,
    val license: String? = null,
    val tags: List<String> = emptyList(),
    val modelId: String? = null,
    @SerialName("base_model")
    val baseModel: String? = null
)

@Serializable
data class HuggingFaceModelSearchResponse(
    val models: List<HuggingFaceModel> = emptyList()
)

@Serializable
data class HuggingFaceModelInfoResponse(
    val id: String,
    val modelId: String = "",
    val author: String = "",
    val tags: List<String>? = null,
    @SerializedName("pipeline_tag")
    val pipelineTag: String? = null,
    val siblings: List<HuggingFaceModelFile>? = null,
    val downloads: Int = 0,
    val likes: Int = 0,
    val cardData: HuggingFaceCardData? = null
)
