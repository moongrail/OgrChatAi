package com.ogrchatai.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerationConfig(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 2048,
    val repeatPenalty: Float = 1.1f,
    val seed: Int? = null,
    val stopSequences: List<String> = emptyList(),
    val systemPrompt: String = ""
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant. Always respond in the same language the user writes in. Be concise, accurate, and natural."

        val DEFAULT = GenerationConfig(systemPrompt = DEFAULT_SYSTEM_PROMPT)

        val CREATIVE = GenerationConfig(
            temperature = 1.2f,
            topP = 0.95f,
            topK = 60
        )

        val PRECISE = GenerationConfig(
            temperature = 0.2f,
            topP = 0.7f,
            topK = 20
        )

        val BALANCED = GenerationConfig(
            temperature = 0.7f,
            topP = 0.9f,
            topK = 40
        )
    }
}
