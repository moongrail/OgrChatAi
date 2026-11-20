package com.ogrchatai.app.data.download

import com.ogrchatai.app.domain.model.ModelDownloadState
import com.ogrchatai.app.domain.usecase.model.DownloadModelUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped model download coordinator.
 *
 * Downloads must not live in a screen ViewModel: navigating away destroys the
 * ViewModel and would otherwise cancel a multi-gigabyte download. This manager
 * owns the jobs for the lifetime of the app process and exposes their state to
 * every screen that observes it.
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val downloadModelUseCase: DownloadModelUseCase
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    private val _downloads = MutableStateFlow<Map<String, ModelDownloadState>>(emptyMap())
    val downloads: StateFlow<Map<String, ModelDownloadState>> = _downloads.asStateFlow()

    fun isDownloading(modelId: String): Boolean = jobs[modelId]?.isActive == true

    fun start(
        modelId: String,
        modelName: String,
        estimatedSizeBytes: Long
    ) {
        if (jobs[modelId]?.isActive == true) return
        if (_downloads.value[modelId]?.isComplete == true) return

        val safeTotal = estimatedSizeBytes.coerceAtLeast(0L)
        _downloads.update { current ->
            current + (modelId to ModelDownloadState(
                modelId = modelId,
                modelName = modelName,
                isDownloading = true,
                totalBytes = safeTotal
            ))
        }

        val startedAt = System.currentTimeMillis()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                downloadModelUseCase(modelId).collect { result ->
                    result.onSuccess { progress ->
                        val safeProgress = progress.coerceIn(0f, 1f)
                        if (safeProgress >= 1f) {
                            _downloads.update { current ->
                                current + (modelId to ModelDownloadState(
                                    modelId = modelId,
                                    modelName = modelName,
                                    progress = 1f,
                                    isDownloading = false,
                                    isComplete = true,
                                    downloadedBytes = safeTotal,
                                    totalBytes = safeTotal
                                ))
                            }
                        } else {
                            val elapsedSeconds =
                                ((System.currentTimeMillis() - startedAt) / 1000.0)
                                    .coerceAtLeast(0.001)
                            val downloadedBytes = (safeProgress * safeTotal).toLong()
                            val speed = if (safeTotal > 0L) {
                                (downloadedBytes / elapsedSeconds).toLong()
                            } else {
                                0L
                            }
                            _downloads.update { current ->
                                current + (modelId to ModelDownloadState(
                                    modelId = modelId,
                                    modelName = modelName,
                                    progress = safeProgress,
                                    isDownloading = true,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = safeTotal,
                                    speedBytesPerSec = speed
                                ))
                            }
                        }
                    }.onFailure { throwable ->
                        _downloads.update { current ->
                            current + (modelId to ModelDownloadState(
                                modelId = modelId,
                                modelName = modelName,
                                isDownloading = false,
                                error = throwable.message ?: "Download failed",
                                totalBytes = safeTotal
                            ))
                        }
                    }
                }
            } catch (e: CancellationException) {
                _downloads.update { it - modelId }
                throw e
            } catch (e: Exception) {
                _downloads.update { current ->
                    current + (modelId to ModelDownloadState(
                        modelId = modelId,
                        modelName = modelName,
                        isDownloading = false,
                        error = e.message ?: "Download failed",
                        totalBytes = safeTotal
                    ))
                }
            } finally {
                val runningJob = coroutineContext[Job]
                if (runningJob != null) {
                    jobs.remove(modelId, runningJob)
                } else {
                    jobs.remove(modelId)
                }
            }
        }
        jobs[modelId] = job
        job.start()
    }

    fun cancel(modelId: String) {
        jobs.remove(modelId)?.cancel()
        _downloads.update { it - modelId }
    }

    fun cancelAll() {
        jobs.keys.toList().forEach(::cancel)
    }

    /**
     * Drops stale "complete" entries after the user deletes a model. Active
     * downloads are never removed by this call.
     */
    fun retainCompleted(downloadedModelIds: Set<String>) {
        _downloads.update { current ->
            current.filterValues { state ->
                !state.isComplete || state.modelId in downloadedModelIds
            }
        }
    }
}
