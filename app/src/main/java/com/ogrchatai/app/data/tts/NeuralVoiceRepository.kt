package com.ogrchatai.app.data.tts

import android.content.Context
import android.os.StatFs
import com.ogrchatai.app.domain.model.NeuralVoiceStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and manages the Supertonic 3 int8 voice model.
 *
 * The model is deliberately not bundled into the APK: it is about 139 MiB and
 * is only needed when neural speech is used. Files are fetched individually
 * from a pinned HuggingFace revision instead of downloading and unpacking a
 * bzip2 archive, which was extremely slow on Android.
 */
@Singleton
class NeuralVoiceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val downloadMutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseDir: File
        get() = File(context.filesDir, "tts").apply { mkdirs() }

    private val modelDirectory: File
        get() = File(baseDir, MODEL_DIR_NAME)

    private val stagingDirectory: File
        get() = File(baseDir, STAGING_DIR_NAME)

    private val _status = MutableStateFlow(
        NeuralVoiceStatus(
            isInstalled = validateModelDir(modelDirectory),
            totalBytes = TOTAL_DOWNLOAD_BYTES
        )
    )
    val status: StateFlow<NeuralVoiceStatus> = _status.asStateFlow()

    init {
        // Cleanup can delete a large staging directory. It must not run on the
        // main thread while the first screen is being composed.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                stagingDirectory.deleteRecursively()
                File(context.cacheDir, LEGACY_ARCHIVE_NAME).delete()
                File(context.cacheDir, LEGACY_EXTRACT_DIR_NAME).deleteRecursively()
            }
            _status.update {
                it.copy(isInstalled = validateModelDir(modelDirectory))
            }
        }
    }

    fun isInstalled(): Boolean = validateModelDir(modelDirectory)

    fun getModelDir(): File? = modelDirectory.takeIf { validateModelDir(it) }

    fun installedSizeBytes(): Long {
        val dir = getModelDir() ?: return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    fun hasEnoughSpace(): Boolean {
        return try {
            val stat = StatFs(baseDir.absolutePath)
            stat.availableBytes > REQUIRED_FREE_BYTES
        } catch (_: Exception) {
            true
        }
    }

    suspend fun download(): Result<Unit> = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
            if (isInstalled()) {
                _status.update {
                    it.copy(
                        isInstalled = true,
                        isDownloading = false,
                        progress = 1f,
                        downloadedBytes = TOTAL_DOWNLOAD_BYTES,
                        totalBytes = TOTAL_DOWNLOAD_BYTES,
                        error = null
                    )
                }
                return@withLock Result.success(Unit)
            }

            stagingDirectory.deleteRecursively()
            // A partially installed directory cannot be used. Removing it frees
            // space for the complete replacement.
            if (modelDirectory.exists() && !validateModelDir(modelDirectory)) {
                modelDirectory.deleteRecursively()
            }

            if (!hasEnoughSpace()) {
                val message = "Not enough free storage. About 200 MB is required."
                _status.update { it.copy(error = message, isDownloading = false) }
                return@withLock Result.failure(IllegalStateException(message))
            }

            _status.update {
                it.copy(
                    isInstalled = false,
                    isDownloading = true,
                    progress = 0f,
                    downloadedBytes = 0L,
                    totalBytes = TOTAL_DOWNLOAD_BYTES,
                    error = null
                )
            }

            var completedBytes = 0L
            try {
                stagingDirectory.mkdirs()
                for (modelFile in MODEL_FILES) {
                    currentCoroutineContext().ensureActive()
                    downloadFile(
                        modelFile = modelFile,
                        destination = File(stagingDirectory, modelFile.name),
                        completedBytes = completedBytes
                    )
                    val downloadedFile = File(stagingDirectory, modelFile.name)
                    if (downloadedFile.length() != modelFile.expectedBytes) {
                        error(
                            "Downloaded ${modelFile.name} has an unexpected size " +
                                "(${downloadedFile.length()} bytes, expected " +
                                "${modelFile.expectedBytes})"
                        )
                    }
                    completedBytes += modelFile.expectedBytes
                    _status.update {
                        it.copy(
                            progress = (completedBytes.toFloat() / TOTAL_DOWNLOAD_BYTES)
                                .coerceIn(0f, 0.99f),
                            downloadedBytes = completedBytes
                        )
                    }
                }

                if (!validateModelDir(stagingDirectory)) {
                    error("The downloaded voice model files are incomplete")
                }

                // The staging directory lives on the same filesystem, so a
                // rename publishes the new model atomically. Keep the old copy
                // until every file has been downloaded and validated.
                if (modelDirectory.exists()) modelDirectory.deleteRecursively()
                modelDirectory.parentFile?.mkdirs()
                if (!stagingDirectory.renameTo(modelDirectory)) {
                    stagingDirectory.copyRecursively(modelDirectory, overwrite = true)
                    stagingDirectory.deleteRecursively()
                }

                if (!validateModelDir(modelDirectory)) {
                    modelDirectory.deleteRecursively()
                    error("The downloaded voice model could not be installed")
                }

                _status.update {
                    it.copy(
                        isInstalled = true,
                        isDownloading = false,
                        progress = 1f,
                        downloadedBytes = TOTAL_DOWNLOAD_BYTES,
                        totalBytes = TOTAL_DOWNLOAD_BYTES,
                        error = null
                    )
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                stagingDirectory.deleteRecursively()
                _status.update {
                    it.copy(
                        isInstalled = isInstalled(),
                        isDownloading = false,
                        progress = 0f,
                        downloadedBytes = 0L,
                        error = null
                    )
                }
                throw e
            } catch (e: Exception) {
                stagingDirectory.deleteRecursively()
                val message = e.message ?: "Failed to download the neural voice"
                _status.update {
                    it.copy(
                        isInstalled = isInstalled(),
                        isDownloading = false,
                        error = message
                    )
                }
                Result.failure(e)
            }
        }
    }

    suspend fun deleteModel(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            modelDirectory.deleteRecursively()
            stagingDirectory.deleteRecursively()
            File(context.cacheDir, LEGACY_ARCHIVE_NAME).delete()
            File(context.cacheDir, LEGACY_EXTRACT_DIR_NAME).deleteRecursively()
            _status.update {
                NeuralVoiceStatus(
                    isInstalled = false,
                    totalBytes = TOTAL_DOWNLOAD_BYTES
                )
            }
        }
    }

    fun clearError() {
        _status.update { it.copy(error = null) }
    }

    private suspend fun downloadFile(
        modelFile: ModelFile,
        destination: File,
        completedBytes: Long
    ) {
        destination.parentFile?.mkdirs()
        val partFile = File(destination.parentFile, "${destination.name}.part")
        partFile.delete()

        try {
            val request = Request.Builder()
                .url(modelFile.url)
                .header("User-Agent", "OgrChatAi-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Download failed for ${modelFile.name}: HTTP ${response.code}")
                }
                val body = response.body
                var downloaded = 0L
                var lastReportedBytes = 0L
                var lastReportedAtNanos = 0L
                body.byteStream().use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read

                            val now = System.nanoTime()
                            val overall = completedBytes + downloaded
                            if (overall - lastReportedBytes >= PROGRESS_UPDATE_BYTES ||
                                now - lastReportedAtNanos >= PROGRESS_UPDATE_NANOS
                            ) {
                                lastReportedBytes = overall
                                lastReportedAtNanos = now
                                _status.update {
                                    it.copy(
                                        progress = (overall.toFloat() / TOTAL_DOWNLOAD_BYTES)
                                            .coerceIn(0f, 0.99f),
                                        downloadedBytes = overall
                                    )
                                }
                            }
                        }
                    }
                }

                if (downloaded != modelFile.expectedBytes) {
                    error(
                        "Downloaded ${modelFile.name} is incomplete " +
                            "($downloaded bytes, expected ${modelFile.expectedBytes})"
                    )
                }

                if (destination.exists()) destination.delete()
                if (!partFile.renameTo(destination)) {
                    partFile.copyTo(destination, overwrite = true)
                    partFile.delete()
                }
            }
        } catch (e: Exception) {
            partFile.delete()
            throw e
        }
    }

    private fun validateModelDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        return MODEL_FILES.all { modelFile ->
            val file = File(dir, modelFile.name)
            file.isFile && file.length() == modelFile.expectedBytes
        }
    }

    private data class ModelFile(
        val name: String,
        val expectedBytes: Long,
        val url: String
    )

    companion object {
        private const val MODEL_DIR_NAME = "supertonic3"
        private const val STAGING_DIR_NAME = "supertonic3-download"
        private const val LEGACY_ARCHIVE_NAME = "supertonic3-model.tar.bz2"
        private const val LEGACY_EXTRACT_DIR_NAME = "supertonic3-extract"
        private const val REQUIRED_FREE_BYTES = 200L * 1024L * 1024L
        private const val PROGRESS_UPDATE_BYTES = 1024L * 1024L
        private const val PROGRESS_UPDATE_NANOS = 100_000_000L

        private const val HF_BASE_URL =
            "https://huggingface.co/csukuangfj2/" +
                "sherpa-onnx-supertonic-3-tts-int8-2026-05-11/resolve/" +
                "cca5a0e6c96e1d2c720986bf7e75fcc81dee3ae4/"

        private val MODEL_FILES = listOf(
            ModelFile(
                name = "text_encoder.int8.onnx",
                expectedBytes = 36_416_150L,
                url = HF_BASE_URL + "text_encoder.int8.onnx"
            ),
            ModelFile(
                name = "tts.json",
                expectedBytes = 8_253L,
                url = HF_BASE_URL + "tts.json"
            ),
            ModelFile(
                name = "voice.bin",
                expectedBytes = 517_168L,
                url = HF_BASE_URL + "voice.bin"
            ),
            ModelFile(
                name = "unicode_indexer.bin",
                expectedBytes = 262_144L,
                url = HF_BASE_URL + "unicode_indexer.bin"
            ),
            ModelFile(
                name = "duration_predictor.int8.onnx",
                expectedBytes = 3_700_147L,
                url = HF_BASE_URL + "duration_predictor.int8.onnx"
            ),
            ModelFile(
                name = "vector_estimator.int8.onnx",
                expectedBytes = 78_400_833L,
                url = HF_BASE_URL + "vector_estimator.int8.onnx"
            ),
            ModelFile(
                name = "vocoder.int8.onnx",
                expectedBytes = 25_991_073L,
                url = HF_BASE_URL + "vocoder.int8.onnx"
            )
        )

        private val TOTAL_DOWNLOAD_BYTES = MODEL_FILES.sumOf { it.expectedBytes }
    }
}
