package com.mindforge.app.domain.usecase.model

import com.mindforge.app.domain.model.DownloadedModel
import com.mindforge.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    operator fun invoke(modelId: String): Flow<Result<Float>> = flow {
        if (modelRepository.isModelDownloaded(modelId)) {
            emit(Result.success(1f))
            return@flow
        }

        val availableFiles = modelRepository.getAvailableFiles(modelId)
        val ggufFile = availableFiles.firstOrNull { it.endsWith(".gguf") }
            ?: availableFiles.firstOrNull()
            ?: run {
                emit(Result.failure(IllegalStateException("No downloadable files found")))
                return@flow
            }

        modelRepository.downloadModel(modelId, ggufFile) { progress ->
            // Progress is reported via repository callback
        }.collect { result ->
            result.onSuccess {
                emit(Result.success(1f))
            }.onFailure { e ->
                emit(Result.failure(e))
            }
        }
    }
}
