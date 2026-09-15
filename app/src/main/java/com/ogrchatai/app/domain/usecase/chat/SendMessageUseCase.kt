package com.ogrchatai.app.domain.usecase.chat

import android.util.Log
import com.ogrchatai.app.data.ml.InferenceEngine
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
    private val modelRepository: ModelRepository,
    private val inferenceEngine: InferenceEngine
) {
    suspend operator fun invoke(
        chatId: Long,
        content: String,
        modelId: String? = null,
        attachments: List<Attachment> = emptyList(),
        config: GenerationConfig = GenerationConfig.DEFAULT
    ): Flow<Result<SendMessageResponse>> {
        Log.d(TAG, "invoke: chatId=$chatId, modelId=$modelId")
        require(content.isNotBlank() || attachments.isNotEmpty()) { "Message content cannot be blank" }

        val userMessage = ChatMessage(
            chatId = chatId,
            role = MessageRole.USER,
            content = content,
            attachments = attachments
        )
        chatRepository.insertMessage(userMessage)

        val chat = chatRepository.getChatById(chatId).firstOrNull()
        if (chat == null) {
            return kotlinx.coroutines.flow.flow {
                emit(Result.failure(SendMessageException("Chat not found")))
            }
        }

        val messages = chatRepository.getMessages(chatId).firstOrNull().orEmpty()
        val chatHistory = messages.map { "${it.role.name}: ${it.content}" }

        val usedModelId = modelId ?: chat.modelId
        val modelPath = modelRepository.getModelPath(usedModelId)
        if (modelPath == null) {
            return kotlinx.coroutines.flow.flow {
                emit(Result.failure(SendMessageException("Model not found: $usedModelId. Please download it first.")))
            }
        }

        val modelSettings = modelRepository.getModelSettingsOnce(usedModelId)
        val effectiveSystemPrompt = modelSettings.systemPrompt.ifBlank {
            config.systemPrompt.ifBlank { com.ogrchatai.app.domain.model.GenerationConfig.DEFAULT_SYSTEM_PROMPT }
        }

        val prompt = buildPrompt(chatHistory, effectiveSystemPrompt)

        val genConfig = InferenceEngine.GenerationConfig(
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP,
            topK = config.topK,
            repeatPenalty = config.repeatPenalty,
            stopSequences = config.stopSequences
        )

        return callbackFlow {
            try {
                val loadResult = inferenceEngine.loadModel(usedModelId, modelPath)
                if (loadResult.isFailure) {
                    trySend(Result.failure(SendMessageException(
                        "Failed to load model: ${loadResult.exceptionOrNull()?.message}"
                    )))
                    close()
                    return@callbackFlow
                }

                trySend(Result.success(SendMessageResponse(token = "")))

                val stream = inferenceEngine.generateTextStream(usedModelId, prompt, effectiveSystemPrompt, genConfig)
                val sb = StringBuilder()

                stream.collect { token ->
                    sb.append(token.text)
                    trySend(Result.success(
                        SendMessageResponse(
                            isStreaming = true,
                            token = token.text
                        )
                    ))
                }

                val fullContent = sb.toString()
                val assistantMsgId = saveAssistantMessage(chatId, fullContent)

                trySend(Result.success(
                    SendMessageResponse(
                        isComplete = true,
                        fullContent = fullContent,
                        messageId = assistantMsgId.toString()
                    )
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                trySend(Result.failure(SendMessageException(e.message ?: "Generation failed")))
            } finally {
                close()
            }
        }
    }

    private fun buildPrompt(chatHistory: List<String>, systemPrompt: String): String {
        val sb = StringBuilder()
        chatHistory.forEachIndexed { _, message ->
            sb.appendLine(message)
        }
        return sb.toString().trim()
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

class SendMessageException(message: String) : Exception(message)

private const val TAG = "SendMessageUseCase"
