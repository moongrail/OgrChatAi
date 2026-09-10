package com.ogrchatai.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ModelSettings(
    val modelId: String,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val repeatPenalty: Float = 1.1f,
    val contextSize: Int = 4096,
    val systemPrompt: String = "",
    val isDefault: Boolean = false,
    val customName: String? = null
) {
    companion object {
        fun default(modelId: String): ModelSettings = ModelSettings(modelId = modelId)
    }
}
