package com.trustlens.domain

/**
 * The outcome of running text recognition over a captured image.
 *
 * Failure is an ordinary value rather than an exception, so every caller has to decide what
 * an unreadable photo means — which matters here, because "we could not read it" must never
 * quietly become "we found nothing wrong with it".
 */
sealed interface OcrResult {

    /**
     * Text was recognised. [text] is untrusted content: it came from whatever the camera was
     * pointed at, and is treated exactly like a pasted message.
     */
    data class Success(val text: String, val blockCount: Int) : OcrResult

    /** The recogniser ran and found no readable text. */
    data object NoTextFound : OcrResult

    /** Recognition could not complete. Recoverable — the user can retry. */
    data class Error(val reason: String) : OcrResult
}

/**
 * What a scan produced.
 *
 * [NoTextFound] and [Failed] deliberately carry no risk result: a blank or failed capture is
 * not evidence of safety, and inventing a verdict from it would be worse than saying nothing.
 */
sealed interface ScanOutcome {

    data class Analysed(
        val result: AnalysisResult,
        /** Exactly what OCR read, shown to the user so they can judge the extraction. */
        val recognisedText: String,
        /**
         * True when little enough text was recognised that the capture was probably
         * incomplete. The analysis still runs; the UI says what it was based on.
         */
        val isPartial: Boolean,
    ) : ScanOutcome

    data object NoTextFound : ScanOutcome

    data class Failed(val reason: String) : ScanOutcome
}
