package com.ogrchatai.app.data.repository

import android.content.Context
import com.ogrchatai.app.data.local.AppDatabase
import com.ogrchatai.app.data.local.ModelDao
import com.ogrchatai.app.data.local.entity.DownloadedModelEntity
import com.ogrchatai.app.data.preferences.UserPreferences
import com.ogrchatai.app.data.remote.HuggingFaceApiClient
import com.ogrchatai.app.domain.model.DownloadedModel
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.HuggingFaceModel
import com.ogrchatai.app.domain.model.ModelSettings
import com.ogrchatai.app.domain.model.Sibling
import com.ogrchatai.app.domain.repository.ModelRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelDao: ModelDao,
    private val apiClient: HuggingFaceApiClient,
    private val userPreferences: UserPreferences
) : ModelRepository {

    override fun getDownloadedModels(): Flow<List<DownloadedModel>> {
        return modelDao.getAllDownloadedModels().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDownloadedModelById(id: String): DownloadedModel? {
        return modelDao.getModelByHfIdOnce(id)?.toDomain()
    }

    override suspend fun searchModels(query: String): List<HuggingFaceModel> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiClient.api.searchModels(
                    query = query,
                    sort = "downloads",
                    limit = 50
                )
                response.map { dto ->
                    HuggingFaceModel(
                        id = dto.id ?: dto.modelId,
                        modelId = dto.modelId ?: dto.id ?: "",
                        name = dto.modelId?.substringAfter('/') ?: dto.id?.substringAfter('/') ?: "",
                        author = dto.modelId?.substringBefore('/') ?: dto.id?.substringBefore('/') ?: "",
                        downloads = dto.downloads.toLong(),
                        likes = dto.likes,
                        tags = dto.tags ?: emptyList(),
                        pipelineTag = dto.pipelineTag,
                        lastModified = dto.lastModified,
                        siblings = dto.siblings?.map { sib ->
                            Sibling(
                                filename = sib.filename ?: "",
                                rfilename = sib.filename ?: "",
                                size = sib.size
                            )
                        } ?: emptyList()
                    )
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override suspend fun downloadModel(
        modelId: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<DownloadedModel> {
        return withContext(Dispatchers.IO) {
            try {
                val modelsDir = File(context.filesDir, "models")
                if (!modelsDir.exists()) modelsDir.mkdirs()

                val outputFile = File(modelsDir, fileName)

                val responseBody = apiClient.api.downloadModelFile(modelId, fileName)
                val totalBytes = responseBody.contentLength()
                var downloadedBytes = 0L

                responseBody.byteStream().use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            if (totalBytes > 0) {
                                onProgress(downloadedBytes.toFloat() / totalBytes.toFloat())
                            }
                        }
                    }
                }

                val entity = DownloadedModelEntity(
                    hfModelId = modelId,
                    name = modelId.substringAfter('/'),
                    fileName = fileName,
                    filePath = outputFile.absolutePath,
                    sizeBytes = outputFile.length(),
                    quantization = com.ogrchatai.app.util.ModelUtils.getQuantizationFromName(fileName) ?: ""
                )
                modelDao.insertModel(entity)

                val downloadedModel = DownloadedModel(
                    id = modelId,
                    name = modelId.substringAfter('/'),
                    repository = modelId.substringBefore('/'),
                    fileName = fileName,
                    fileSize = outputFile.length(),
                    quantization = com.ogrchatai.app.util.ModelUtils.getQuantizationFromName(fileName) ?: "",
                    file = outputFile
                )
                Result.success(downloadedModel)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteModel(id: String): Result<Unit> {
        return runCatching {
            val entity = modelDao.getModelByHfIdOnce(id) ?: return@runCatching Unit
            val file = File(entity.filePath)
            if (file.exists()) file.delete()
            modelDao.deleteModelById(entity.id)
        }
    }

    override suspend fun getAvailableFiles(modelId: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val modelInfo = apiClient.api.getModelInfo(modelId)
                modelInfo.siblings
                    ?.filter { it.filename?.endsWith(".gguf") == true }
                    ?.map { it.filename ?: "" }
                    ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun getGenerationConfig(): Flow<GenerationConfig> {
        return flow {
            val temp = userPreferences.temperature.first()
            val topP = userPreferences.topP.first()
            val maxTokens = userPreferences.maxTokens.first()
            emit(GenerationConfig(
                temperature = temp,
                topP = topP,
                maxTokens = maxTokens
            ))
        }
    }

    override suspend fun updateGenerationConfig(config: GenerationConfig) {
        userPreferences.setTemperature(config.temperature)
        userPreferences.setTopP(config.topP)
        userPreferences.setMaxTokens(config.maxTokens)
    }

    override suspend fun getModelPath(id: String): String? {
        val entity = modelDao.getModelByHfIdOnce(id) ?: return null
        val file = File(entity.filePath)
        return if (file.exists()) file.absolutePath else null
    }

    override suspend fun isModelDownloaded(id: String): Boolean {
        return modelDao.getModelByHfIdOnce(id) != null
    }

    override fun getModelSettings(modelId: String): Flow<ModelSettings> {
        return userPreferences.getModelSettings(modelId)
    }

    override suspend fun getModelSettingsOnce(modelId: String): ModelSettings {
        return userPreferences.getModelSettingsOnce(modelId)
    }

    override suspend fun saveModelSettings(settings: ModelSettings) {
        userPreferences.saveModelSettings(settings)
    }

    private fun DownloadedModelEntity.toDomain(): DownloadedModel {
        val file = File(filePath)
        return DownloadedModel(
            id = hfModelId,
            name = name,
            repository = hfModelId.substringBefore('/'),
            fileName = fileName,
            fileSize = sizeBytes,
            quantization = quantization,
            file = if (file.exists()) file else null
        )
    }
}
