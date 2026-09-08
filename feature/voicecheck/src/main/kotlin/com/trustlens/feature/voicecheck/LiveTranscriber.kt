package com.trustlens.feature.voicecheck

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.trustlens.domain.TranscriptionBackend

/**
 * Continuous speech recognition for Live Guard.
 *
 * [SpeechTranscriber] handles one utterance and stops. Live Guard needs to keep listening
 * across a whole conversation, and Android's [SpeechRecognizer] has no continuous mode — it
 * ends a session at every pause. So this class restarts it, and the restart loop is the entire
 * reason this file exists.
 *
 * Everything [SpeechTranscriber] guarantees still holds, and is re-stated here because a
 * long-running listener is exactly where these properties would be easiest to lose:
 *
 *  - **The microphone runs only between [start] and [stop].** There is no service, no
 *    `WorkManager` job, no `startForegroundService`, and no hotword. When the screen leaves
 *    composition the caller disposes this object and the microphone goes with it.
 *  - **No audio is kept.** [onBufferReceived] is a no-op, as in the single-shot path. Only
 *    recognised *text* ever leaves this class; there is no buffer, file, cache or upload
 *    anywhere in it.
 *  - **The backend is reported, never assumed** — delegated to [SpeechTranscriber] rather
 *    than re-derived, so both paths can never disagree about a privacy claim.
 *
 * ## Why it reports [LiveStatus.NO_AUDIO]
 *
 * On many devices the telephony stack takes exclusive use of the microphone during a call, so
 * a third-party app is handed silence rather than an error. Silence that looks like "nothing
 * suspicious was said" would be the single most dangerous failure this feature could have. So
 * audio level is tracked, and a run of sessions that ends without the recogniser hearing
 * anything at all is reported as *not listening* rather than as *nothing found*.
 *
 * Must be constructed and driven from the main thread, as [SpeechRecognizer] requires.
 */
internal class LiveTranscriber(private val context: Context) {

    /** What the listener is currently doing, as far as it can tell. */
    sealed interface LiveStatus {
        /** Running and receiving audio. */
        data object Listening : LiveStatus

        /**
         * Running, but no audio has arrived for several sessions. On a live call this
         * usually means the telephony stack holds the microphone.
         */
        data object NoAudio : LiveStatus

        /** Stopped because the recogniser kept failing. */
        data class Failed(val reason: String) : LiveStatus

        /** No recogniser exists on this device at all. */
        data class Unavailable(val reason: String) : LiveStatus
    }

    private val handler = Handler(Looper.getMainLooper())
    private val availability = SpeechTranscriber(context)

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    /** Consecutive sessions that ended having heard no audio at all. */
    private var silentSessions = 0

    /** Consecutive sessions that ended in a hard error. Bounds the restart loop. */
    private var failedSessions = 0

    private var heardAudioThisSession = false
    private var reportedNoAudio = false

    /** The recogniser this session is using; reported with every segment. */
    var backend: TranscriptionBackend? = null
        private set

    val isListening: Boolean get() = listening

    /**
     * Begins listening and keeps listening until [stop].
     *
     * @param onSegment a completed phrase. This is the only text that is analysed.
     * @param onPartial interim text, shown so the user can see the microphone working. Never
     *   analysed — a half-heard sentence would produce a verdict about words nobody said.
     * @param onStatus transitions between listening, silent, failed and unavailable.
     * @param onLanguage the BCP 47 tag the recogniser reports it is hearing, when it reports
     *   one at all. Display only — nothing in the risk pipeline branches on it.
     */
    fun start(
        onSegment: (String) -> Unit,
        onPartial: (String) -> Unit,
        onStatus: (LiveStatus) -> Unit,
        onLanguage: (String) -> Unit = {},
    ) {
        val available = availability.availableBackend()
        if (available == null) {
            onStatus(
                LiveStatus.Unavailable(
                    "No speech recognition service is available on this device",
                ),
            )
            return
        }

        stop()
        backend = available
        listening = true
        silentSessions = 0
        failedSessions = 0
        reportedNoAudio = false
        onStatus(LiveStatus.Listening)

        beginSession(available, onSegment, onPartial, onStatus, onLanguage)
    }

