package com.ogrchatai.app.domain.usecase.chat

import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.MessageRole
import com.ogrchatai.app.domain.model.SendMessageResponse
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.domain.repository.ModelRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val modelRepository: ModelRepository
) {
    suspend operator fun invoke(
        chatId: Long,
        content: String,
        modelId: String? = null,
        attachments: List<Attachment> = emptyList(),
        config: GenerationConfig = GenerationConfig.DEFAULT
    ): Flow<Result<SendMessageResponse>> {
        require(content.isNotBlank() || attachments.isNotEmpty()) { "Message content cannot be blank" }

        val userMessage = ChatMessage(
            chatId = chatId,
            role = MessageRole.USER,
            content = content,
            attachments = attachments
        )
        chatRepository.insertMessage(userMessage)

        val chat = chatRepository.getChatById(chatId).firstOrNull()
            ?: throw IllegalStateException("Chat not found")

        val messages = chatRepository.getMessages(chatId).firstOrNull().orEmpty()
        val chatHistory = messages.map { "${it.role.name}: ${it.content}" }

        val usedModelId = modelId ?: chat.modelId
        val modelPath = modelRepository.getModelPath(usedModelId)
            ?: throw IllegalStateException("Model not found: $usedModelId")

        val prompt = buildPrompt(chatHistory, config.systemPrompt)

        return callbackFlow {
            val sb = StringBuilder()
            trySend(Result.success(SendMessageResponse(token = "")))

            awaitClose {
                if (sb.isNotEmpty()) {
                    // Streaming completed
                }
            }
        }
    }

    private fun buildPrompt(chatHistory: List<String>, systemPrompt: String): String {
        val sb = StringBuilder()
        if (systemPrompt.isNotBlank()) {
            sb.appendLine("[INST] <<SYS>>")
            sb.appendLine(systemPrompt)
            sb.appendLine("<</SYS>>")
            sb.appendLine()
        }
        chatHistory.forEachIndexed { _, message ->
            sb.appendLine(message)
        }
        return sb.toString()
    }

    suspend fun saveAssistantMessage(chatId: Long, content: String): Long {
        val assistantMessage = ChatMessage(
            chatId = chatId,
            role = MessageRole.ASSISTANT,
            content = content
        )
        return chatRepository.insertMessage(assistantMessage)
    }
}
