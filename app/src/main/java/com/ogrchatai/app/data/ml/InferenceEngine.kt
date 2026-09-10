package com.ogrchatai.app.data.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InferenceEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val loadedModels = ConcurrentHashMap<String, LoadedModel>()
    private val isGenerating = AtomicBoolean(false)
    private val totalTokensGenerated = AtomicLong(0)
    private var memoryWarningThresholdBytes: Long = 512L * 1024 * 1024

    data class GenerationConfig(
        val maxTokens: Int = 512,
        val temperature: Float = 0.7f,
        val topP: Float = 0.9f,
        val topK: Int = 40,
        val repeatPenalty: Float = 1.1f,
        val stopSequences: List<String> = emptyList(),
        val stream: Boolean = true
    )

    data class TokenInfo(
        val token: String,
        val tokenId: Int,
        val probability: Float
    )

    data class GenerationResult(
        val text: String,
        val tokensGenerated: Int,
        val promptTokens: Int,
        val totalTokens: Int,
        val durationMs: Long,
        val tokensPerSecond: Float
    )

    data class LoadedModel(
        val modelId: String,
        val filePath: String,
        val modelType: ModelType,
        val vocabularySize: Int,
        val contextSize: Int,
        val embeddingSize: Int,
        val numberOfLayers: Int,
        val numberOfHeads: Int,
        val parameterCount: Long,
        val fileSizeBytes: Long,
        val loadedAt: Long = System.currentTimeMillis(),
        var lastUsedAt: Long = System.currentTimeMillis(),
        var isInUse: Boolean = false
    )

    enum class ModelType(val value: String) {
        LLAMA("llama"),
        MISTRAL("mistral"),
        PHI("phi"),
        GEMMA("gemma"),
        GPT2("gpt2"),
        UNKNOWN("unknown");

        companion object {
            fun fromArchitecture(architecture: String): ModelType {
                return when {
                    architecture.contains("llama", ignoreCase = true) -> LLAMA
                    architecture.contains("mistral", ignoreCase = true) -> MISTRAL
                    architecture.contains("phi", ignoreCase = true) -> PHI
                    architecture.contains("gemma", ignoreCase = true) -> GEMMA
                    architecture.contains("gpt2", ignoreCase = true) -> GPT2
                    else -> UNKNOWN
                }
            }
        }
    }

    suspend fun loadModel(modelId: String, filePath: String): Result<LoadedModel> =
        withContext(Dispatchers.IO) {
            try {
                if (loadedModels.containsKey(modelId)) {
                    val existing = loadedModels[modelId]!!
                    existing.lastUsedAt = System.currentTimeMillis()
                    existing.isInUse = true
                    return@withContext Result.success(existing)
                }

                checkMemoryAvailable(filePath)

                val file = File(filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Model file not found: $filePath")
                    )
                }

                val metadata = parseGgufMetadata(file)
                val loadedModel = LoadedModel(
                    modelId = modelId,
                    filePath = filePath,
                    modelType = ModelType.fromArchitecture(metadata.architecture),
                    vocabularySize = metadata.vocabularySize,
                    contextSize = metadata.contextSize,
                    embeddingSize = metadata.embeddingSize,
                    numberOfLayers = metadata.numberOfLayers,
                    numberOfHeads = metadata.numberOfHeads,
                    parameterCount = metadata.parameterCount,
                    fileSizeBytes = file.length()
                )

                loadedModels[modelId] = loadedModel
                Log.i(TAG, "Model loaded: $modelId (${loadedModel.modelType.name}, ${loadedModel.parameterCount} params)")

                Result.success(loadedModel)
            } catch (e: OutOfMemoryError) {
                unloadOldestModel()
                Result.failure(RuntimeException("Out of memory loading model: ${e.message}"))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: $modelId", e)
                Result.failure(e)
            }
        }

    fun unloadModel(modelId: String) {
        val model = loadedModels.remove(modelId)
        if (model != null) {
            model.isInUse = false
            Log.i(TAG, "Model unloaded: $modelId")
        }
    }

    fun unloadAllModels() {
        loadedModels.values.forEach { it.isInUse = false }
        loadedModels.clear()
        Log.i(TAG, "All models unloaded")
    }

    fun getLoadedModels(): List<LoadedModel> = loadedModels.values.toList()

    fun isModelLoaded(modelId: String): Boolean = loadedModels.containsKey(modelId)

    fun getLoadedModel(modelId: String): LoadedModel? = loadedModels[modelId]

    suspend fun generateText(
        modelId: String,
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Result<GenerationResult> = withContext(Dispatchers.IO) {
        val model = loadedModels[modelId]
            ?: return@withContext Result.failure(
                IllegalStateException("Model not loaded: $modelId")
            )

        if (isGenerating.get()) {
            return@withContext Result.failure(
                IllegalStateException("Another generation is in progress")
            )
        }

        try {
            isGenerating.set(true)
            model.lastUsedAt = System.currentTimeMillis()
            model.isInUse = true

            val startTime = System.currentTimeMillis()

            val result = runInference(model, prompt, config)

            val durationMs = System.currentTimeMillis() - startTime
            val tokensPerSecond = if (durationMs > 0) {
                result.tokensGenerated * 1000f / durationMs
            } else 0f

            totalTokensGenerated.addAndGet(result.tokensGenerated.toLong())

            Result.success(
                GenerationResult(
                    text = result.text,
                    tokensGenerated = result.tokensGenerated,
                    promptTokens = result.promptTokens,
                    totalTokens = result.promptTokens + result.tokensGenerated,
                    durationMs = durationMs,
                    tokensPerSecond = tokensPerSecond
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed for model: $modelId", e)
            Result.failure(e)
        } finally {
            isGenerating.set(false)
            loadedModels[modelId]?.isInUse = false
        }
    }

    fun generateTextStream(
        modelId: String,
        prompt: String,
        config: GenerationConfig = GenerationConfig()
    ): Flow<StreamToken> = flow {
        val model = loadedModels[modelId]
            ?: throw IllegalStateException("Model not loaded: $modelId")

        if (isGenerating.get()) {
            throw IllegalStateException("Another generation is in progress")
        }

        isGenerating.set(true)
        model.lastUsedAt = System.currentTimeMillis()
        model.isInUse = true

        try {
            val tokens = streamInference(model, prompt, config)
            var tokenCount = 0
            tokens.collect { token ->
                emit(token)
                tokenCount++
                if (config.stopSequences.any { stop ->
                        token.text.endsWith(stop)
                    }) {
                    // Can't break from collect, but we can return
                }
            }
            totalTokensGenerated.addAndGet(tokenCount.toLong())
        } finally {
            isGenerating.set(false)
            loadedModels[modelId]?.isInUse = false
        }
    }.flowOn(Dispatchers.IO)

    fun cancelGeneration() {
        isGenerating.set(false)
    }

    fun isGenerating(): Boolean = isGenerating.get()

    fun getTotalTokensGenerated(): Long = totalTokensGenerated.get()

    private fun checkMemoryAvailable(filePath: String) {
        val fileSize = File(filePath).length()
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val availableMemory = maxMemory - (totalMemory - freeMemory)

        if (fileSize > availableMemory * 0.8) {
            Log.w(TAG, "Low memory warning. Model: ${fileSize / (1024 * 1024)}MB, Available: ${availableMemory / (1024 * 1024)}MB")
        }
    }

    private fun unloadOldestModel() {
        val oldest = loadedModels.values
            .filter { !it.isInUse }
            .minByOrNull { it.lastUsedAt }

        if (oldest != null) {
            unloadModel(oldest.modelId)
            Log.i(TAG, "Unloaded oldest model to free memory: ${oldest.modelId}")
        }
    }

    private fun parseGgufMetadata(file: File): GgufMetadata {
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                val magicString = String(magic)

                if (magicString != "GGUF") {
                    return GgufMetadata()
                }

                val versionBuffer = ByteArray(4)
                raf.readFully(versionBuffer)
                val version = ByteBuffer.wrap(versionBuffer).order(ByteOrder.LITTLE_ENDIAN).int

                val tensorCountBuffer = ByteArray(8)
                raf.readFully(tensorCountBuffer)
                val tensorCount = ByteBuffer.wrap(tensorCountBuffer).order(ByteOrder.LITTLE_ENDIAN).long

                val metadataKVCountBuffer = ByteArray(8)
                raf.readFully(metadataKVCountBuffer)
                val metadataKVCount = ByteBuffer.wrap(metadataKVCountBuffer).order(ByteOrder.LITTLE_ENDIAN).long

                var architecture = ""
                var vocabularySize = 32000
                var contextSize = 2048
                var embeddingSize = 4096
                var numberOfLayers = 32
                var numberOfHeads = 32

                repeat(metadataKVCount.toInt().coerceAtMost(100)) {
                    try {
                        val key = readGgufString(raf) ?: return@repeat
                        val valueTypeBuf = ByteArray(4)
                        raf.readFully(valueTypeBuf)
                        val valueType = ByteBuffer.wrap(valueTypeBuf).order(ByteOrder.LITTLE_ENDIAN).int

                        when {
                            key.contains("general.architecture") -> {
                                val value = readGgufString(raf)
                                if (value != null) architecture = value
                            }
                            key.contains("vocab_size") -> {
                                val buf = ByteArray(4)
                                raf.readFully(buf)
                                vocabularySize = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                            }
                            key.contains("context_length") -> {
                                val buf = ByteArray(4)
                                raf.readFully(buf)
                                contextSize = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                            }
                            key.contains("embedding_length") -> {
                                val buf = ByteArray(4)
                                raf.readFully(buf)
                                embeddingSize = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                            }
                            key.contains("block_count") -> {
                                val buf = ByteArray(4)
                                raf.readFully(buf)
                                numberOfLayers = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                            }
                            key.contains("attention.head_count") -> {
                                val buf = ByteArray(4)
                                raf.readFully(buf)
                                numberOfHeads = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).int
                            }
                            else -> skipGgufValue(raf, valueType)
                        }
                    } catch (e: Exception) {
                        return@repeat
                    }
                }

                GgufMetadata(
                    architecture = architecture,
                    vocabularySize = vocabularySize,
                    contextSize = contextSize,
                    embeddingSize = embeddingSize,
                    numberOfLayers = numberOfLayers,
                    numberOfHeads = numberOfHeads,
                    parameterCount = estimateParameterCount(
                        numberOfLayers, embeddingSize, vocabularySize
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse GGUF metadata, using defaults: ${e.message}")
            GgufMetadata()
        }
    }

    private fun readGgufString(raf: RandomAccessFile): String? {
        val lengthBuffer = ByteArray(8)
        raf.readFully(lengthBuffer)
        val length = ByteBuffer.wrap(lengthBuffer).order(ByteOrder.LITTLE_ENDIAN).long
        if (length < 0 || length > 4096) return null
        val bytes = ByteArray(length.toInt())
        raf.readFully(bytes)
        return String(bytes)
    }

    private fun skipGgufValue(raf: RandomAccessFile, valueType: Int) {
        when (valueType) {
            0 -> raf.readByte()                        // UINT8
            1 -> raf.readShort()                       // INT8
            2 -> raf.readShort()                       // UINT16
            3 -> raf.readShort()                       // INT16
            4 -> raf.readInt()                         // UINT32
            5 -> raf.readInt()                         // INT32
            6 -> raf.readFloat()                       // FLOAT32
            7 -> raf.readBoolean()                     // BOOL
            8 -> { raf.readLong(); raf.readLong() }    // UINT64
            9 -> raf.readLong()                        // INT64
            10 -> raf.readDouble()                     // FLOAT64
            11 -> {                                    // ARRAY
                val innerLength = ByteArray(8)
                raf.readFully(innerLength)
                val count = ByteBuffer.wrap(innerLength).order(ByteOrder.LITTLE_ENDIAN).long
                val elemType = raf.readByte().toInt() and 0xFF
                repeat(count.toInt()) { skipGgufValue(raf, elemType) }
            }
            12 -> readGgufString(raf)                  // STRING
            else -> {}
        }
    }

    private fun estimateParameterCount(
        numberOfLayers: Int,
        embeddingSize: Int,
        vocabularySize: Int
    ): Long {
        val hiddenSize = embeddingSize.toLong()
        return numberOfLayers.toLong() * hiddenSize * hiddenSize * 12 + vocabularySize.toLong() * hiddenSize
    }

    private fun runInference(
        model: LoadedModel,
        prompt: String,
        config: GenerationConfig
    ): InferenceResult {
        val promptTokens = estimateTokenCount(prompt)

        val generatedText = simulateInference(model, prompt, config)

        return InferenceResult(
            text = generatedText,
            tokensGenerated = estimateTokenCount(generatedText),
            promptTokens = promptTokens
        )
    }

    private fun streamInference(
        model: LoadedModel,
        prompt: String,
        config: GenerationConfig
    ): Flow<StreamToken> = flow {
        val generatedText = simulateInference(model, prompt, config)
        val words = generatedText.split(" ")
        var accumulated = ""

        for (word in words) {
            val tokenText = if (accumulated.isEmpty()) word else " $word"
            accumulated += tokenText
            emit(
                StreamToken(
                    text = tokenText,
                    tokenId = accumulated.hashCode(),
                    probability = 0.95f
                )
            )
            kotlinx.coroutines.delay(30)
        }
    }.flowOn(Dispatchers.IO)

    private fun simulateInference(
        model: LoadedModel,
        prompt: String,
        config: GenerationConfig
    ): String {
        val responses = mapOf(
            ModelType.LLAMA to "As a helpful AI assistant, I can help you with that. ",
            ModelType.MISTRAL to "Based on my analysis, here is my response. ",
            ModelType.PHI to "I understand your question. Let me provide a detailed answer. ",
            ModelType.GEMMA to "Thank you for your question. Here is what I think. ",
            ModelType.GPT2 to "The answer to your question involves several factors. ",
            ModelType.UNKNOWN to "I appreciate your question. Here is my response. "
        )
        val prefix = responses[model.modelType] ?: responses[ModelType.UNKNOWN]!!

        val maxTokens = config.maxTokens.coerceAtMost(100)
        val words = listOf(
            "The", "important", "thing", "to", "consider", "is", "that", "the",
            "analysis", "shows", "clear", "patterns", "in", "the", "data",
            "which", "suggests", "further", "investigation", "is", "warranted",
            "based", "on", "current", "evidence", "and", "research", "findings"
        )

        val resultWords = mutableListOf<String>()
        var count = 0
        while (count < maxTokens) {
            resultWords.add(words.random())
            count++
            if (count >= 20 && Math.random() < 0.3) break
        }

        return prefix + resultWords.joinToString(" ")
    }

    private fun estimateTokenCount(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    private data class GgufMetadata(
        val architecture: String = "llama",
        val vocabularySize: Int = 32000,
        val contextSize: Int = 2048,
        val embeddingSize: Int = 4096,
        val numberOfLayers: Int = 32,
        val numberOfHeads: Int = 32,
        val parameterCount: Long = 7_000_000_000L
    )

    private data class InferenceResult(
        val text: String,
        val tokensGenerated: Int,
        val promptTokens: Int
    )

    data class StreamToken(
        val text: String,
        val tokenId: Int,
        val probability: Float
    )

    companion object {
        private const val TAG = "InferenceEngine"
    }
}
