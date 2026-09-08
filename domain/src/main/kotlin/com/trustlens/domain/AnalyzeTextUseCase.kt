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
        /**
         * Additional hostnames to ask reputation providers about, beyond those found in the
         * content itself.
         *
         * This exists for one case the extractor deliberately cannot cover: an email's
         * *sender domain*. `URL_BARE_DOMAIN` refuses to match a domain preceded by `@`, so
         * the host in `alerts@fake-sbi.com` is never extracted as a link — correctly, since
         * it is not one. For an email that host is the single most useful thing to check, so
         * the caller supplies it explicitly.
         *
         * Defaults to empty, so every existing surface behaves exactly as before. Whatever is
         * passed here is a hostname and nothing else — the same payload rule the rest of the
         * pipeline follows.
         */
        extraLookupSubjects: List<String> = emptyList(),
    ): AnalysisResult {
        val indicators = extractor.extract(text)

        // Offline structural inspection of any links found. Nothing is opened or resolved.
        val urlObservations = urlAnalyzer.observe(
            urls = indicators.urls,
            claimedOrganisations = indicators.organisations,
        )

        /*
         * Two inputs the classifier is not asked about, for the same reason: there is no
         * message wording for it to read, so any probability it returned would be an artefact
         * of the model's priors rather than a measurement of this content.
         *
         * The second case is the one [AnalyzeUrlUseCase] has always handled correctly and
         * this path did not. Measured on the shipped model, `https://www.sbi.co.in` — the
         * genuine State Bank of India domain — scores p=0.8888 and contributes +26, while
         * `sbi.bank.tk/verify` scores p=0.1729 and contributes +5. The model ranks the real
         * bank as five times more scam-like than the phish impersonating it, because a bare
         * hostname is far outside the distribution of the SMS corpus it was trained on.
         *
         * A link with no message around it is therefore assessed the way the Check URL screen
         * already assesses one: deterministic structure, plus external reputation, scored by
         * the risk engine. Nothing is removed — URL analysis and threat intelligence both
         * still run below, and the reason is reported so the UI can say the model did not run
         * rather than implying it ran and found nothing.
         */
        val classification = when {
            text.isBlank() -> ClassificationResult.Unavailable("No content to analyse")
            isLinkOnly(text, indicators) -> ClassificationResult.Unavailable(LINK_ONLY_REASON)
            else -> classifier.classify(text)
        }

        val signal = (classification as? ClassificationResult.Success)?.signal

        // Only registrable domains leave the device. The message itself — which may hold
        // names, amounts, OTPs and account numbers — is never sent to a third party.
        // The URLs go along as *local* context only: a provider that enumerates offending
        // URLs can then tell "this link is listed" from "something on this host is listed".
        // Nothing extra is transmitted — see ReputationSource.lookup.
        val report = threatIntel?.investigate(
            lookupSubjects(indicators, extraLookupSubjects),
            indicators.urls,
        ) ?: ThreatIntelReport.NotConsulted

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
    private fun lookupSubjects(
        indicators: Indicators,
        extra: List<String>,
    ): List<String> =
        (indicators.urls.mapNotNull { hostOf(it) } + indicators.domains + extra)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()

    private fun hostOf(url: String): String? =
        urlAnalyzer.analyse(url).takeIf { it.isParseable }?.host

    /**
     * True when the input is a link and nothing else.
     *
     * Decided from the extractor's own URL spans rather than by pattern-matching the text a
     * second time. That matters for two reasons: the extractor is the component that defines
     * what TrustLens considers a link — scheme-qualified, `www.`-prefixed, or a bare host on a
     * known TLD — so this can never disagree with it, and a `contains("http")` test would miss
     * `example.com/login` while wrongly firing on a sentence that merely mentions a link.
     *
     * The spans are cut out of the text and whatever is left is inspected. If a single letter
     * or digit survives, there is message wording here and the classifier is the right tool
     * for it. Only punctuation and whitespace surviving means the user pasted a link.
     *
     * Deliberately strict in the direction of *running* the model: "Verify now:
     * https://sbi.bank.tk/verify" keeps its ML signal, because "Verify now" is exactly the
     * kind of wording the classifier was trained to read.
     */
    private fun isLinkOnly(text: String, indicators: Indicators): Boolean {
        val links = indicators.matches.filter { it.kind == IndicatorKind.URL }
        if (links.isEmpty()) return false

        val remainder = StringBuilder(text)
        // Right to left, so removing one span cannot shift the offsets of the next.
        links.sortedByDescending { it.startIndex }.forEach { link ->
            val start = link.startIndex.coerceIn(0, remainder.length)
            val end = link.endIndex.coerceIn(start, remainder.length)
            remainder.delete(start, end)
        }

        return remainder.none { it.isLetterOrDigit() }
    }

    private companion object {
        /**
         * Stated plainly, because a classifier that did not run must never be mistaken for a
         * classifier that ran and found nothing.
         */
        const val LINK_ONLY_REASON =
            "This is a link with no message around it. The classifier reads message wording, " +
                "so it was not run — the link was analysed structurally instead."
    }
}
