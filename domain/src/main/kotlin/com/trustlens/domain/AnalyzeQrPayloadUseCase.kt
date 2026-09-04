package com.trustlens.domain

import javax.inject.Inject

/**
 * What a QR scan produced.
 *
 * [Empty] carries no risk result on purpose: a code that decoded to nothing is not a code that
 * decoded to something harmless, and manufacturing a verdict for it would be a lie.
 */
sealed interface QrScanOutcome {

    data class Analysed(val payload: QrPayload, val result: AnalysisResult) : QrScanOutcome

    data object Empty : QrScanOutcome
}

/**
 * Routes a decoded QR payload into the analysis that already exists for its content type:
 *
 * ```
 * payload -> classify -> URL  -> AnalyzeUrlUseCase
 *                     -> UPI  -> AnalyzeUpiRequestUseCase
 *                     -> TEXT -> AnalyzeTextUseCase
 * ```
 *
 * This class contains no scam-detection logic whatsoever. It is a router: every judgement is
 * made downstream by components that predate QR scanning, so a link in a QR code is scored
 * identically to the same link typed into Check URL.
 *
 * It is also, structurally, the guarantee behind Principle 3. There is no branch here that
 * opens a URL, launches an app, or starts a payment — the only thing a payload can cause is an
 * [AnalysisResult] being returned to the screen for the user to read.
 */
class AnalyzeQrPayloadUseCase @Inject constructor(
    private val classifier: QrPayloadClassifier,
    private val analyzeText: AnalyzeTextUseCase,
    private val analyzeUrl: AnalyzeUrlUseCase,
    private val analyzeUpi: AnalyzeUpiRequestUseCase,
) {

    suspend operator fun invoke(rawPayload: String): QrScanOutcome =
        analyse(classifier.classify(rawPayload))

    suspend fun analyse(payload: QrPayload): QrScanOutcome = when (payload) {
        is QrPayload.Url -> QrScanOutcome.Analysed(
            payload = payload,
            result = analyzeUrl(payload.url, source = InvestigationSource.QR),
        )

        is QrPayload.Upi -> QrScanOutcome.Analysed(
            payload = payload,
            result = analyzeUpi(request = payload.request, rawPayload = payload.raw),
        )

        is QrPayload.Text -> QrScanOutcome.Analysed(
            payload = payload,
            result = analyzeText(payload.raw, InvestigationSource.QR),
        )

        QrPayload.Empty -> QrScanOutcome.Empty
    }
}
