package com.ogrchatai.app.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ogrchatai.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small wrapper around [SpeechRecognizer] that keeps all recognition lifecycle handling in one
 * place. When the device supports on-device recognition (Android 13+), it is preferred so audio
 * does not leave the phone.
 */
@Singleton
class VoiceInputManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    interface Listener {
        fun onReadyForSpeech()
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onEndOfSpeech()
        fun onNoSpeechDetected(message: String)
        fun onError(message: String)
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var listener: Listener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentLanguageTag: String = Locale.getDefault().toLanguageTag()
    private var usingOnDeviceRecognizer = false
    private var networkOnlyRequested = false

    fun isRecognitionAvailable(): Boolean {
        return try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Starts listening. Must be called from the main thread.
     */
    fun start(languageTag: String = Locale.getDefault().toLanguageTag(), listener: Listener): Boolean {
        return startInternal(languageTag, listener, networkOnly = false)
    }

    private fun startInternal(
        languageTag: String,
        listener: Listener,
        networkOnly: Boolean
    ): Boolean {
        release()

        if (Looper.myLooper() != Looper.getMainLooper()) {
            listener.onError(context.getString(R.string.voice_error_generic))
            return false
        }

        if (!isRecognitionAvailable()) {
            listener.onError(context.getString(R.string.voice_error_unavailable))
            return false
        }

        this.listener = listener
        currentLanguageTag = languageTag
        networkOnlyRequested = networkOnly

        val onDeviceAvailable = !networkOnly &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }
                .getOrDefault(false)

        val recognizer = try {
            if (onDeviceAvailable) {
                usingOnDeviceRecognizer = true
                try {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } catch (_: Exception) {
                    usingOnDeviceRecognizer = false
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } else {
                usingOnDeviceRecognizer = false
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } catch (e: Exception) {
            this.listener = null
            usingOnDeviceRecognizer = false
            listener.onError(
                context.getString(R.string.voice_error_unavailable) +
                    (e.message?.let { " ($it)" } ?: "")
            )
            return false
        }

        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                this@VoiceInputManager.listener?.onReadyForSpeech()
            }

            override fun onBeginningOfSpeech() = Unit

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                this@VoiceInputManager.listener?.onEndOfSpeech()
            }

            override fun onError(error: Int) {
                val currentListener = this@VoiceInputManager.listener ?: return

                // Many Android 13+ devices expose the on-device recognizer before the user has
                // downloaded the selected language. In that case the network recognizer usually
                // still works, so retry once instead of failing the whole call.
                if (usingOnDeviceRecognizer &&
                    !networkOnlyRequested &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                ) {
                    mainHandler.post {
                        if (this@VoiceInputManager.listener === currentListener) {
                            startInternal(currentLanguageTag, currentListener, networkOnly = true)
                        }
                    }
                    return
                }

                if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {
                    currentListener.onNoSpeechDetected(context.getString(R.string.voice_error_no_speech))
                } else {
                    currentListener.onError(errorMessage(error))
                }
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                val currentListener = this@VoiceInputManager.listener ?: return
                if (text.isBlank()) {
                    currentListener.onNoSpeechDetected(context.getString(R.string.voice_error_no_match))
                } else {
                    currentListener.onFinalResult(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trimStart()
                    .orEmpty()

                if (text.isNotBlank()) {
                    this@VoiceInputManager.listener?.onPartialResult(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        return try {
            recognizer.startListening(intent)
            true
        } catch (e: SecurityException) {
            release()
            listener.onError(context.getString(R.string.voice_error_permission))
            false
        } catch (e: Exception) {
            release()
            listener.onError(context.getString(R.string.voice_error_generic))
            false
        }
    }

    /**
     * Asks the recognizer to finish and deliver the final result.
     */
    fun stop() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
            release()
        }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
            // Ignore failures while shutting down.
        } finally {
            release()
        }
    }

    fun release() {
        listener = null
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
            // Ignore failures while shutting down.
        }
        speechRecognizer = null
    }

    private fun createRecognizer(): SpeechRecognizer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (_: Exception) {
                // Fall through to the regular recognition service.
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.voice_error_audio)

        SpeechRecognizer.ERROR_CLIENT -> context.getString(R.string.voice_error_generic)

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.voice_error_permission)

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> context.getString(R.string.voice_error_network)

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> context.getString(R.string.voice_error_busy)

        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> context.getString(R.string.voice_error_language)

        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_error_no_speech)

        else -> context.getString(R.string.voice_error_generic)
    }
}
