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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
            val response = apiClient.api.searchModels(
                query = query,
                sort = "downloads",
                // HuggingFace caps this at 100.
                limit = 100,
                filter = "gguf"
            )
            response.map { dto ->
                val canonicalId = dto.id.ifBlank { dto.modelId }
                HuggingFaceModel(
                    id = canonicalId,
                    modelId = dto.modelId.ifBlank { dto.id },
                    name = canonicalId.substringAfter('/'),
                    author = canonicalId.substringBefore('/'),
                    downloads = dto.downloads.toLong(),
                    likes = dto.likes,
                    tags = dto.tags.orEmpty(),
                    pipelineTag = dto.pipelineTag,
                    lastModified = dto.lastModified,
                    siblings = dto.siblings.orEmpty().map { sib ->
                        Sibling(
                            filename = sib.name,
                            rfilename = sib.name,
                            size = sib.size ?: sib.lfs?.size
                        )
                    }
                )
            }
        }
    }

    override suspend fun downloadModel(
        modelId: String,
        fileName: String,
        onProgress: (Float) -> Unit
    ): Result<DownloadedModel> {
        return withContext(Dispatchers.IO) {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val relativeName = fileName.replace('\\', '/').trimStart('/')
            if (relativeName.isBlank() || relativeName.split('/').any { it == ".." || it == "." }) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid model file name")
                )
            }

            val outputFile = File(modelsDir, relativeName)
            outputFile.parentFile?.mkdirs()
            val partFile = File(outputFile.parentFile, "${outputFile.name}.part")

            try {
                var existingBytes = partFile.length().coerceAtLeast(0L)

                var response = apiClient.api.downloadModelFile(
                    repoId = modelId,
                    fileName = fileName,
                    range = if (existingBytes > 0L) "bytes=$existingBytes-" else null
                )

                // A stale or already-complete fragment cannot be resumed.
                if (response.code() == 416 && existingBytes > 0L) {
                    response.errorBody()?.close()
                    partFile.delete()
                    existingBytes = 0L
                    response = apiClient.api.downloadModelFile(modelId, fileName)
                }

                if (!response.isSuccessful) {
                    response.errorBody()?.close()
                    error("Download failed: HTTP ${response.code()}")
                }

                val responseBody = response.body()
                    ?: error("Download failed: empty response")
                try {
                    val isPartial = response.code() == 206 && existingBytes > 0L
                    if (existingBytes > 0L && !isPartial) {
                        // Some proxies ignore Range and send the whole file.
                        partFile.delete()
                        existingBytes = 0L
                    }

                    val responseBytes = responseBody.contentLength()
                    val totalBytes = if (responseBytes > 0L) {
                        existingBytes + responseBytes
                    } else {
                        -1L
                    }

                    var downloadedBytes = existingBytes
                    var lastReportedBytes = existingBytes
                    var lastReportedAtNanos = 0L

                    responseBody.byteStream().use { inputStream ->
                        FileOutputStream(partFile, existingBytes > 0L).use { outputStream ->
                            // Large sequential reads are significantly faster
                            // than 8 KiB reads on multi-gigabyte GGUF files.
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val bytesRead = inputStream.read(buffer)
                                if (bytesRead < 0) break
                                outputStream.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead

                                val now = System.nanoTime()
                                if (downloadedBytes - lastReportedBytes >= PROGRESS_UPDATE_BYTES ||
                                    now - lastReportedAtNanos >= PROGRESS_UPDATE_NANOS ||
                                    (totalBytes > 0L && downloadedBytes >= totalBytes)
                                ) {
                                    lastReportedBytes = downloadedBytes
                                    lastReportedAtNanos = now
                                    if (totalBytes > 0L) {
                                        onProgress(
                                            downloadedBytes.toFloat() / totalBytes.toFloat()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (totalBytes > 0L && downloadedBytes != totalBytes) {
                        error(
                            "Download incomplete: received $downloadedBytes of " +
                                "$totalBytes bytes"
                        )
                    }
                } finally {
                    responseBody.close()
                }

                currentCoroutineContext().ensureActive()
                if (outputFile.exists()) outputFile.delete()
                if (!partFile.renameTo(outputFile)) {
                    partFile.copyTo(outputFile, overwrite = true)
                    partFile.delete()
                }

                val previous = modelDao.getModelByHfIdOnce(modelId)
                if (previous != null &&
                    File(previous.filePath).absolutePath != outputFile.absolutePath
                ) {
                    runCatching { File(previous.filePath).delete() }
                }

                val entity = DownloadedModelEntity(
                    hfModelId = modelId,
                    name = modelId.substringAfter('/'),
                    fileName = relativeName,
                    filePath = outputFile.absolutePath,
                    sizeBytes = outputFile.length(),
                    quantization = com.ogrchatai.app.util.ModelUtils.getQuantizationFromName(relativeName) ?: ""
                )
                // Older builds could create more than one row for the same
                // repository. Remove them before inserting the fresh record.
                if (previous != null) {
                    modelDao.deleteModelsByHfId(modelId)
                }
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
            } catch (e: CancellationException) {
                // Keep the .part file: the next attempt can resume with a
                // Range request instead of downloading several gigabytes again.
                throw e
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
            File(file.parentFile, "${file.name}.part").delete()
            modelDao.deleteModelById(entity.id)
        }
    }

    override suspend fun getAvailableFiles(modelId: String): List<Sibling> {
        return withContext(Dispatchers.IO) {
            try {
                val modelInfo = apiClient.api.getModelInfo(modelId)
                modelInfo.siblings.orEmpty()
                    .filter { it.name.endsWith(".gguf", ignoreCase = true) }
                    .map { file ->
                        Sibling(
                            filename = file.name,
                            rfilename = file.name,
                            size = file.size ?: file.lfs?.size
                        )
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    override fun getGenerationConfig(): Flow<GenerationConfig> {
        return combine(
            userPreferences.temperature,
            userPreferences.topP,
            userPreferences.maxTokens
        ) { temperature, topP, maxTokens ->
            GenerationConfig(
                temperature = temperature,
                topP = topP,
                maxTokens = maxTokens
            )
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
        val entity = modelDao.getModelByHfIdOnce(id) ?: return false
        return File(entity.filePath).isFile
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

    override suspend fun setModelEnabled(modelId: String, enabled: Boolean) {
        val entity = modelDao.getModelByHfIdOnce(modelId) ?: return
        modelDao.setModelEnabled(entity.id, enabled)
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
            file = if (file.exists()) file else null,
            isEnabled = isEnabled
        )
    }
}

private const val PROGRESS_UPDATE_BYTES = 1024L * 1024L
private const val PROGRESS_UPDATE_NANOS = 100_000_000L