    private fun beginSession(
        using: TranscriptionBackend,
        onSegment: (String) -> Unit,
        onPartial: (String) -> Unit,
        onStatus: (LiveStatus) -> Unit,
        onLanguage: (String) -> Unit,
    ) {
        if (!listening) return

        val client = runCatching {
            if (using == TranscriptionBackend.ON_DEVICE &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        }.getOrElse {
            stop()
            onStatus(LiveStatus.Failed(it.message ?: "Could not start speech recognition"))
            return
        }

        recognizer = client
        heardAudioThisSession = false

        // Exactly one of these runs per session, whatever ends it.
        var sessionClosed = false
        fun endSession(restartAfterMillis: Long) {
            if (sessionClosed) return
            sessionClosed = true
            releaseRecognizer()
            if (!listening) return
            handler.postDelayed(
                { beginSession(using, onSegment, onPartial, onStatus, onLanguage) },
                restartAfterMillis,
            )
        }

        client.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    failedSessions = 0
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                        .trim()

                    if (text.isNotEmpty()) {
                        noteAudio(onStatus)
                        onSegment(text)
                    } else {
                        noteSilence(onStatus)
                    }
                    endSession(RESTART_DELAY_MILLIS)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let {
                            noteAudio(onStatus)
                            onPartial(it)
                        }
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // The only signal that the microphone is actually delivering something.
                    if (rmsdB > AUDIBLE_RMS_DB) heardAudioThisSession = true
                }

                override fun onError(error: Int) {
                    when (error) {
                        // A pause in speech. Entirely normal in a conversation: go again.
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                        -> {
                            failedSessions = 0
                            noteSilence(onStatus)
                            endSession(RESTART_DELAY_MILLIS)
                        }

                        // Another app — very often the phone app — holds the microphone.
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                        SpeechRecognizer.ERROR_AUDIO,
                        -> {
                            failedSessions++
                            noteSilence(onStatus)
                            if (failedSessions >= MAX_CONSECUTIVE_FAILURES) {
                                stop()
                                onStatus(
                                    LiveStatus.Failed(
                                        "The microphone could not be read. Another app — " +
                                            "usually the phone call itself — is using it.",
                                    ),
                                )
                            } else {
                                endSession(BUSY_RETRY_DELAY_MILLIS)
                            }
                        }

                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            stop()
                            onStatus(
                                LiveStatus.Failed("Microphone permission is not granted"),
                            )
                        }

                        else -> {
                            failedSessions++
                            if (failedSessions >= MAX_CONSECUTIVE_FAILURES) {
                                stop()
                                onStatus(
                                    LiveStatus.Failed(
                                        "Speech recognition kept failing (code $error)",
                                    ),
                                )
                            } else {
                                endSession(BUSY_RETRY_DELAY_MILLIS)
                            }
                        }
                    }
                }

                /**
                 * The recogniser reporting which language it believes it is hearing.
                 *
                 * Reported to the user, never acted on. Nothing in the risk pipeline
                 * branches on language: the deterministic extractor reads Hindi, Hinglish
                 * and English with the same rules and the same weights, so a wrong guess
                 * here can mislabel a caption but can never change a score.
                 *
                 * A not-confident guess is dropped rather than shown, because "we think
                 * this might be Hindi" is not worth the screen space during a live call.
                 */
                override fun onLanguageDetection(results: Bundle) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
                    val confidence = results.getInt(
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN,
                    )
                    if (confidence < SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_CONFIDENT) {
                        return
                    }
                    results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                        ?.takeIf { it.isNotBlank() }
                        ?.let(onLanguage)
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit

                /** Raw audio is never received, never buffered, never written. */
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        runCatching { client.startListening(recognitionIntent()) }.onFailure {
            failedSessions++
            endSession(BUSY_RETRY_DELAY_MILLIS)
        }
    }

    /**
     * The listening configuration, including multilingual recognition.
     *
     * An Indian scam call is rarely conducted in one language. The caller opens in Hindi,
     * says "account", "block" and "OTP" in English, and switches back — so a recogniser
     * pinned to a single locale mis-hears whichever half it was not configured for, and a
     * mis-heard sentence scores as harmless. Android 13 added the extras that address this
     * directly, and all of them are asked for here:
     *
     *  - **detection** reports the language of each utterance via [onLanguageDetection], so
     *    the screen can say what was heard instead of implying it understood.
     *  - **switching** lets the recogniser change language mid-session, which is the only
     *    thing that handles genuine code-switching.
     *  - `BALANCED` rather than `QUICK_RESPONSE`: switching on a not-confident detection
     *    would corrupt a transcript that is about to be scored.
     *
     * Every one of these extras is documented as *"depending on the recognizer
     * implementation, this value may have no effect"*, so none of them is load-bearing. If
     * the service ignores them the behaviour is exactly what it was before they were set.
     *
     * `EXTRA_PREFER_OFFLINE` is requested for the same reason the backend is reported rather
     * than assumed: an on-device claim has to be the thing the app actually asked for.
     */
    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val allowed = ArrayList(RecognitionLanguages.REQUESTED)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, allowed.first())
                putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                    allowed,
                )
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH,
                    RecognizerIntent.LANGUAGE_SWITCH_BALANCED,
                )
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                    allowed,
                )
            }
        }

    private fun noteAudio(onStatus: (LiveStatus) -> Unit) {
        heardAudioThisSession = true
        silentSessions = 0
        if (reportedNoAudio) {
            reportedNoAudio = false
            onStatus(LiveStatus.Listening)
        }
    }

    private fun noteSilence(onStatus: (LiveStatus) -> Unit) {
        if (heardAudioThisSession) {
            silentSessions = 0
            return
        }
        silentSessions++
        if (silentSessions >= SILENT_SESSIONS_BEFORE_WARNING && !reportedNoAudio) {
            reportedNoAudio = true
            onStatus(LiveStatus.NoAudio)
        }
    }

    /** Stops listening and releases the microphone. Safe to call repeatedly. */
    fun stop() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        releaseRecognizer()
        backend = null
    }

    private fun releaseRecognizer() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private companion object {
        /**
         * Long enough that restarting does not thrash the recognition service, short enough
         * that a sentence beginning right after a pause is not clipped.
         */
        const val RESTART_DELAY_MILLIS = 150L

        /** Backoff when something else holds the microphone. */
        const val BUSY_RETRY_DELAY_MILLIS = 800L

        /** Bounds the restart loop so a permanently broken recogniser cannot spin forever. */
        const val MAX_CONSECUTIVE_FAILURES = 5

        /**
         * Sessions that may end silently before the user is told. Two covers an ordinary gap
         * in conversation; three in a row means nothing is arriving.
         */
        const val SILENT_SESSIONS_BEFORE_WARNING = 3

        /** Above the recogniser's noise floor — enough to call it "audio is arriving". */
        const val AUDIBLE_RMS_DB = 1.5f
    }
}
