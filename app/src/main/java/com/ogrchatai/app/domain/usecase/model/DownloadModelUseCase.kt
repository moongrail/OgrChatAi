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
        val ggufFile = availableFiles.firstOrNull { it.endsWith(".gguf") }
            ?: availableFiles.firstOrNull()
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
}
