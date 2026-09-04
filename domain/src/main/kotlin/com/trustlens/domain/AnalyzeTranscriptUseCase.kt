package com.trustlens.domain

import javax.inject.Inject

/**
 * Routes a speech transcript into the analysis that already exists for text:
 *
 * ```
 * TranscriptionResult -> AnalyzeTextUseCase -> VoiceCheckOutcome
 * ```
 *
 * There is no scam-detection logic here, and no voice-specific scoring anywhere. A spoken
 * sentence and the same sentence pasted into Check Message reach an identical score, because
 * they run through identical code — a property a test pins directly.
 *
 * That is deliberate, and it is the reason [ObservationKind.VOICE_SCAM_LANGUAGE] is **not**
 * emitted here despite existing in the domain. Voice is an *input source*, not a risk signal:
 * adding points because words arrived through a microphone would inflate every voice result
 * without a shred of evidence behind it. Provenance is recorded on the context instead, where
 * it informs the reader without touching the number.
 */
class AnalyzeTranscriptUseCase @Inject constructor(
    private val analyzeText: AnalyzeTextUseCase,
) {

    suspend operator fun invoke(transcription: TranscriptionResult): VoiceCheckOutcome =
        when (transcription) {
            is TranscriptionResult.Error -> VoiceCheckOutcome.Failed(transcription.reason)

            is TranscriptionResult.Unavailable ->
                VoiceCheckOutcome.Unavailable(transcription.reason)

            TranscriptionResult.NoSpeechDetected -> VoiceCheckOutcome.NoSpeech

            is TranscriptionResult.Success -> {
                val trimmed = transcription.text.trim()
                if (trimmed.isEmpty()) {
                    VoiceCheckOutcome.NoSpeech
                } else {
                    VoiceCheckOutcome.Analysed(
                        result = analyzeText(transcription.text, InvestigationSource.VOICE),
                        transcript = transcription.text,
                        backend = transcription.backend,
                        isPartial = trimmed.length < PARTIAL_TRANSCRIPT_THRESHOLD,
                    )
                }
            }
        }

    private companion object {
        /** Below this, a transcript is short enough that the listen was probably cut off. */
        const val PARTIAL_TRANSCRIPT_THRESHOLD = 25
    }
}
