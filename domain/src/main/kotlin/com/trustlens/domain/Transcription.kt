package com.trustlens.domain

/**
 * Which speech recogniser actually produced a transcript.
 *
 * Reported by the adapter that ran, never inferred — the same rule
 * [InferenceBackend] follows. Labelling a network-capable recognition as on-device
 * would be a privacy claim TrustLens cannot support, so the type exists to make the
 * distinction impossible to lose on the way to the screen.
 */
enum class TranscriptionBackend {
    /** The platform's on-device recogniser. Audio does not leave the phone. */
    ON_DEVICE,

    /**
     * The standard system recogniser. Depending on the installed service it *may*
     * process audio off-device, so the UI must disclose this before it is used.
     */
    SYSTEM_MAY_USE_NETWORK,

    /** No recogniser at all: the user typed or pasted the words themselves. */
    MANUAL_ENTRY,
}

/**
 * The outcome of one listening session.
 *
 * Failure is an ordinary value rather than an exception, so every caller is forced to
 * decide what silence or a failed recognition means — which matters here, because
 * "we did not hear anything" must never quietly become "we heard nothing wrong".
 */
sealed interface TranscriptionResult {

    /**
     * Speech was recognised. [text] is untrusted content: it is whatever was said near
     * the microphone, and is treated exactly like a pasted message.
     */
    data class Success(
        val text: String,
        /** Which recogniser produced this. Displayed to the user verbatim. */
        val backend: TranscriptionBackend,
    ) : TranscriptionResult

    /** The recogniser ran and heard no speech. */
    data object NoSpeechDetected : TranscriptionResult

    /** No recogniser is available on this device at all. Manual entry still works. */
    data class Unavailable(val reason: String) : TranscriptionResult

    /** Recognition failed. Recoverable — the user can try again. */
    data class Error(val reason: String) : TranscriptionResult
}

/**
 * What a voice check produced.
 *
 * [NoSpeech], [Unavailable] and [Failed] deliberately carry no risk result: an empty or
 * failed listen is not evidence of safety, and inventing a verdict from silence would be
 * worse than saying nothing.
 */
sealed interface VoiceCheckOutcome {

    data class Analysed(
        val result: AnalysisResult,
        /** Exactly what was transcribed, shown so the user can judge the recognition. */
        val transcript: String,
        /** Which recogniser produced it, carried through to the screen. */
        val backend: TranscriptionBackend,
        /**
         * True when so few words were recognised that the capture was probably
         * incomplete. The analysis still runs; the UI says what it was based on.
         */
        val isPartial: Boolean,
    ) : VoiceCheckOutcome

    data object NoSpeech : VoiceCheckOutcome

    data class Unavailable(val reason: String) : VoiceCheckOutcome

    data class Failed(val reason: String) : VoiceCheckOutcome
}
