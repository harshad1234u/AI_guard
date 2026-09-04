package com.trustlens.domain

import javax.inject.Inject

/**
 * The URL investigation pipeline:
 *
 * ```
 * url -> UrlAnalyzer (offline) -> RiskAssessor -> AnalysisResult
 * ```
 *
 * The scam text classifier is deliberately not used here. It is trained on message wording,
 * and a bare URL gives it nothing meaningful to read — asking it anyway would manufacture a
 * probability out of nothing.
 *
 * No external reputation source is consulted in this phase, so every result reports itself as
 * degraded: this is local structural analysis, not verification.
 */
class AnalyzeUrlUseCase @Inject constructor(
    private val urlAnalyzer: UrlAnalyzer,
    private val riskAssessor: RiskAssessor,
    /**
     * Optional so the deterministic pipeline can still be exercised — and tested — with no
     * provider configured at all. Absent, the result simply reports itself as unverified.
     */
    private val threatIntel: InvestigateIndicatorsUseCase? = null,
) {

    /**
     * [claimedOrganisation] lets a caller carry over an organisation named in a message.
     *
     * [source] records where the link came from — typed in, or read out of a QR code. It is
     * provenance for the result, and changes nothing about how the link is analysed: a URL is
     * inspected the same way whatever carried it.
     */
    suspend operator fun invoke(
        url: String,
        claimedOrganisation: String? = null,
        source: InvestigationSource = InvestigationSource.URL,
    ): AnalysisResult {
        val analysis = urlAnalyzer.analyse(url)
        val organisations = listOfNotNull(claimedOrganisation)

        val indicators = Indicators(
            urls = if (analysis.isParseable) listOf(url) else emptyList(),
            domains = listOfNotNull(analysis.host),
            organisations = organisations,
        )

        // Only the hostname is ever sent to a provider — never the path or query, which can
        // carry per-victim tokens that are nobody else's business.
        val report = threatIntel?.investigate(listOfNotNull(analysis.host))
            ?: ThreatIntelReport.NotConsulted

        val context = InvestigationContext(
            source = source,
            rawContent = url,
            indicators = indicators,
            mlSignal = null,
            observations = urlAnalyzer.observe(indicators.urls, organisations),
            externalEvidence = report.evidence,
            threatIntelUnavailable = report.degraded,
        )

        return AnalysisResult(
            context = context,
            risk = riskAssessor.assess(context),
            classifierBackend = null,
            classifierUnavailableReason =
                "URL analysis does not use the text classifier",
        )
    }

    /** Structural detail for the UI: host, domain, findings. Never contacts the URL. */
    fun inspect(url: String): UrlAnalysis = urlAnalyzer.analyse(url)
}
