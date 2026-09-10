package com.ogrchatai.app.domain.repository

import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.HuggingFaceModel
import com.ogrchatai.app.domain.model.ModelSettings
import kotlinx.coroutines.flow.Flow

interface ModelRepository {
    fun getDownloadedModels(): Flow<List<DownloadedModel>>
    suspend fun getDownloadedModelById(id: String): DownloadedModel?
    suspend fun searchModels(query: String): List<HuggingFaceModel>
    suspend fun downloadModel(
        modelId: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<DownloadedModel>
    suspend fun deleteModel(id: String): Result<Unit>
    suspend fun getAvailableFiles(modelId: String): List<String>
    fun getGenerationConfig(): Flow<GenerationConfig>
    suspend fun updateGenerationConfig(config: GenerationConfig)
    suspend fun getModelPath(id: String): String?
    suspend fun isModelDownloaded(id: String): Boolean

    fun getModelSettings(modelId: String): Flow<ModelSettings>
    suspend fun getModelSettingsOnce(modelId: String): ModelSettings
    suspend fun saveModelSettings(settings: ModelSettings)
}
