package com.trustlens.feature.voicecheck

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.trustlens.domain.TranscriptionBackend
import com.trustlens.domain.TranscriptionResult

/**
 * A thin wrapper over Android's [SpeechRecognizer].
 *
 * Three properties matter more than anything else here:
 *
 *  - **The microphone only runs between [start] and [stop].** There is no service, no
 *    background listener and no always-on hotword. A session begins because the user pressed a
 *    button and ends when they press another, when speech stops, or when the screen goes away.
 *  - **No audio is kept.** [SpeechRecognizer] hands back text; TrustLens never receives, writes
 *    or transmits an audio buffer, and there is no file, cache or upload anywhere in this file.
 *  - **The backend is reported, never assumed.** [availableBackend] asks the platform what
 *    actually exists, and the result of a session says which recogniser really ran. Calling a
 *    network-capable recognition "on-device" would be a false privacy claim.
 *
 * Must be constructed and driven from the main thread, as [SpeechRecognizer] requires.
 */
internal class SpeechTranscriber(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null
    private var activeBackend: TranscriptionBackend? = null
    private var finished = false

    /**
     * What this device can actually do, checked at runtime.
     *
     * On-device recognition is an API 31 addition and is not present on every device even
     * then, so both the version and the platform's own answer are consulted. `null` means no
     * recogniser at all — the manual-entry path.
     */
    fun availableBackend(): TranscriptionBackend? = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            TranscriptionBackend.ON_DEVICE

        SpeechRecognizer.isRecognitionAvailable(context) ->
            TranscriptionBackend.SYSTEM_MAY_USE_NETWORK

        else -> null
    }

    /**
     * Starts one listening session.
     *
     * [onPartial] receives interim text purely so the user can see that the microphone is
     * working; it is never analysed. [onFinished] is called exactly once per session.
     */
    fun start(
        onPartial: (String) -> Unit,
        onFinished: (TranscriptionResult) -> Unit,
    ) {
        val backend = availableBackend()
        if (backend == null) {
            onFinished(
                TranscriptionResult.Unavailable(
                    "No speech recognition service is available on this device",
                ),
            )
            return
        }

        stop()
        finished = false
        activeBackend = backend

        val client = runCatching {
            if (backend == TranscriptionBackend.ON_DEVICE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse {
            onFinished(
                TranscriptionResult.Error(it.message ?: "Could not start speech recognition"),
            )
            return
        }
        recognizer = client

        // Reports once, whichever way the session ends, and releases the microphone with it.
        fun finish(result: TranscriptionResult) {
            if (finished) return
            finished = true
            release()
            onFinished(result)
        }

        client.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()

                    finish(
                        if (text.isBlank()) {
                            TranscriptionResult.NoSpeechDetected
                        } else {
                            TranscriptionResult.Success(text, backend)
                        },
                    )
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let(onPartial)
                }

                override fun onError(error: Int) = finish(resultForError(error))

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        runCatching { client.startListening(intent) }.onFailure {
            finish(TranscriptionResult.Error(it.message ?: "Could not start listening"))
        }
    }

    /** Ends the current session early; the recogniser still delivers what it heard. */
    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    /** Tears everything down and releases the microphone. Safe to call repeatedly. */
    fun stop() {
        release()
    }

    private fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
        activeBackend = null
    }

    /**
     * Maps recogniser errors to outcomes.
     *
     * "Heard nothing" and "could not listen" are kept apart deliberately: silence is not a
     * failure, and neither of them is a clean result.
     */
    private fun resultForError(error: Int): TranscriptionResult = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> TranscriptionResult.NoSpeechDetected

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            TranscriptionResult.Error("Microphone permission is not granted")

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            TranscriptionResult.Error("The speech recogniser is busy — try again in a moment")

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> TranscriptionResult.Error(
            "This recogniser needed a network connection and could not reach it",
        )

        SpeechRecognizer.ERROR_AUDIO -> TranscriptionResult.Error("The microphone could not be read")
        SpeechRecognizer.ERROR_CLIENT -> TranscriptionResult.Error("Speech recognition stopped")
        SpeechRecognizer.ERROR_SERVER ->
            TranscriptionResult.Error("The speech recognition service reported an error")

        else -> TranscriptionResult.Error("Speech recognition failed (code $error)")
    }
}
