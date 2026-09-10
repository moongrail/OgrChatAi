package com.ogrchatai.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HuggingFaceModel(
    val id: String,
    val modelId: String = "",
    val author: String = "",
    val sha: String = "",
    val lastModified: String = "",
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val siblings: List<HuggingFaceModelFile> = emptyList(),
    val cardData: HuggingFaceCardData? = null,
    val downloads: Int = 0,
    val likes: Int = 0
)

@Serializable
data class HuggingFaceModelFile(
    val filename: String,
    val size: Long? = null,
    val lfs: HuggingFaceLfs? = null
)

@Serializable
data class HuggingFaceLfs(
    val size: Long = 0,
    val sha256: String = ""
)

@Serializable
data class HuggingFaceCardData(
    val language: String? = null,
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
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val siblings: List<HuggingFaceModelFile> = emptyList(),
    val downloads: Int = 0,
    val likes: Int = 0,
    val cardData: HuggingFaceCardData? = null
)
