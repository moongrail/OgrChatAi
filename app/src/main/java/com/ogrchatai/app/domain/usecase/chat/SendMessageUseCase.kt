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

        val prompt = buildPrompt(messages)

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

                val fullContent = sb.toString().trim()

                if (fullContent.isBlank()) {
                    trySend(Result.failure(SendMessageException("The model returned an empty response")))
                    close()
                    return@callbackFlow
                }

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

    private fun buildPrompt(messages: List<ChatMessage>): String {
        val recentMessages = messages.takeLast(MAX_HISTORY_MESSAGES)
        val blocks = recentMessages.map { formatMessageWithAttachments(it) }

        // Keep the newest messages when the prompt is too large for the model context.
        val selectedBlocks = mutableListOf<String>()
        var usedChars = 0
        for (block in blocks.asReversed()) {
            val remaining = MAX_PROMPT_CHARS - usedChars
            if (remaining <= 0) break

            val clipped = if (block.length > remaining) block.take(remaining) else block
            selectedBlocks.add(0, clipped)
            usedChars += clipped.length + 1
        }

        return selectedBlocks.joinToString("\n").trim()
    }

    private fun formatMessageWithAttachments(message: ChatMessage): String {
        val sb = StringBuilder()
        val roleLabel = when (message.role) {
            MessageRole.USER -> "User"
            MessageRole.ASSISTANT -> "Assistant"
            MessageRole.SYSTEM -> "System"
        }

        sb.append(roleLabel).append(": ").append(message.content.trim())

        message.attachments.forEach { attachment ->
            sb.append("\n[Attached file: ")
                .append(attachment.fileName)
                .append(" | ")
                .append(attachment.mimeType)
                .append(" | ")
                .append(attachment.formattedSize)
                .append("]")

            val content = attachment.content?.trim()
            if (!content.isNullOrBlank()) {
                sb.append("\n<<<FILE CONTENT START>>>\n")
                    .append(content.take(MAX_ATTACHMENT_TEXT_CHARS))
                if (content.length > MAX_ATTACHMENT_TEXT_CHARS) {
                    sb.append("\n… [file content truncated]")
                }
                sb.append("\n<<<FILE CONTENT END>>>")
            } else if (attachment.isImage) {
                sb.append(
                    "\n[Image attachment. This model works with text only, " +
                        "so the image itself is not available. Ask the user for a description if needed.]"
                )
            } else {
                sb.append(
                    "\n[Binary attachment. Text could not be extracted from this file; " +
                        "the file name and metadata are available to you.]"
                )
            }
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

class SendMessageException(message: String) : Exception(message)

private const val TAG = "SendMessageUseCase"
private const val MAX_HISTORY_MESSAGES = 30
private const val MAX_PROMPT_CHARS = 24_000
private const val MAX_ATTACHMENT_TEXT_CHARS = 20_000
