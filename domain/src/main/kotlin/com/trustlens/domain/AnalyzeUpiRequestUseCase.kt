package com.trustlens.domain

import javax.inject.Inject

/**
 * Analysis of a UPI payment request:
 *
 * ```
 * UpiPaymentRequest -> IndicatorExtractor (on the free-text fields)
 *                   -> UrlAnalyzer (on any link carried alongside)
 *                   -> RiskAssessor -> AnalysisResult
 * ```
 *
 * Two things this class deliberately does not do:
 *
 *  - **It does not score.** It assembles an [InvestigationContext] and hands it to the same
 *    [RiskAssessor] every other flow uses. No weight, threshold or band lives here.
 *  - **It does not judge a payee.** A UPI ID appearing in a QR code is not evidence of
 *    anything; without a reputation source the honest position is that the recipient is
 *    unverified, which is what the resulting safe actions say.
 *
 * The payee name and transaction note are attacker-controlled free text. They are run through
 * the ordinary indicator extractor as *content*, exactly like a message body — a note reading
 * "URGENT: share OTP to confirm" is analysed for what it asks for, never obeyed.
 */
class AnalyzeUpiRequestUseCase @Inject constructor(
    private val extractor: IndicatorExtractor,
    private val urlAnalyzer: UrlAnalyzer,
    private val riskAssessor: RiskAssessor,
) {

    operator fun invoke(
        request: UpiPaymentRequest,
        rawPayload: String,
        source: InvestigationSource = InvestigationSource.QR,
    ): AnalysisResult {
        // Everything a human would read off the request, analysed as untrusted text.
        val descriptiveText = listOfNotNull(request.payeeName, request.transactionNote)
            .joinToString(separator = " ")
        val extracted = if (descriptiveText.isBlank()) {
            Indicators()
        } else {
            extractor.extract(descriptiveText)
        }

        val structuralAmount = request.amount?.let { amount ->
            listOfNotNull(request.currency, amount).joinToString(separator = " ")
        }

        // Structural fields take precedence: they were parsed from the payload itself rather
        // than found in prose, so they are the authoritative view of what is being asked for.
        val indicators = extracted.copy(
            urls = (listOfNotNull(request.url) + extracted.urls).distinct(),
            upiIds = (listOfNotNull(request.payeeAddress) + extracted.upiIds).distinct(),
            amounts = (listOfNotNull(structuralAmount) + extracted.amounts).distinct(),
        )

        val observations = buildList {
            add(
                Observation(
                    kind = ObservationKind.UPI_PAYMENT_REQUEST,
                    summary = "Asks for a UPI payment to a recipient that has not been verified",
                    detail = request.payeeAddress,
                ),
            )
            addAll(urlAnalyzer.observe(indicators.urls, indicators.organisations))
        }

        val context = InvestigationContext(
            source = source,
            rawContent = rawPayload,
            indicators = indicators,
            // The text classifier is trained on message wording; a payment payload's few
            // structured fields would only produce a probability invented out of nothing.
            mlSignal = null,
            observations = observations,
            externalEvidence = emptyList(),
            threatIntelUnavailable = true,
        )

        return AnalysisResult(
            context = context,
            risk = riskAssessor.assess(context),
            classifierBackend = null,
            classifierUnavailableReason =
                "UPI payment analysis does not use the text classifier",
        )
    }
}
