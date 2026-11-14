package com.ogrchatai.app.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig
import com.ogrchatai.app.domain.model.TtsLanguages
import com.ogrchatai.app.domain.model.TtsVoice
import com.ogrchatai.app.util.ChatUtils
import com.ogrchatai.app.util.TtsLanguageDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supertonic 3 neural speech through sherpa-onnx.
 *
 * Synthesis and playback run on dedicated background threads. Text is split into
 * sentences and synthesized incrementally, while already generated audio is
 * played immediately, so the first words are heard before the whole answer has
 * been generated.
 */
@Singleton
class NeuralTextToSpeechEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var tts: OfflineTts? = null
    private var loadedModelPath: String? = null
    private var loadedSampleRate: Int = 24_000
    private val prepareMutex = Mutex()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeGeneration = AtomicLong(0L)
    private val textBuffer = StringBuilder()
    private val audioQueue = LinkedBlockingQueue<PlaybackItem>(64)
    private val audioLock = Any()

    private val synthesisExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "neural-tts-synthesis").apply { isDaemon = true }
    }

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var streamLanguage: String = "en"

    @Volatile
    private var streamVoiceId: Int = 0

    var onAllDone: (() -> Unit)? = null

    private val playbackThread = Thread({ playbackLoop() }, "neural-tts-playback").apply {
        isDaemon = true
        start()
    }

    fun isReady(): Boolean = tts != null

    suspend fun prepare(modelDir: File): Result<Unit> = withContext(Dispatchers.IO) {
        prepareMutex.withLock {
            val path = modelDir.absolutePath
            if (tts != null && loadedModelPath == path) {
                return@withLock Result.success(Unit)
            }
            try {
                releaseInternal()
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        supertonic = OfflineTtsSupertonicModelConfig(
                            durationPredictor = File(modelDir, "duration_predictor.int8.onnx").absolutePath,
                            textEncoder = File(modelDir, "text_encoder.int8.onnx").absolutePath,
                            vectorEstimator = File(modelDir, "vector_estimator.int8.onnx").absolutePath,
                            vocoder = File(modelDir, "vocoder.int8.onnx").absolutePath,
                            ttsJson = File(modelDir, "tts.json").absolutePath,
                            unicodeIndexer = File(modelDir, "unicode_indexer.bin").absolutePath,
                            voiceStyle = File(modelDir, "voice.bin").absolutePath
                        ),
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    ),
                    maxNumSentences = 1,
                    silenceScale = 0.2f
                )
                val engine = OfflineTts(config = config)
                tts = engine
                loadedModelPath = path
                loadedSampleRate = engine.sampleRate().takeIf { it > 0 } ?: 24_000
                Log.i(TAG, "Supertonic 3 loaded: ${engine.numSpeakers()} voices, " +
                    "${loadedSampleRate} Hz")
                Result.success(Unit)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load Supertonic 3", e)
                releaseInternal()
                Result.failure(
                    IllegalStateException(
                        e.message ?: "Failed to load the neural voice model",
                        e
                    )
                )
            }
        }
    }

    fun startStreaming(languageTag: String, voice: TtsVoice) {
        val generation = activeGeneration.incrementAndGet()
        streamLanguage = languageTag.toSupertonicLanguage()
        streamVoiceId = voice.speakerId
        textBuffer.clear()
        audioQueue.clear()
        audioQueue.offer(PlaybackItem.Stop)
        synchronized(audioLock) {
            releaseAudioTrackLocked()
        }
        Log.d(TAG, "Streaming generation $generation, lang=$streamLanguage, sid=$streamVoiceId")
    }

    fun appendStreamingToken(token: String) {
        if (token.isEmpty() || tts == null) return
        textBuffer.append(token)
        drainBuffer(flush = false)
    }

    fun finishStreaming() {
        val generation = activeGeneration.get()
        drainBuffer(flush = true)
        // Queue the end marker only after all synthesis jobs for this generation.
        synthesisExecutor.execute {
            if (generation == activeGeneration.get()) {
                audioQueue.put(PlaybackItem.End(generation))
            }
        }
    }

    fun speakPreview(text: String, languageTag: String, voice: TtsVoice) {
        if (tts == null) return
        startStreaming(languageTag, voice)
        textBuffer.append(text)
        finishStreaming()
    }

    fun stop() {
        activeGeneration.incrementAndGet()
        textBuffer.clear()
        audioQueue.clear()
        audioQueue.offer(PlaybackItem.Stop)
        synchronized(audioLock) {
            releaseAudioTrackLocked()
        }
    }

    fun release() {
        stop()
        tts = null
        loadedModelPath = null
        loadedSampleRate = 24_000
    }

    private fun releaseInternal() {
        try {
            tts?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release neural TTS", e)
        }
        tts = null
        loadedModelPath = null
        loadedSampleRate = 24_000
    }

    private fun drainBuffer(flush: Boolean) {
        while (true) {
            val end = textBuffer.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '\n' }
            if (end < 0) break
            val chunk = textBuffer.substring(0, end + 1)
            textBuffer.delete(0, end + 1)
            enqueueSentence(chunk)
        }

        if (textBuffer.length >= MAX_BUFFER_CHARS) {
            val chunk = textBuffer.take(MAX_BUFFER_CHARS).toString()
            textBuffer.delete(0, MAX_BUFFER_CHARS)
            enqueueSentence(chunk)
        }

        if (flush && textBuffer.isNotBlank()) {
            enqueueSentence(textBuffer.toString())
            textBuffer.clear()
        }
    }

    private fun enqueueSentence(raw: String) {
        val text = sanitizeForSpeech(raw)
        if (text.isBlank()) return
        val generation = activeGeneration.get()
        val sentenceLanguage = TtsLanguageDetector.detect(text, fallback = streamLanguage)
        Log.d(TAG, "Synthesize [$sentenceLanguage]")
        synthesisExecutor.execute {
            if (generation != activeGeneration.get()) return@execute
            val engine = tts ?: return@execute
            try {
                val config = com.k2fsa.sherpa.onnx.GenerationConfig(
                    silenceScale = 0.2f,
                    speed = 1.03f,
                    sid = streamVoiceId,
                    numSteps = 8,
                    extra = mapOf("lang" to sentenceLanguage)
                )
                // Generate one sentence at a time. The callback overload uses a
                // Kotlin Function1 whose boxed Integer return type is not stable
                // across D8 versions, so the blocking call is both simpler and
                // safe. Playback is still streamed sentence by sentence.
                val audio = engine.generateWithConfig(text, config)
                if (generation != activeGeneration.get()) return@execute
                if (audio.samples.isNotEmpty()) {
                    audioQueue.offer(
                        PlaybackItem.Audio(
                            generation = generation,
                            samples = audio.samples.copyOf(),
                            sampleRate = audio.sampleRate
                        )
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Neural synthesis failed: ${e.message}", e)
            }
        }
    }

    private fun playbackLoop() {
        while (true) {
            val item = try {
                audioQueue.take()
            } catch (_: InterruptedException) {
                continue
            }
            when (item) {
                is PlaybackItem.Stop -> {
                    synchronized(audioLock) { releaseAudioTrackLocked() }
                }

                is PlaybackItem.Audio -> {
                    if (item.generation != activeGeneration.get()) continue
                    val track = ensureAudioTrack(item.sampleRate) ?: continue
                    try {
                        track.write(item.samples, 0, item.samples.size, AudioTrack.WRITE_BLOCKING)
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio playback failed", e)
                    }
                }

                is PlaybackItem.End -> {
                    if (item.generation != activeGeneration.get()) continue
                    synchronized(audioLock) {
                        val track = audioTrack
                        audioTrack = null
                        if (track != null) {
                            try {
                                track.stop()
                            } catch (_: Exception) {
                                // Ignore stop errors.
                            }
                            track.release()
                        }
                    }
                    val finishedGeneration = item.generation
                    mainHandler.post {
                        if (finishedGeneration == activeGeneration.get()) {
                            val callback = onAllDone
                            onAllDone = null
                            callback?.invoke()
                        }
                    }
                }
            }
        }
    }

    private fun ensureAudioTrack(sampleRate: Int): AudioTrack? = synchronized(audioLock) {
        val existing = audioTrack
        if (existing != null && existing.sampleRate == sampleRate) return existing
        releaseAudioTrackLocked()
        return try {
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            ).coerceAtLeast(sampleRate / 2 * 4)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            audioTrack = track
            track
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio track", e)
            null
        }
    }

    private fun releaseAudioTrackLocked() {
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            try {
                track.pause()
                track.flush()
                track.release()
            } catch (_: Exception) {
                // Ignore release errors.
            }
        }
    }

    private fun sanitizeForSpeech(raw: String): String {
        val tagPlaceholders = mutableListOf<String>()
        var text = raw
        EXPRESSION_TAG.findAll(raw).forEach { match ->
            val placeholder = "\u0001${tagPlaceholders.size}\u0001"
            tagPlaceholders.add(match.value.lowercase(Locale.ROOT))
            text = text.replaceFirst(match.value, placeholder)
        }
        text = ChatUtils.stripMarkdown(text)
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("[*_#>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        tagPlaceholders.forEachIndexed { index, tag ->
            text = text.replace("\u0001$index\u0001", tag)
        }
        return text
    }

    private fun String.toSupertonicLanguage(): String {
        val code = Locale.forLanguageTag(this).language.lowercase(Locale.ROOT)
        return when {
            code in TtsLanguages.codes -> code
            code == "nb" || code == "nn" -> "en"
            code.isBlank() -> "en"
            else -> "en"
        }
    }

    private sealed interface PlaybackItem {
        data class Audio(
            val generation: Long,
            val samples: FloatArray,
            val sampleRate: Int
        ) : PlaybackItem

        data class End(val generation: Long) : PlaybackItem
        data object Stop : PlaybackItem
    }

    companion object {
        private const val TAG = "NeuralTts"
        private const val MAX_BUFFER_CHARS = 240
        private val EXPRESSION_TAG = Regex("<(?:laugh|breath|sigh)>", RegexOption.IGNORE_CASE)
    }
}
