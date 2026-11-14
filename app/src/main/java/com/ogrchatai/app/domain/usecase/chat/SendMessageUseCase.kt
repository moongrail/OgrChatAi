package com.ogrchatai.app.domain.usecase.chat

import android.util.Log
import com.ogrchatai.app.data.ml.InferenceEngine
import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.MessageRole
import com.ogrchatai.app.domain.model.ReasoningMode
import com.ogrchatai.app.domain.model.SendMessageResponse
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.util.ContextInput
import com.ogrchatai.app.util.ConversationContextBuilder
import com.ogrchatai.app.util.TokenEstimator
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
        val reasoningMode = modelSettings.effectiveReasoningMode
        val isQwen3 = isQwen3Model(usedModelId)
        val effectiveSystemPrompt = buildSystemPrompt(
            configuredPrompt = modelSettings.systemPrompt.ifBlank {
                config.systemPrompt.ifBlank {
                    com.ogrchatai.app.domain.model.GenerationConfig.DEFAULT_SYSTEM_PROMPT
                }
            },
            reasoningMode = reasoningMode,
            isQwen3 = isQwen3
        )

        val outputReserve = modelSettings.maxTokens.coerceAtMost(modelSettings.contextSize / 2)
        val effectiveContextSize = modelSettings.contextSize.coerceIn(1024, 8192)
        val promptBudget = (
            effectiveContextSize -
                outputReserve -
                TokenEstimator.estimate(effectiveSystemPrompt) -
                PROMPT_OVERHEAD_TOKENS
            ).coerceAtLeast(MIN_PROMPT_TOKENS)

        // The latest user turn is sent separately. Older turns are compressed into
        // a compact context seed that is only used when the KV cache is reset.
        // Keep the seed from ending with an assistant message: tiny models often
        // treat that as a reply to continue and copy it instead of answering the
        // new user turn.
        val priorMessages = messages
            .dropLast(1)
            .dropLastWhile { it.role == MessageRole.ASSISTANT }
        val contextInputs = priorMessages.map { message ->
            val formatted = formatMessageWithAttachments(message)
            ContextInput(
                message = message,
                formatted = formatted,
                tokens = TokenEstimator.estimate(formatted)
            )
        }
        val context = ConversationContextBuilder.build(
            messages = contextInputs,
            currentQuery = content,
            budgetTokens = promptBudget,
            memoryClearedAt = chat.memoryClearedAt
        )
        val userPrompt = buildCurrentUserPrompt(
            content = content,
            attachments = attachments,
            reasoningMode = reasoningMode,
            isQwen3 = isQwen3
        )

        val genConfig = InferenceEngine.GenerationConfig(
            maxTokens = modelSettings.maxTokens,
            temperature = modelSettings.temperature,
            topP = modelSettings.topP,
            topK = modelSettings.topK,
            repeatPenalty = modelSettings.repeatPenalty,
            stopSequences = config.stopSequences,
            expectImplicitReasoning = isQwen3 && reasoningMode != ReasoningMode.OFF,
            contextSize = effectiveContextSize
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

                trySend(Result.success(SendMessageResponse(isStreaming = true, token = "")))

                val stream = inferenceEngine.generateTextStream(
                    modelId = usedModelId,
                    prompt = userPrompt,
                    systemPrompt = effectiveSystemPrompt,
                    config = genConfig,
                    conversationKey = "chat:$chatId",
                    contextSeed = context.contextText
                )
                val sb = StringBuilder()

                stream.collect { token ->
                    if (token.isReasoning) {
                        if (reasoningMode != ReasoningMode.OFF && token.text.isNotEmpty()) {
                            trySend(
                                Result.success(
                                    SendMessageResponse(
                                        isStreaming = true,
                                        reasoningToken = token.text
                                    )
                                )
                            )
                        }
                        return@collect
                    }
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

    private fun buildCurrentUserPrompt(
        content: String,
        attachments: List<Attachment>,
        reasoningMode: ReasoningMode,
        isQwen3: Boolean
    ): String {
        val sb = StringBuilder(content.trim())
        attachments.forEach { attachment ->
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append("[Attached file: ")
                .append(attachment.fileName)
                .append(" | ")
                .append(attachment.mimeType)
                .append(" | ")
                .append(attachment.formattedSize)
                .append("]")

            val attachmentContent = attachment.content?.trim()
            if (!attachmentContent.isNullOrBlank()) {
                sb.append("\n<<<FILE CONTENT START>>>\n")
                    .append(attachmentContent.take(MAX_ATTACHMENT_TEXT_CHARS))
                if (attachmentContent.length > MAX_ATTACHMENT_TEXT_CHARS) {
                    sb.append("\n[... file content truncated]")
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

        if (sb.isBlank()) {
            return "Please review the attached file."
        }

        if (isQwen3) {
            when (reasoningMode) {
                ReasoningMode.OFF -> sb.append("\n/no_think")
                ReasoningMode.ON -> sb.append("\n/think")
                ReasoningMode.AUTO -> Unit
            }
        }
        return sb.toString()
    }

    private fun buildSystemPrompt(
        configuredPrompt: String,
        reasoningMode: ReasoningMode,
        isQwen3: Boolean
    ): String {
        return when (reasoningMode) {
            ReasoningMode.AUTO -> configuredPrompt
            ReasoningMode.ON -> configuredPrompt.trimEnd() + "\n\n" +
                "Think through the problem carefully before answering. " +
                "Put your internal reasoning inside <think>...</think> and then give " +
                "the final answer after the closing tag."
            ReasoningMode.OFF -> configuredPrompt.trimEnd() + "\n\n" +
                "Answer directly and concisely. Do not produce a thinking or reasoning " +
                "section, and do not use <think> tags." +
                if (isQwen3) " Follow the /no_think instruction." else ""
        }
    }

    private fun isQwen3Model(modelId: String): Boolean {
        val normalized = modelId.lowercase()
        return normalized.contains("qwen3") ||
            normalized.contains("qwen-3") ||
            normalized.contains("qwen_3")
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
private const val MAX_ATTACHMENT_TEXT_CHARS = 20_000
private const val MIN_PROMPT_TOKENS = 512
private const val PROMPT_OVERHEAD_TOKENS = 64
