package com.ogrchatai.app.domain.usecase.model

import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DownloadModelUseCase @Inject constructor(
    private val modelRepository: ModelRepository
) {
    sealed class DownloadResult {
        data object Idle : DownloadResult()
        data class Downloading(val progress: Float) : DownloadResult()
        data class Success(val model: DownloadedModel) : DownloadResult()
        data class Error(val message: String) : DownloadResult()
    }

    operator fun invoke(modelId: String): Flow<Result<Float>> = channelFlow {
        if (modelRepository.isModelDownloaded(modelId)) {
            send(Result.success(1f))
            return@channelFlow
        }

        val availableFiles = modelRepository.getAvailableFiles(modelId)
        val ggufFile = chooseBestGgufFile(availableFiles)
            ?: run {
                send(Result.failure(IllegalStateException("No downloadable GGUF files found for this model")))
                return@channelFlow
            }

        val progressChannel = Channel<Float>(Channel.CONFLATED)

        val downloadJob = launch(Dispatchers.IO) {
            val result = modelRepository.downloadModel(modelId, ggufFile) { progress ->
                progressChannel.trySend(progress)
            }
            progressChannel.close()
            result.onSuccess {
                send(Result.success(1f))
            }.onFailure { e ->
                send(Result.failure(e))
            }
        }

        for (progress in progressChannel) {
            send(Result.success(progress))
        }

        downloadJob.join()
    }

    private fun chooseBestGgufFile(files: List<String>): String? {
        val ggufFiles = files.filter {
            it.endsWith(".gguf", ignoreCase = true) &&
                !it.contains("mmproj", ignoreCase = true)
        }
        if (ggufFiles.isEmpty()) return files.firstOrNull()

        // Prefer a balanced 4-bit quantisation. This keeps quality reasonable while fitting
        // comfortably into the memory available on typical phones.
        val preference = listOf(
            "q4_k_m", "q4_k_s", "q4_0", "q5_k_m", "q5_0", "q3_k_m", "q6_k", "q8_0", "q2_k", "f16", "fp16"
        )
        return ggufFiles.sortedBy { file ->
            val name = file.lowercase()
            preference.indexOfFirst { name.contains(it) }.let { if (it < 0) preference.size else it }
        }.first()
    }
}
