package com.ogrchatai.app.data.ml

import android.content.Context
import android.util.Log
import com.hazratbilal.aikit.core.AiKitEngine
import com.hazratbilal.aikit.core.InternalAiKitApi
import com.hazratbilal.aikit.core.engine.LlmEngine
import com.hazratbilal.aikit.core.model.LlmRequest
import com.ogrchatai.app.util.DeviceMemory
import com.ogrchatai.app.util.ReasoningTagParser
import com.ogrchatai.app.util.TokenEstimator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class InferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var aiKitEngine: AiKitEngine? = null
    private val isGenerating = AtomicBoolean(false)
    private val loadMutex = Mutex()
    private var currentModelId: String? = null
    private var activeGenerationJob: Job? = null

    private var activeConversationKey: String? = null
    private var conversationModelId: String? = null
    private var conversationSystemPrompt: String? = null
    private var conversationTokens: Int = 0
    private var conversationInitialized: Boolean = false
    private var conversationResetRequested: Boolean = false

    data class GenerationConfig(
        val maxTokens: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val stopSequences: List<String> = emptyList(),
        val expectImplicitReasoning: Boolean = false,
        val contextSize: Int = 8192,
        val stream: Boolean = true
    )

    data class StreamToken(
        val text: String,
        val tokenId: Int,
        val probability: Float,
        val isReasoning: Boolean = false
    )

    data class LoadedModel(
        val modelId: String,
        val filePath: String,
        val fileSizeBytes: Long,
        val loadedAt: Long = System.currentTimeMillis(),
        var lastUsedAt: Long = System.currentTimeMillis(),
        var isInUse: Boolean = false
    )

    private val loadedModels = mutableMapOf<String, LoadedModel>()

    suspend fun loadModel(modelId: String, filePath: String): Result<LoadedModel> =
        withContext(Dispatchers.IO) {
            loadMutex.withLock {
                try {
                    if (loadedModels.containsKey(modelId)) {
                        val existing = loadedModels[modelId]!!
                        existing.lastUsedAt = System.currentTimeMillis()
                        existing.isInUse = true
                        Log.d(TAG, "Model already loaded: $modelId")
                        return@withContext Result.success(existing)
                    }

                    val file = File(filePath)
                    if (!file.exists()) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Model file not found: $filePath")
                        )
                    }

                    // The RAM budget is advisory only: the user may explicitly
                    // install a model larger than the recommendation. Let the
                    // native loader try instead of rejecting the model here.
                    val advisoryLimit = DeviceMemory.maxModelBytes(context)
                    if (file.length() > advisoryLimit) {
                        Log.w(
                            TAG,
                            "Model is larger than the advisory memory budget " +
                                "(${file.length() / (1024L * 1024L)} MB > " +
                                "${advisoryLimit / (1024L * 1024L)} MB); loading anyway"
                        )
                    }

                    // Only one llama.cpp engine can be active at a time. If a chat model is
                    // already loaded, stop it and free its native memory before loading the
                    // model requested by voice-call (or vice versa).
                    if (currentModelId != null && currentModelId != modelId) {
                        Log.d(TAG, "Switching model: $currentModelId -> $modelId")
                        if (isGenerating.get()) {
                            cancelGeneration()
                        }
                        try {
                            aiKitEngine?.unloadModel()
                        } catch (_: Exception) {
                            // The old engine may already be released or be in
                            // an error state. ModelManager resets error states
                            // without touching the new model.
                        }
                        currentModelId = null
                        loadedModels.clear()
                        clearConversationState()
                    }

                    Log.d(TAG, "Loading model via AiKit: $modelId from $filePath (${file.length() / (1024 * 1024)}MB)")

                    // AiKitEngine is a thin wrapper around one process-wide
                    // LlamaCppEngine singleton. Reusing the wrapper keeps its
                    // ModelManager bookkeeping in sync with the native engine.
                    val engine = aiKitEngine ?: AiKitEngine.create(context).also {
                        aiKitEngine = it
                    }

                    val loadResult = loadModelBlocking(engine, filePath)

                    if (loadResult.isFailure) {
                        Log.e(TAG, "AiKit loadModel failed: ${loadResult.exceptionOrNull()?.message}")
                        runCatching { engine.unloadModel() }
                            .onFailure { Log.w(TAG, "Failed to reset AiKit after load error", it) }
                        currentModelId = null
                        loadedModels.clear()
                        clearConversationState()
                        return@withContext Result.failure(loadResult.exceptionOrNull()!!)
                    }

                    val loadedModel = LoadedModel(
                        modelId = modelId,
                        filePath = filePath,
                        fileSizeBytes = file.length()
                    )

                    loadedModels[modelId] = loadedModel
                    currentModelId = modelId
                    Log.i(TAG, "Model loaded successfully: $modelId")

                    Result.success(loadedModel)
                } catch (e: OutOfMemoryError) {
                    Log.e(TAG, "Out of memory loading model: $modelId", e)
                    runCatching { aiKitEngine?.unloadModel() }
                    currentModelId = null
                    loadedModels.clear()
                    clearConversationState()
                    Result.failure(RuntimeException("Out of memory: ${e.message}"))
                } catch (e: CancellationException) {
                    Log.d(TAG, "Model load cancelled: $modelId")
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load model: $modelId", e)
                    runCatching { aiKitEngine?.unloadModel() }
                    currentModelId = null
                    loadedModels.clear()
                    clearConversationState()
                    Result.failure(e)
                }
            }
        }

    private suspend fun loadModelBlocking(
        engine: AiKitEngine,
        filePath: String
    ): Result<Unit> = suspendCancellableCoroutine { continuation ->
        engine.loadModel(filePath, object : AiKitEngine.ModelStateListener {
            override fun onModelLoading() {
                Log.d(TAG, "AiKit: Model loading...")
            }

            override fun onModelLoaded() {
                Log.d(TAG, "AiKit: Model loaded")
                if (continuation.isActive) {
                    continuation.resume(Result.success(Unit))
                }
            }

            override fun onModelError(error: Throwable) {
                Log.e(TAG, "AiKit: Model load error", error)
                if (continuation.isActive) {
                    continuation.resume(Result.failure(error))
                }
            }

            override fun onModelUnloaded() {
                Log.d(TAG, "AiKit: Model unloaded")
            }
        })
    }

    /**
     * Clears the native KV cache and chat history without reloading the GGUF
     * file. AiKit exposes no public reset that keeps the model in memory, but
     * its native processSystemPrompt() performs exactly this reset. The private
     * flag only guards the public Kotlin entry point, so setting it temporarily
     * is safe and is covered by the AiKit ProGuard keep rules.
     */
    @OptIn(InternalAiKitApi::class)
    private suspend fun resetNativeConversation(engine: AiKitEngine, seed: String): Boolean {
        if (seed.isBlank()) return false
        return try {
            val manager = engine.modelManager
            val llmField = manager.javaClass.getDeclaredField("llmEngine").apply {
                isAccessible = true
            }
            val llm = llmField.get(manager) as? LlmEngine ?: return false
            if (llm.state.value !is LlmEngine.State.ModelReady) return false

            val readyField = llm.javaClass.getDeclaredField("_readyForSystemPrompt").apply {
                isAccessible = true
            }
            readyField.setBoolean(llm, true)
            llm.setSystemPrompt(seed)
            Log.d(TAG, "Native context reset in place (no model reload)")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "In-place context reset failed", e)
            false
        }
    }

    @OptIn(InternalAiKitApi::class)
    fun generateTextStream(
        modelId: String,
        prompt: String,
        systemPrompt: String = "You are a helpful assistant.",
        config: GenerationConfig = GenerationConfig(),
        conversationKey: String = "default",
        contextSeed: String = "",
        forceReset: Boolean = false
    ): Flow<StreamToken> = flow {
        val engine = aiKitEngine
            ?: throw IllegalStateException("Model not loaded: $modelId")

        if (!isGenerating.compareAndSet(false, true)) {
            throw IllegalStateException("Another generation is in progress")
        }

        val generationJob = currentCoroutineContext()[Job]
        activeGenerationJob = generationJob

        loadedModels[modelId]?.let {
            it.lastUsedAt = System.currentTimeMillis()
            it.isInUse = true
        }

        val contextSize = config.contextSize.coerceIn(MIN_CONTEXT_SIZE, MAX_CONTEXT_SIZE)
        val key = conversationKey.ifBlank { "default:$modelId" }
        val projectedTokens = conversationTokens + TokenEstimator.estimate(prompt)
        val sessionChanged = !conversationInitialized ||
            activeConversationKey != key ||
            conversationModelId != modelId ||
            conversationSystemPrompt != systemPrompt
        val overBudget = projectedTokens > (contextSize * COMPACT_AT_RATIO).toInt()
        val needsReset = forceReset || conversationResetRequested || sessionChanged || overBudget
        val requestPrompt = if (needsReset && contextSeed.isNotBlank()) {
            // Put the boundary instruction in the current user turn as well. The
            // model follows the closest, most specific instruction much more
            // reliably than a rule buried in a long system prompt.
            buildString {
                appendLine("[New user message]")
                appendLine(prompt.trim())
                appendLine("[End of new user message]")
                appendLine()
                append(CURRENT_TURN_ONLY_RULES)
            }
        } else {
            prompt
        }

        var appliedSystemPrompt: String? = null
        if (needsReset) {
            val seed = if (contextSeed.isBlank()) {
                systemPrompt.trim()
            } else {
                buildString {
                    append(systemPrompt.trim())
                    appendLine()
                    appendLine()
                    append(contextSeed.trim())
                    appendLine()
                    appendLine()
                    append(BACKGROUND_ONLY_RULES)
                }.trim()
            }
            if (conversationInitialized && engine.isModelLoaded()) {
                Log.d(TAG, "Compacting conversation context for $key")
                // processSystemPrompt() already clears the native KV cache
                // and the chat history. Calling ModelManager.resetConversation()
                // here would unload and reload the whole multi-gigabyte model
                // just to clear the cache, causing a long freeze and a second
                // native load. Use the cheap reset when possible and fall back
                // to a reload only if AiKit's private flag cannot be reached.
                val resetInPlace = resetNativeConversation(engine, seed)
                if (!resetInPlace) {
                    Log.w(TAG, "In-place context reset unavailable; reloading model")
                    engine.modelManager.resetConversation()
                }
            }
            appliedSystemPrompt = seed.ifBlank { null }
            activeConversationKey = key
            conversationModelId = modelId
            conversationSystemPrompt = systemPrompt
            conversationTokens = TokenEstimator.estimate(seed) +
                TokenEstimator.estimate(requestPrompt) +
                CHAT_TEMPLATE_OVERHEAD_TOKENS
            conversationInitialized = true
            conversationResetRequested = false
            Log.d(
                TAG,
                "Seeded conversation $key: ~$conversationTokens tokens " +
                    "(seed=${TokenEstimator.estimate(contextSeed)})"
            )
        } else {
            conversationTokens += TokenEstimator.estimate(prompt) + CHAT_TEMPLATE_OVERHEAD_TOKENS
        }

        var tokenCount = 0
        val responseBuilder = StringBuilder()
        val reasoningParser = ReasoningTagParser(
            expectImplicitReasoning = config.expectImplicitReasoning
        )

        suspend fun emitParsedChunks(chunks: List<ReasoningTagParser.Chunk>) {
            chunks.forEach { chunk ->
                if (chunk.text.isEmpty()) return@forEach
                emit(
                    StreamToken(
                        text = chunk.text,
                        tokenId = tokenCount,
                        probability = 1.0f,
                        isReasoning = chunk.isReasoning
                    )
                )
            }
        }

        try {
            // The wrapper sends every prompt as a single user turn and applies the
            // model's chat template. Keeping the previous turns inside that user
            // turn made small models repeat the last answer. We therefore send only
            // the new user message on each turn and let llama.cpp's KV cache hold
            // the dialogue. When the cache approaches the context limit (or the
            // chat/memory changes), the cache is reset and a compressed context is
            // supplied through the system prompt instead.
            val request = LlmRequest(
                prompt = requestPrompt,
                systemPrompt = appliedSystemPrompt,
                maxTokens = config.maxTokens.coerceAtLeast(1),
                temperature = config.temperature.coerceIn(0f, 2f)
            )

            engine.modelManager.generateStreaming(request).collect { token ->
                tokenCount++
                responseBuilder.append(token)
                emitParsedChunks(reasoningParser.process(token))
            }
            emitParsedChunks(reasoningParser.finish())

            conversationTokens += TokenEstimator.estimate(responseBuilder.toString())
            Log.d(TAG, "Generation complete: $tokenCount tokens, context ~$conversationTokens")
        } finally {
            isGenerating.set(false)
            loadedModels[modelId]?.isInUse = false
            if (activeGenerationJob === generationJob) {
                activeGenerationJob = null
            }
        }
    }.flowOn(Dispatchers.Default)

    /**
     * Marks the current conversation cache as stale, for example after the user
     * clears memory or edits/deletes an earlier message.
     */
    fun invalidateConversation(key: String? = null) {
        if (key == null || activeConversationKey == key) {
            conversationResetRequested = true
            conversationTokens = 0
            Log.d(TAG, "Conversation cache invalidated for ${key ?: activeConversationKey}")
        }
    }

    private fun clearConversationState() {
        activeConversationKey = null
        conversationModelId = null
        conversationSystemPrompt = null
        conversationTokens = 0
        conversationInitialized = false
        conversationResetRequested = false
    }

    fun cancelGeneration() {
        // Do not clear isGenerating here. The flow's finally block clears it
        // after the native loop observes cancellation, which prevents a new
        // request from racing a still-running llama.cpp generation.
        activeGenerationJob?.cancel()
        activeGenerationJob = null
    }

    fun unloadModel(modelId: String) {
        if (currentModelId == modelId) {
            runCatching { aiKitEngine?.unloadModel() }
                .onFailure { Log.w(TAG, "Failed to unload model $modelId", it) }
            currentModelId = null
            clearConversationState()
        }
        loadedModels.remove(modelId)
        Log.i(TAG, "Model unloaded: $modelId")
    }

    fun unloadAllModels() {
        if (isGenerating.get()) {
            cancelGeneration()
        }
        try {
            aiKitEngine?.unloadModel()
        } catch (_: Exception) {
            // Ignore unload errors during shutdown. Keep the wrapper: its
            // ModelManager is reused for the next load.
        }
        currentModelId = null
        loadedModels.clear()
        clearConversationState()
        Log.i(TAG, "All models unloaded")
    }

    fun isModelLoaded(modelId: String): Boolean = loadedModels.containsKey(modelId)

    fun isGenerating(): Boolean = isGenerating.get()

    companion object {
        private const val TAG = "InferenceEngine"
        private const val MIN_CONTEXT_SIZE = 1024
        private const val MAX_CONTEXT_SIZE = 8192
        private const val COMPACT_AT_RATIO = 0.68
        private const val CHAT_TEMPLATE_OVERHEAD_TOKENS = 32

        /**
         * Appended after every seeded background context. Without this explicit
         * boundary, small models often treat the last assistant message in the
         * seed as something to continue, especially when the new message is
         * also a greeting.
         */
        private const val BACKGROUND_ONLY_RULES =
            "[End of background context]\n" +
                "The conversation above is reference-only history, not the current turn. " +
                "Do not continue, repeat, quote, summarize, or paraphrase it. " +
                "The next user message is a new request: answer it directly and freshly."

        private const val CURRENT_TURN_ONLY_RULES =
            "[Instruction: the earlier conversation is background context only. " +
                "Do not continue, repeat, or copy the previous assistant reply. " +
                "Answer only the user request above as a new, fresh turn.]"
    }
}
