package com.ogrchatai.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Controls the per-model "thinking" mode.
 *
 * Stored as a nullable string so settings written by older app versions (which
 * did not contain this field) keep loading safely with Gson.
 */
@Serializable
enum class ReasoningMode(val storageValue: String) {
    AUTO("auto"),
    ON("on"),
    OFF("off");

    companion object {
        fun fromStorage(value: String?): ReasoningMode =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}

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
    val customName: String? = null,
    val reasoningMode: String? = null
) {
    val effectiveReasoningMode: ReasoningMode
        get() = ReasoningMode.fromStorage(reasoningMode)

    companion object {
        fun default(modelId: String): ModelSettings = ModelSettings(modelId = modelId)
    }
}
