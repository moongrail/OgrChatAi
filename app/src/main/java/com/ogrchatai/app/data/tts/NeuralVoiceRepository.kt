package com.ogrchatai.app.data.tts

import android.content.Context
import android.os.StatFs
import com.ogrchatai.app.domain.model.NeuralVoiceStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and manages the Supertonic 3 int8 voice model.
 *
 * The model is deliberately not bundled into the APK: it is about 129 MB and is
 * only needed when neural speech is used. It is stored in app-private storage,
 * so no storage permission is required.
 */
@Singleton
class NeuralVoiceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val downloadMutex = Mutex()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val baseDir: File
        get() = File(context.filesDir, "tts").apply { mkdirs() }

    private val modelDirectory: File
        get() = File(baseDir, MODEL_DIR_NAME)

    private val _status = MutableStateFlow(
        NeuralVoiceStatus(isInstalled = isInstalled(), totalBytes = EXPECTED_ARCHIVE_BYTES)
    )
    val status: StateFlow<NeuralVoiceStatus> = _status.asStateFlow()

    init {
        // Remove leftovers from a previous interrupted download. This is cheap and
        // keeps the app from accumulating hundreds of megabytes in cache.
        runCatching {
            File(context.cacheDir, ARCHIVE_NAME).delete()
            File(context.cacheDir, EXTRACT_DIR_NAME).deleteRecursively()
        }
        _status.value = NeuralVoiceStatus(
            isInstalled = isInstalled(),
            totalBytes = EXPECTED_ARCHIVE_BYTES
        )
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
                        isExtracting = false,
                        progress = 1f,
                        error = null
                    )
                }
                return@withLock Result.success(Unit)
            }

            if (!hasEnoughSpace()) {
                val message = "Not enough free storage. About 300 MB is required."
                _status.update { it.copy(error = message, isDownloading = false) }
                return@withLock Result.failure(IllegalStateException(message))
            }

            _status.update {
                it.copy(
                    isDownloading = true,
                    isExtracting = false,
                    progress = 0f,
                    downloadedBytes = 0L,
                    error = null
                )
            }

            val archive = File(context.cacheDir, ARCHIVE_NAME)
            val extractRoot = File(context.cacheDir, EXTRACT_DIR_NAME)
            try {
                archive.delete()
                extractRoot.deleteRecursively()

                val request = Request.Builder().url(MODEL_URL).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Download failed: HTTP ${response.code}")
                    }
                    val body = response.body ?: error("Download failed: empty response")
                    val total = body.contentLength().takeIf { it > 0 } ?: EXPECTED_ARCHIVE_BYTES
                    var downloaded = 0L
                    body.byteStream().use { input ->
                        FileOutputStream(archive).use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                _status.update {
                                    it.copy(
                                        progress = (downloaded.toFloat() / total).coerceIn(0f, 0.98f),
                                        downloadedBytes = downloaded
                                    )
                                }
                            }
                        }
                    }
                }

                if (archive.length() < MIN_VALID_ARCHIVE_BYTES) {
                    error("Downloaded model archive is incomplete")
                }

                _status.update {
                    it.copy(
                        isDownloading = false,
                        isExtracting = true,
                        progress = 0.99f
                    )
                }

                extractArchive(archive, extractRoot)
                val extracted = findModelDir(extractRoot)
                    ?: error("The downloaded archive does not contain the expected model files")
                if (!validateModelDir(extracted)) {
                    error("The downloaded model files are incomplete")
                }

                // Publish atomically enough for this purpose: remove the old copy
                // only after a complete new copy has been extracted and validated.
                if (modelDirectory.exists()) modelDirectory.deleteRecursively()
                modelDirectory.parentFile?.mkdirs()
                if (!extracted.renameTo(modelDirectory)) {
                    extracted.copyRecursively(modelDirectory, overwrite = true)
                    extracted.deleteRecursively()
                }

                if (!validateModelDir(modelDirectory)) {
                    modelDirectory.deleteRecursively()
                    error("The downloaded model could not be installed")
                }

                archive.delete()
                extractRoot.deleteRecursively()
                _status.update {
                    it.copy(
                        isInstalled = true,
                        isDownloading = false,
                        isExtracting = false,
                        progress = 1f,
                        downloadedBytes = EXPECTED_ARCHIVE_BYTES,
                        error = null
                    )
                }
                Result.success(Unit)
            } catch (e: CancellationException) {
                archive.delete()
                extractRoot.deleteRecursively()
                _status.update {
                    it.copy(
                        isDownloading = false,
                        isExtracting = false,
                        progress = 0f,
                        error = null
                    )
                }
                throw e
            } catch (e: Exception) {
                archive.delete()
                extractRoot.deleteRecursively()
                val message = e.message ?: "Failed to download the neural voice"
                _status.update {
                    it.copy(
                        isInstalled = isInstalled(),
                        isDownloading = false,
                        isExtracting = false,
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
            File(context.cacheDir, ARCHIVE_NAME).delete()
            File(context.cacheDir, EXTRACT_DIR_NAME).deleteRecursively()
            _status.update {
                NeuralVoiceStatus(
                    isInstalled = false,
                    totalBytes = EXPECTED_ARCHIVE_BYTES
                )
            }
        }
    }

    fun clearError() {
        _status.update { it.copy(error = null) }
    }

    private fun extractArchive(archive: File, destination: File) {
        destination.mkdirs()
        val canonicalRoot = destination.canonicalFile
        FileInputStream(archive).use { fileInput ->
            BZip2CompressorInputStream(fileInput, true).use { bzip ->
                TarArchiveInputStream(bzip).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        if (entry.isDirectory) {
                            File(canonicalRoot, entry.name).canonicalFile.let { dir ->
                                require(dir.path.startsWith(canonicalRoot.path)) {
                                    "Unsafe path in model archive"
                                }
                                dir.mkdirs()
                            }
                            continue
                        }
                        if (!entry.isFile) continue
                        val output = File(canonicalRoot, entry.name).canonicalFile
                        require(output.path.startsWith(canonicalRoot.path)) {
                            "Unsafe path in model archive"
                        }
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { out ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = tar.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findModelDir(root: File): File? {
        if (!root.exists()) return null
        return root.walkTopDown()
            .firstOrNull { dir -> dir.isDirectory && validateModelDir(dir) }
    }

    private fun validateModelDir(dir: File): Boolean {
        if (!dir.isDirectory) return false
        return REQUIRED_FILES.all { name ->
            val file = File(dir, name)
            file.isFile && file.length() > 0L
        }
    }

    companion object {
        const val MODEL_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/" +
                "sherpa-onnx-supertonic-3-tts-int8-2026-05-11.tar.bz2"

        private const val MODEL_DIR_NAME = "supertonic3"
        private const val ARCHIVE_NAME = "supertonic3-model.tar.bz2"
        private const val EXTRACT_DIR_NAME = "supertonic3-extract"
        private const val EXPECTED_ARCHIVE_BYTES = 128_774_318L
        private const val MIN_VALID_ARCHIVE_BYTES = 100_000_000L
        private const val REQUIRED_FREE_BYTES = 300L * 1024L * 1024L

        private val REQUIRED_FILES = listOf(
            "tts.json",
            "voice.bin",
            "duration_predictor.int8.onnx",
            "text_encoder.int8.onnx",
            "vector_estimator.int8.onnx",
            "vocoder.int8.onnx",
            "unicode_indexer.bin"
        )
    }
}
