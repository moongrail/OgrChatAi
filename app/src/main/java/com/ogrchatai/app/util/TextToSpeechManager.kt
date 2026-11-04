package com.ogrchatai.app.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.ogrchatai.app.domain.model.VoiceGender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Streaming text-to-speech helper for the voice-call screen.
 *
 * Complete sentences are queued to Android TTS as soon as they arrive from the model, so the
 * assistant starts speaking before generation has finished. The class also selects a voice based
 * on the user's male/female preference and falls back to pitch adjustment when the installed TTS
 * engine does not expose gender information.
 */
class TextToSpeechManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val utteranceCounter = AtomicLong(0L)

    private var tts: TextToSpeech? = null
    private var initialized = false
    private var preparing: CompletableDeferred<Result<Unit>>? = null
    private var desiredLocale: Locale = Locale.getDefault()
    private var desiredGender: VoiceGender = VoiceGender.FEMALE

    private val streamBuffer = StringBuilder()
    private var pendingUtterances = 0
    private var streamFinished = false

    var onAllDone: (() -> Unit)? = null

    fun isReady(): Boolean = initialized && tts != null

    suspend fun prepare(locale: Locale, gender: VoiceGender): Result<Unit> =
        withContext(Dispatchers.Main) {
            desiredLocale = locale
            desiredGender = gender

            if (initialized && tts != null) {
                return@withContext configure(tts!!)
            }

            val existing = preparing
            if (existing != null) {
                return@withContext existing.await()
            }

            val deferred = CompletableDeferred<Result<Unit>>()
            preparing = deferred
            createEngine(deferred)
            deferred.await()
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
                    deferred.complete(Result.failure(
                        IllegalStateException("Text-to-speech engine is not available")
                    ))
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
        val languageResult = engine.setLanguage(desiredLocale)
        if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED
        ) {
            return Result.failure(
                IllegalStateException(
                    "Voice data for ${desiredLocale.displayLanguage} is not installed"
                )
            )
        }

        selectVoice(engine)
        engine.setPitch(if (desiredGender == VoiceGender.MALE) 0.82f else 1.12f)
        engine.setSpeechRate(1.0f)
        return Result.success(Unit)
    }

    private fun selectVoice(engine: TextToSpeech) {
        val voices = try {
            engine.voices.orEmpty()
        } catch (_: Exception) {
            emptySet<Voice>()
        }

        if (voices.isEmpty()) return

        val sameLanguage = voices.filter {
            it.locale.language.equals(desiredLocale.language, ignoreCase = true)
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
                // Some engines expose voices they cannot actually select. Pitch still applies.
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
        val engine = tts ?: return
        val utteranceId = "ogrtts-preview-${utteranceCounter.incrementAndGet()}"
        try {
            engine.speak(cleanupForSpeech(text), TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } catch (_: Exception) {
            // A missing engine is reported by prepare(); a preview must never crash Settings.
        }
    }

    fun startStreaming() {
        streamBuffer.clear()
        pendingUtterances = 0
        streamFinished = false
        try {
            tts?.stop()
        } catch (_: Exception) {
            // Ignore playback stop failures.
        }
    }

    fun appendStreamingToken(token: String) {
        if (token.isEmpty() || tts == null) return
        streamBuffer.append(token)
        drainBuffer(flush = false)
    }

    fun finishStreaming() {
        drainBuffer(flush = true)
        streamFinished = true
        notifyIfFinished()
    }

    fun stop() {
        streamBuffer.clear()
        pendingUtterances = 0
        streamFinished = false
        onAllDone = null
        try {
            tts?.stop()
        } catch (_: Exception) {
            // Ignore playback stop failures.
        }
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
            // Ignore engine shutdown failures.
        }
        tts = null
        initialized = false
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
        val text = cleanupForSpeech(raw)
        if (text.isBlank()) return

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

    private fun cleanupForSpeech(raw: String): String {
        return ChatUtils.stripMarkdown(raw)
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
        private const val MAX_BUFFER_BEFORE_FLUSH = 220
    }
}
