package com.ogrchatai.app.data.ml

import android.content.Context
import android.util.Log
import com.hazratbilal.aikit.core.AiKitEngine
import com.hazratbilal.aikit.chat.chat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    private var aiKitChat: com.hazratbilal.aikit.chat.AiKitChat? = null
    private val isGenerating = AtomicBoolean(false)
    private val loadMutex = Mutex()
    private var currentModelId: String? = null

    data class GenerationConfig(
        val maxTokens: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val stopSequences: List<String> = emptyList(),
        val stream: Boolean = true
    )

    data class StreamToken(
        val text: String,
        val tokenId: Int,
        val probability: Float
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

                    Log.d(TAG, "Loading model via AiKit: $modelId from $filePath (${file.length() / (1024 * 1024)}MB)")

                    val engine = AiKitEngine.create(context)
                    aiKitEngine = engine

                    val loadResult = loadModelBlocking(engine, filePath)

                    if (loadResult.isFailure) {
                        Log.e(TAG, "AiKit loadModel failed: ${loadResult.exceptionOrNull()?.message}")
                        return@withContext Result.failure(loadResult.exceptionOrNull()!!)
                    }

                    val chat = engine.chat()
                    aiKitChat = chat

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
                    Result.failure(RuntimeException("Out of memory: ${e.message}"))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load model: $modelId", e)
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

    fun generateTextStream(
        modelId: String,
        prompt: String,
        systemPrompt: String = "You are a helpful assistant.",
        config: GenerationConfig = GenerationConfig()
    ): Flow<StreamToken> = callbackFlow {
        val chat = aiKitChat
        if (chat == null) {
            close(IllegalStateException("Model not loaded: $modelId"))
            return@callbackFlow
        }

        if (isGenerating.get()) {
            close(IllegalStateException("Another generation is in progress"))
            return@callbackFlow
        }

        isGenerating.set(true)
        loadedModels[modelId]?.let {
            it.lastUsedAt = System.currentTimeMillis()
            it.isInUse = true
        }

        var tokenCount = 0
        var insideThinkTag = false
        val thinkBuffer = StringBuilder()

        try {
            chat.sendMessage(
                prompt,
                config.maxTokens,
                systemPrompt.ifBlank { "You are a helpful assistant." },
                object : com.hazratbilal.aikit.chat.AiKitChat.ChatListener {
                    override fun onGenerationStarted() {
                        Log.d(TAG, "Generation started")
                    }

                    override fun onToken(token: String) {
                        tokenCount++

                        if (token.contains("<think>")) {
                            insideThinkTag = true
                        }

                        if (insideThinkTag) {
                            thinkBuffer.append(token)
                            if (token.contains("</think>")) {
                                insideThinkTag = false
                                thinkBuffer.clear()
                            }
                            return
                        }

                        trySend(
                            StreamToken(
                                text = token,
                                tokenId = tokenCount,
                                probability = 1.0f
                            )
                        )
                    }

                    override fun onGenerationComplete(fullResponse: String) {
                        Log.d(TAG, "Generation complete: ${fullResponse.length} chars, $tokenCount tokens")
                        isGenerating.set(false)
                        loadedModels[modelId]?.isInUse = false
                        close()
                    }

                    override fun onGenerationError(error: Throwable) {
                        Log.e(TAG, "Generation error", error)
                        isGenerating.set(false)
                        loadedModels[modelId]?.isInUse = false
                        close(error)
                    }

                    override fun onCancelled() {
                        Log.d(TAG, "Generation cancelled")
                        isGenerating.set(false)
                        loadedModels[modelId]?.isInUse = false
                        close()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start generation", e)
            isGenerating.set(false)
            loadedModels[modelId]?.isInUse = false
            close(e)
        }

        awaitClose {
            if (isGenerating.get()) {
                chat.cancelGeneration()
                isGenerating.set(false)
                loadedModels[modelId]?.isInUse = false
            }
        }
    }

    fun cancelGeneration() {
        aiKitChat?.cancelGeneration()
        isGenerating.set(false)
        loadedModels.values.forEach { it.isInUse = false }
    }

    fun unloadModel(modelId: String) {
        if (currentModelId == modelId) {
            aiKitEngine?.unloadModel()
            aiKitEngine = null
            aiKitChat = null
            currentModelId = null
        }
        loadedModels.remove(modelId)
        Log.i(TAG, "Model unloaded: $modelId")
    }

    fun unloadAllModels() {
        aiKitEngine?.unloadModel()
        aiKitEngine = null
        aiKitChat = null
        currentModelId = null
        loadedModels.clear()
        Log.i(TAG, "All models unloaded")
    }

    fun isModelLoaded(modelId: String): Boolean = loadedModels.containsKey(modelId)

    fun isGenerating(): Boolean = isGenerating.get()

    companion object {
        private const val TAG = "InferenceEngine"
    }
}
