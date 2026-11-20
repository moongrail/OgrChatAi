package com.ogrchatai.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.ogrchatai.app.data.tts.NeuralTextToSpeechEngine
import com.ogrchatai.app.data.tts.NeuralVoiceRepository
import com.ogrchatai.app.domain.model.TtsEngine
import com.ogrchatai.app.domain.model.TtsVoice
import com.ogrchatai.app.domain.model.VoiceGender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Streaming speech for the voice-call screen and Settings previews.
 *
 * When the Supertonic 3 model is installed it is used for natural, emotional
 * speech. Android's system TTS remains available as a transparent fallback, and
 * is used directly when the user selects the system engine.
 */
@Singleton
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val neuralVoiceRepository: NeuralVoiceRepository,
    private val neuralEngine: NeuralTextToSpeechEngine
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(0L)

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var preparing: CompletableDeferred<Result<Unit>>? = null
    private var desiredLocale: Locale = Locale.getDefault()
    private var systemLanguageTag: String? = null
    private var desiredGender: VoiceGender = VoiceGender.FEMALE
    private var desiredEngine: TtsEngine = TtsEngine.AUTO
    private var desiredVoice: TtsVoice = TtsVoice.F1

    private var useNeural = false
    private var neuralStreaming = false

    private val streamBuffer = StringBuilder()
    private var pendingUtterances = 0
    private var streamFinished = false

    var onAllDone: (() -> Unit)? = null

    val isUsingNeural: Boolean
        get() = useNeural && neuralEngine.isReady()

    fun isReady(): Boolean = isUsingNeural || (initialized && tts != null)

    suspend fun prepare(
        locale: Locale,
        gender: VoiceGender,
        engine: TtsEngine = TtsEngine.AUTO,
        voice: TtsVoice = TtsVoice.forGender(gender)
    ): Result<Unit> = withContext(Dispatchers.Main) {
        desiredLocale = locale
        desiredGender = gender
        desiredEngine = engine
        desiredVoice = voice

        if (engine == TtsEngine.SYSTEM) {
            useNeural = false
            neuralEngine.release()
            return@withContext prepareSystem(locale)
        }

        // Do not initialize Android's system TTS when the neural model is
        // available. Creating a second engine takes seconds on some devices and
        // wastes memory that the local LLM needs.
        var neuralFailure: Result<Unit>? = null
        val modelDir = neuralVoiceRepository.getModelDir()
        if (modelDir != null) {
            val neuralResult = neuralEngine.prepare(modelDir)
            if (neuralResult.isSuccess) {
                useNeural = true
                return@withContext Result.success(Unit)
            }
            neuralFailure = neuralResult
            Log.w(TAG, "Neural TTS unavailable, falling back to system voice", neuralResult.exceptionOrNull())
        }

        useNeural = false
        neuralEngine.release()
        val systemResult = prepareSystem(locale)
        if (systemResult.isFailure && neuralFailure != null) {
            return@withContext Result.failure(
                neuralFailure.exceptionOrNull()
                    ?: systemResult.exceptionOrNull()
                    ?: IllegalStateException("Text-to-speech is not available")
            )
        }
        systemResult
    }

    private suspend fun prepareSystem(locale: Locale): Result<Unit> {
        if (initialized && tts != null) {
            return configure(tts!!)
        }

        val existing = preparing
        if (existing != null) {
            return existing.await()
        }

        val deferred = CompletableDeferred<Result<Unit>>()
        preparing = deferred
        createEngine(deferred)
        return deferred.await()
    }

    private fun createEngine(deferred: CompletableDeferred<Result<Unit>>) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            mainHandler.post {
                val created = engine
                if (status != TextToSpeech.SUCCESS || created == null) {
                    initialized = false
                    tts = null
                    preparing = null
                    deferred.complete(
                        Result.failure(
                            IllegalStateException("Text-to-speech engine is not available")
                        )
                    )
                    return@post
                }

                tts = created
                initialized = true
                created.setOnUtteranceProgressListener(createProgressListener())
                val result = configure(created)
                preparing = null
                deferred.complete(result)
            }
        }
    }

    private fun configure(engine: TextToSpeech): Result<Unit> {
        val languageResult = applySystemLanguage(engine, desiredLocale, force = true)
        if (languageResult.isFailure) {
            return languageResult
        }

        // Do not force an artificial pitch. It was the main reason the fallback
        // voice sounded robotic. If a matching-gender voice exists, use it as-is;
        // otherwise keep the engine's natural pitch.
        engine.setPitch(1.0f)
        engine.setSpeechRate(1.0f)
        return Result.success(Unit)
    }

    /**
     * Switches the system engine to the detected language before each utterance.
     * The neural model detects language itself; the system fallback needs this
     * explicit switch so an English reply is not read with a Russian voice (and
     * vice versa) when the device UI language differs from the answer.
     */
    private fun applySystemLanguage(
        engine: TextToSpeech,
        locale: Locale,
        force: Boolean = false
    ): Result<Unit> {
        val tag = locale.language.lowercase(Locale.ROOT)
        if (!force && systemLanguageTag == tag) return Result.success(Unit)

        val languageResult = engine.setLanguage(locale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            if (!force && tag != desiredLocale.language.lowercase(Locale.ROOT)) {
                return applySystemLanguage(engine, desiredLocale, force = true)
            }
            systemLanguageTag = null
            return Result.failure(
                IllegalStateException(
                    "Voice data for ${locale.displayLanguage} is not installed"
                )
            )
        }

        systemLanguageTag = tag
        selectVoice(engine, locale)
        Log.d(TAG, "System TTS language -> ${locale.toLanguageTag()}, voice=${engine.voice?.name}")
        return Result.success(Unit)
    }

    private fun selectVoice(engine: TextToSpeech, locale: Locale) {
        val voices = try {
            engine.voices.orEmpty()
        } catch (_: Exception) {
            emptySet<Voice>()
        }

        if (voices.isEmpty()) return

        val sameLanguage = voices.filter {
            it.locale.language.equals(locale.language, ignoreCase = true)
        }
        if (sameLanguage.isEmpty()) return

        val localVoices = sameLanguage.filterNot { it.isNetworkConnectionRequired }
            .ifEmpty { sameLanguage.toList() }

        val matching = localVoices.firstOrNull { voice ->
            voiceMatchesGender(voice, desiredGender)
        }
        if (matching != null) {
            try {
                engine.voice = matching
            } catch (_: Exception) {
                // Some engines expose voices they cannot actually select.
            }
        }
    }

    private fun voiceMatchesGender(voice: Voice, gender: VoiceGender): Boolean {
        val text = buildString {
            append(voice.name)
            append(' ')
            voice.features?.forEach { feature ->
                append(feature)
                append(' ')
            }
        }.lowercase(Locale.ROOT)

        val hasFemale = text.contains("female") || text.contains("gender=female")
        val hasMale = !hasFemale && (text.contains("male") || text.contains("gender=male"))

        return when (gender) {
            VoiceGender.FEMALE -> hasFemale
            VoiceGender.MALE -> hasMale
        }
    }

    fun speakPreview(text: String) {
        val detectedLanguage = TtsLanguageDetector.detect(
            text,
            fallback = desiredLocale.language
        )
        val detectedLocale = Locale.forLanguageTag(detectedLanguage)
        if (isUsingNeural) {
            neuralEngine.onAllDone = null
            neuralEngine.speakPreview(text, detectedLanguage, desiredVoice)
            return
        }
        val engine = tts ?: return
        applySystemLanguage(engine, detectedLocale)
        val utteranceId = "ogrtts-preview-${utteranceCounter.incrementAndGet()}"
        try {
            engine.speak(cleanupForSystemSpeech(text), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (_: Exception) {
            // A missing engine is reported by prepare(); a preview must never crash Settings.
        }
    }

    fun startStreaming() {
        streamBuffer.clear()
        pendingUtterances = 0
        streamFinished = false
        neuralStreaming = false
        try {
            tts?.stop()
        } catch (_: Exception) {
            // Ignore playback stop failures.
        }

        if (isUsingNeural) {
            neuralStreaming = true
            neuralEngine.onAllDone = {
                mainHandler.post {
                    if (neuralStreaming) {
                        neuralStreaming = false
                        val callback = onAllDone
                        onAllDone = null
                        callback?.invoke()
                    }
                }
            }
            neuralEngine.startStreaming(
                languageTag = desiredLocale.toLanguageTag(),
                voice = desiredVoice
            )
        }
    }

    fun appendStreamingToken(token: String) {
        if (token.isEmpty()) return
        if (isUsingNeural) {
            neuralEngine.appendStreamingToken(token)
            return
        }
        if (tts == null) return
        streamBuffer.append(token)
        drainBuffer(flush = false)
    }

    fun finishStreaming() {
        if (isUsingNeural) {
            neuralEngine.finishStreaming()
            return
        }
        drainBuffer(flush = true)
        streamFinished = true
        notifyIfFinished()
    }

    fun stop() {
        streamBuffer.clear()
        pendingUtterances = 0
        streamFinished = false
        neuralStreaming = false
        neuralEngine.onAllDone = null
        neuralEngine.stop()
        onAllDone = null
        try {
            tts?.stop()
        } catch (_: Exception) {
            // Ignore playback stop failures.
        }
    }

    /** Release the large neural model, e.g. before deleting it from storage. */
    fun shutdownNeural() {
        if (useNeural) {
            useNeural = false
            neuralStreaming = false
        }
        neuralEngine.onAllDone = null
        neuralEngine.release()
    }

    fun shutdown() {
        stop()
        neuralEngine.release()
        useNeural = false
        try {
            tts?.shutdown()
        } catch (_: Exception) {
            // Ignore engine shutdown failures.
        }
        tts = null
        initialized = false
        systemLanguageTag = null
        preparing?.complete(Result.failure(IllegalStateException("TTS was shut down")))
        preparing = null
    }

    private fun drainBuffer(flush: Boolean) {
        while (true) {
            val end = streamBuffer.indexOfFirst { it == '.' || it == '!' || it == '?' || it == '\n' }
            if (end < 0) break

            val chunk = streamBuffer.substring(0, end + 1)
            streamBuffer.delete(0, end + 1)
            enqueue(chunk)
        }

        // Very long text without sentence punctuation should still start playing.
        if (streamBuffer.length >= MAX_BUFFER_BEFORE_FLUSH) {
            val chunk = streamBuffer.take(MAX_BUFFER_BEFORE_FLUSH).toString()
            streamBuffer.delete(0, MAX_BUFFER_BEFORE_FLUSH)
            enqueue(chunk)
        }

        if (flush && streamBuffer.isNotBlank()) {
            enqueue(streamBuffer.toString())
            streamBuffer.clear()
        }
    }

    private fun enqueue(raw: String) {
        val engine = tts ?: return
        val text = cleanupForSystemSpeech(raw)
        if (text.isBlank()) return

        val detectedLanguage = TtsLanguageDetector.detect(
            text,
            fallback = desiredLocale.language
        )
        applySystemLanguage(engine, Locale.forLanguageTag(detectedLanguage))

        Log.d(TAG, "System TTS sentence [$detectedLanguage]")
        pendingUtterances++
        val utteranceId = "ogrtts-${utteranceCounter.incrementAndGet()}"
        val result = try {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, utteranceId)
        } catch (_: Exception) {
            TextToSpeech.ERROR
        }
        if (result == TextToSpeech.ERROR) {
            pendingUtterances = (pendingUtterances - 1).coerceAtLeast(0)
            notifyIfFinished()
        }
    }

    private fun cleanupForSystemSpeech(raw: String): String {
        return ChatUtils.stripMarkdown(raw)
            .replace(Regex("<(?:laugh|breath|sigh)>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`[^`]*`"), " ")
            .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
            .replace(Regex("[*_#>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun createProgressListener(): UtteranceProgressListener {
        return object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post { onUtteranceFinished() }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post { onUtteranceFinished() }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post { onUtteranceFinished() }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                mainHandler.post { onUtteranceFinished() }
            }
        }
    }

    private fun onUtteranceFinished() {
        pendingUtterances = (pendingUtterances - 1).coerceAtLeast(0)
        notifyIfFinished()
    }

    private fun notifyIfFinished() {
        if (streamFinished && pendingUtterances == 0) {
            val callback = onAllDone
            onAllDone = null
            callback?.invoke()
        }
    }

    companion object {
        private const val TAG = "OgrTts"
        private const val MAX_BUFFER_BEFORE_FLUSH = 220
    }
}
