package com.trustlens.domain

import javax.inject.Inject

/**
 * The text/SMS investigation pipeline:
 *
 * ```
 * text -> IndicatorExtractor -> AiClassifier -> RiskAssessor -> AnalysisResult
 * ```
 *
 * Depends only on interfaces, so it can join components from :investigation, :ai and
 * :riskengine without any of those modules depending on each other.
 *
 * Two invariants this class is responsible for:
 *
 *  - **The message is data.** [text] is passed to the extractor and the classifier as an
 *    opaque value and stored verbatim on the context. Nothing here interprets its content as
 *    an instruction, however imperative that content sounds.
 *  - **It creates no findings of its own.** Observations come from the extractor's structured
 *    indicators and external evidence comes from reputation sources; the classifier's output
 *    travels only in the typed [InvestigationContext.mlSignal] field. The use case never
 *    promotes a model's output into an [Observation] or an [Inference].
 */
class AnalyzeTextUseCase @Inject constructor(
    private val extractor: IndicatorExtractor,
    private val urlAnalyzer: UrlAnalyzer,
    private val classifier: AiClassifier,
    private val riskAssessor: RiskAssessor,
    /**
     * Optional so the deterministic pipeline can still be exercised — and tested — with no
     * provider configured at all. Absent, the result simply reports itself as unverified.
     */
    private val threatIntel: InvestigateIndicatorsUseCase? = null,
) {

    suspend operator fun invoke(
        text: String,
        source: InvestigationSource = InvestigationSource.TEXT,
    ): AnalysisResult {
        val indicators = extractor.extract(text)

        // Offline structural inspection of any links found. Nothing is opened or resolved.
        val urlObservations = urlAnalyzer.observe(
            urls = indicators.urls,
            claimedOrganisations = indicators.organisations,
        )

        // Nothing to classify in empty input, and running a model on it would only invent a
        // probability for content that does not exist.
        val classification = if (text.isBlank()) {
            ClassificationResult.Unavailable("No content to analyse")
        } else {
            classifier.classify(text)
        }

        val signal = (classification as? ClassificationResult.Success)?.signal

        // Only registrable domains leave the device. The message itself — which may hold
        // names, amounts, OTPs and account numbers — is never sent to a third party.
        val report = threatIntel?.investigate(lookupSubjects(indicators))
            ?: ThreatIntelReport.NotConsulted

        val context = InvestigationContext(
            source = source,
            rawContent = text,
            indicators = indicators,
            mlSignal = signal,
            observations = urlObservations,
            externalEvidence = report.evidence,
            // Reported, never inferred: the absence of a lookup is stated plainly rather
            // than being allowed to pass for a clean result.
            threatIntelUnavailable = report.degraded,
        )

        return AnalysisResult(
            context = context,
            risk = riskAssessor.assess(context),
            classifierBackend = signal?.backend,
            classifierUnavailableReason =
                (classification as? ClassificationResult.Unavailable)?.reason,
        )
    }

    /**
     * The minimum that has to leave the phone for a reputation lookup to mean anything:
     * the hostname of each link, deduplicated.
     *
     * **Paths and query strings are dropped.** A phishing link's path routinely carries a
     * per-victim token; forwarding it would leak that token and tell a third party that this
     * user received that message. The message body itself — names, amounts, OTPs, account
     * numbers — never leaves the device at all.
     *
     * The host is kept whole rather than reduced to its registrable domain. Attackers put
     * the brand in the subdomain (`sbi-kyc.example.com`), reputation providers track such
     * hosts as objects in their own right, and collapsing to `example.com` would throw away
     * the more precise question while gaining no meaningful privacy: the hostname is the
     * attacker's infrastructure, not the user's secret.
     *
     * Everything is folded to one entry per distinct host, so a message repeating a link
     * five times still costs one lookup per provider.
     */
    private fun lookupSubjects(indicators: Indicators): List<String> =
        (indicators.urls.mapNotNull { hostOf(it) } + indicators.domains)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun hostOf(url: String): String? =
        urlAnalyzer.analyse(url).takeIf { it.isParseable }?.host
}
