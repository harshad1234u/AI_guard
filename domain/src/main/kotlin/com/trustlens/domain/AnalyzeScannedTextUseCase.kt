package com.trustlens.domain

import javax.inject.Inject

/**
 * Routes recognised text from a camera scan into the existing analysis pipeline.
 *
 * This class contains no scam-detection logic whatsoever, and that is the point: a
 * photographed message and a pasted message are the same problem once the words are
 * extracted, so both go through [AnalyzeTextUseCase]. The only thing camera input adds is
 * OCR's own failure modes — nothing read, a partial read, or a recogniser error — and
 * deciding what those mean is all this class does.
 *
 * The camera never "detects a scam". OCR produces text; the existing pipeline analyses it.
 */
class AnalyzeScannedTextUseCase @Inject constructor(
    private val analyzeText: AnalyzeTextUseCase,
) {

    suspend operator fun invoke(ocr: OcrResult): ScanOutcome = when (ocr) {
        is OcrResult.Error -> ScanOutcome.Failed(ocr.reason)

        OcrResult.NoTextFound -> ScanOutcome.NoTextFound

        is OcrResult.Success -> {
            val text = ocr.text.trim()
            if (text.isEmpty()) {
                // A recogniser can return "success" with nothing usable in it. Scoring that
                // would mean producing a verdict about content we never actually read.
                ScanOutcome.NoTextFound
            } else {
                ScanOutcome.Analysed(
                    result = analyzeText(ocr.text, InvestigationSource.CAMERA_OCR),
                    recognisedText = ocr.text,
                    isPartial = text.length < PARTIAL_TEXT_THRESHOLD,
                )
            }
        }
    }

    private companion object {
        /**
         * Below this many characters a capture is reported as probably partial. A blunt
         * heuristic, but an honest one: it drives a caveat in the UI, never the score.
         */
        const val PARTIAL_TEXT_THRESHOLD = 25
    }
}
