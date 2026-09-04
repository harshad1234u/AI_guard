package com.trustlens.riskengine

import com.trustlens.domain.Confidence
import com.trustlens.domain.ExternalEvidence
import com.trustlens.domain.Inference
import com.trustlens.domain.Indicators
import com.trustlens.domain.InvestigationContext
import com.trustlens.domain.Observation
import com.trustlens.domain.ObservationKind
import com.trustlens.domain.ReputationVerdict
import com.trustlens.domain.RiskAssessor
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.RiskResult
import com.trustlens.domain.SafeAction
import com.trustlens.domain.ScamCategory

/**
 * The deterministic risk engine.
 *
 * Pure Kotlin, no IO, no coroutines, no randomness, no clock: the same context always yields
 * an identical [RiskResult]. This is the only component permitted to produce the final
 * number, and it can only be reached through [assess].
 *
 * Inputs are [Observation]s (what TrustLens saw) and [ExternalEvidence] (what a source
 * reported). [Inference]s are produced as output and are never scored — a conclusion cannot
 * feed back into the score that produced it.
 */
class RiskEngine : RiskAssessor {

    override fun assess(context: InvestigationContext): RiskResult {
        val observations = collectObservations(context)
        val evidence = context.externalEvidence
        val mlProbability = context.mlSignal?.scamProbability

        val observationPoints = observations.sumOf {
            RiskWeights.forObservation(it.kind, mlProbability)
        }
        val evidenceContributions = scoreEvidence(evidence)
        val evidencePoints = evidenceContributions.values.sum()

        val score = (observationPoints + evidencePoints).coerceIn(0, 100)
        val level = RiskLevel.forScore(score)

        val unusableEvidence = evidence.any { it.verdict in UNUSABLE_VERDICTS }
        val degraded = context.threatIntelUnavailable || unusableEvidence

        return RiskResult(
            score = score,
            level = level,
            confidence = confidenceOf(context, observations, evidence, degraded),
            reasons = reasonsFor(observations, evidence, evidenceContributions, mlProbability),
            observations = observations,
            externalEvidence = evidence,
            inferences = inferencesFor(context, observations, evidence),
            recommendedActions = actionsFor(context.indicators, observations, evidence, level),
            degraded = degraded,
        )
    }

    /**
     * Derives observations from the extracted indicators and the classifier signal, then
     * merges in observations produced elsewhere (URL analysis, QR decoding, voice).
     *
     * Deduplicated by [ObservationKind] so a signal reported by two stages is scored once,
     * and sorted by kind so ordering of the inputs cannot change the score.
     */
    private fun collectObservations(context: InvestigationContext): List<Observation> {
        val derived = buildList {
            val indicators = context.indicators

            context.mlSignal?.let { signal ->
                add(
                    Observation(
                        kind = ObservationKind.ML_SCAM_SIGNAL,
                        // A binary model reports UNKNOWN because it genuinely cannot name a
                        // category; saying "flagged this as UNKNOWN" would read as a finding
                        // rather than as the absence of one. Wording only — the weight of
                        // this observation is unchanged and still lives in RiskWeights.
                        summary = if (signal.category == ScamCategory.UNKNOWN) {
                            "On-device classifier rates this as likely spam or scam wording"
                        } else {
                            "On-device classifier flagged this as ${signal.category}"
                        },
                        detail = "probability=${signal.scamProbability}, backend=${signal.backend}",
                    ),
                )
            }
            if (indicators.otpRequest) {
                add(Observation(ObservationKind.OTP_REQUEST, "Requests an OTP or PIN"))
            }
            if (indicators.credentialRequest) {
                add(Observation(ObservationKind.CREDENTIAL_REQUEST, "Requests account credentials"))
            }
            if (indicators.paymentRequest) {
                add(Observation(ObservationKind.PAYMENT_REQUEST, "Requests a payment"))
            }
            /*
             * A payment request that names where the money should go is a stronger fact than
             * either half alone, and it is the same fact the QR path already reports for a
             * `upi://pay` payload — so it reuses that observation rather than inventing one.
             *
             * Both halves are required. A UPI ID on its own is an everyday thing: shops,
             * invoices and signatures carry them, and flagging every message that mentions
             * one would be the kind of warning users learn to ignore.
             */
            if (indicators.paymentRequest && indicators.upiIds.isNotEmpty()) {
                add(
                    Observation(
                        kind = ObservationKind.UPI_PAYMENT_REQUEST,
                        summary = "Asks for a payment to a UPI ID that has not been verified",
                        detail = indicators.upiIds.first(),
                    ),
                )
            }
            if (indicators.impersonatedOrganisation != null) {
                add(
                    Observation(
                        kind = ObservationKind.ORGANISATION_IMPERSONATION,
                        summary = "Claims to be ${indicators.impersonatedOrganisation}",
                        detail = indicators.impersonatedOrganisation,
                    ),
                )
            }
            if (indicators.urgency) {
                add(Observation(ObservationKind.URGENCY, "Creates urgency or time pressure"))
            }
            if (indicators.accountThreat) {
                add(Observation(ObservationKind.ACCOUNT_THREAT, "Threatens account suspension"))
            }
        }

        val derivedKinds = derived.mapTo(mutableSetOf()) { it.kind }
        val supplied = context.observations.filterNot { it.kind in derivedKinds }.distinctBy { it.kind }

        return (derived + supplied).sortedBy { it.kind.ordinal }
    }

    /**
     * One reason per scored input, so the user can see exactly what produced the number.
     *
     * Evidence is listed even when it contributed zero points: telling the user that a
     * provider had no report — or could not be reached — is part of not overstating safety.
     */
    /**
     * Scores external reports with diminishing corroboration, returning what each individual
     * report contributed so the reasons can state the truth.
     *
     * Two rules, both about independence:
     *
     *  - **Corroboration is counted per provider, not per report.** One provider reporting
     *    three subjects is still one opinion; a second provider agreeing is new information.
     *  - **Only the strongest report from each provider counts.** Otherwise a provider that
     *    flags both a host and its parent domain would corroborate itself.
     *
     * Ordering is by provider name so the result cannot depend on lookup completion order.
     */
    private fun scoreEvidence(evidence: List<ExternalEvidence>): Map<ExternalEvidence, Int> {
        if (evidence.isEmpty()) return emptyMap()

        // The one report per provider that carries the most weight on its own.
        val strongestPerSource = evidence
            .groupBy { it.sourceName }
            .mapValues { (_, reports) ->
                reports.maxBy { RiskWeights.forEvidence(it.verdict) }
            }

        val counted = mutableMapOf<ReputationVerdict, Int>()
        val contributions = mutableMapOf<ExternalEvidence, Int>()

        strongestPerSource.values
            .sortedWith(compareBy({ it.sourceName }, { it.subject }))
            .forEach { report ->
                val index = counted.getOrDefault(report.verdict, 0)
                contributions[report] = RiskWeights.forEvidence(report.verdict, index)
                counted[report.verdict] = index + 1
            }

        // Reports that lost to a stronger one from the same provider still get shown, at 0.
        evidence.forEach { contributions.putIfAbsent(it, 0) }

        return contributions
    }

    private fun reasonsFor(
        observations: List<Observation>,
        evidence: List<ExternalEvidence>,
        evidenceContributions: Map<ExternalEvidence, Int>,
        mlProbability: Double?,
    ): List<String> {
        val observationReasons = observations.map { observation ->
            val points = RiskWeights.forObservation(observation.kind, mlProbability)
            "${observation.summary} (+$points)"
        }
        val evidenceReasons = evidence.map { report ->
            // The points actually credited to this report, which for a second agreeing
            // provider is less than the first. Stating the schedule's number instead would
            // make the reasons stop adding up to the score.
            val points = evidenceContributions[report] ?: 0
            when (report.verdict) {
                ReputationVerdict.CONFIRMED ->
                    "${report.sourceName} reports ${report.subject} as malicious (+$points)"
                ReputationVerdict.SUSPICIOUS ->
                    "${report.sourceName} reports indicators of concern for ${report.subject} " +
                        "(+$points)"
                ReputationVerdict.CONFLICTING ->
                    "Sources disagree about ${report.subject} (+$points)"
                ReputationVerdict.NO_DETECTION ->
                    "${report.sourceName} has no report for ${report.subject} — " +
                        "this is not a confirmation of safety (+0)"
                ReputationVerdict.UNKNOWN ->
                    "${report.sourceName} could not assess ${report.subject} — " +
                        "treated as unknown, not safe (+0)"
                ReputationVerdict.ERROR ->
                    "${report.sourceName} lookup failed for ${report.subject} — " +
                        "treated as unknown, not safe (+0)"
            }
        }
        return observationReasons + evidenceReasons
    }

    /**
     * Conclusions drawn from the combination of signals. Output only.
     *
     * Confidence in the *assessment* is reported separately from the risk itself, so a strong
     * verdict held on thin corroboration is visible rather than hidden.
     */
    private fun inferencesFor(
        context: InvestigationContext,
        observations: List<Observation>,
        evidence: List<ExternalEvidence>,
    ): List<Inference> = buildList {
        val kinds = observations.mapTo(mutableSetOf()) { it.kind }

        if (ObservationKind.ORGANISATION_IMPERSONATION in kinds &&
            (ObservationKind.OTP_REQUEST in kinds || ObservationKind.CREDENTIAL_REQUEST in kinds)
        ) {
            add(
                Inference(
                    "Consistent with an impersonation attempt aimed at obtaining " +
                        "authentication details.",
                ),
            )
        }
        if (ObservationKind.URGENCY in kinds && ObservationKind.ACCOUNT_THREAT in kinds) {
            add(Inference("Uses manufactured time pressure, a common coercion technique."))
        }
        if (ObservationKind.PAYMENT_REQUEST in kinds || ObservationKind.UPI_PAYMENT_REQUEST in kinds) {
            add(Inference("Seeks a payment to a destination that has not been independently verified."))
        }
        if (evidence.any { it.verdict == ReputationVerdict.CONFIRMED }) {
            add(Inference("An external source independently corroborates malicious activity."))
        }
        if (context.threatIntelUnavailable || evidence.any { it.verdict in UNUSABLE_VERDICTS }) {
            add(
                Inference(
                    "External verification was incomplete. This assessment rests on " +
                        "on-device analysis alone.",
                ),
            )
        }
        // Any content that scored at all deserves a stated conclusion, not just a number.
        if (isEmpty() && observations.isNotEmpty()) {
            add(
                Inference(
                    if (observations.size > 1) {
                        "Multiple independent risk signals occur together in this content."
                    } else {
                        "A risk signal was identified in this content."
                    },
                ),
            )
        }
    }

    /**
     * Confidence describes how well corroborated the assessment is — never how dangerous the
     * content is. [Confidence.HIGH] requires both a classifier signal and at least one usable
     * external report, and is withheld entirely whenever any lookup was unusable.
     */
    private fun confidenceOf(
        context: InvestigationContext,
        observations: List<Observation>,
        evidence: List<ExternalEvidence>,
        degraded: Boolean,
    ): Confidence {
        if (observations.isEmpty() && evidence.isEmpty()) return Confidence.UNKNOWN

        val hasMlSignal = context.mlSignal != null

        // Independent providers that actually answered, and whether they told the same story.
        val answered = evidence
            .filter { it.verdict in USABLE_VERDICTS }
            .distinctBy { it.sourceName }
        val positive = answered.count { it.verdict != ReputationVerdict.NO_DETECTION }
        val sourcesDisagree = answered.size > 1 && positive > 0 && positive < answered.size

        return when {
            // Something could not be consulted, so the picture is incomplete by definition.
            degraded ->
                if (hasMlSignal || observations.size >= 2) Confidence.MEDIUM else Confidence.LOW

            // Providers that answered told different stories. The risk may still be high;
            // how well it is corroborated is not, and the two are reported separately.
            sourcesDisagree -> Confidence.MEDIUM

            // Two or more independent sources agreeing is the strongest position available.
            answered.size >= 2 -> Confidence.HIGH

            answered.size == 1 -> if (hasMlSignal) Confidence.HIGH else Confidence.MEDIUM

            // Local analysis only. Several independent local signals agreeing is worth more
            // than one, but none of it has been corroborated off the device.
            hasMlSignal && observations.size >= 3 -> Confidence.MEDIUM
            hasMlSignal || observations.size >= 2 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }

    /**
     * Safe actions only. Every entry declines or defers an action; none of them opens, pays,
     * sends or installs anything, so the user always remains the actor.
     */
    private fun actionsFor(
        indicators: Indicators,
        observations: List<Observation>,
        evidence: List<ExternalEvidence>,
        level: RiskLevel,
    ): List<SafeAction> {
        val kinds = observations.mapTo(mutableSetOf()) { it.kind }

        /*
         * An external provider positively reporting the destination is a link concern in its
         * own right, independent of the band.
         *
         * Without this, a domain that VirusTotal and URLhaus both call malicious but which
         * looks structurally ordinary scores 35 — GUARDED — and the user is told only to
         * "verify through the official channel", with nothing said about the link itself.
         * That is a gap in the advice, not in the arithmetic, so it is fixed here in the
         * safe-action mapping and no weight in RiskWeights is touched.
         */
        val externallyFlaggedDestination = evidence.any {
            it.verdict == ReputationVerdict.CONFIRMED || it.verdict == ReputationVerdict.SUSPICIOUS
        }

        val specific = buildList {
            if (ObservationKind.OTP_REQUEST in kinds) add(SafeAction.DO_NOT_SHARE_OTP)
            if (ObservationKind.CREDENTIAL_REQUEST in kinds) add(SafeAction.DO_NOT_SHARE_CREDENTIALS)
            /*
             * Warn about paying only when the message is actually about a payment. A UPI ID
             * on its own is not a payment context — it appears in signatures, invoices and
             * ordinary conversation, and it appeared in TrustLens's own advice ("do not send
             * money to X"), which previously answered itself with "Do not pay".
             *
             * The band fallback keeps the conservative behaviour where it earns its place: if
             * the message is independently MEDIUM or worse, a payment destination inside it is
             * worth flagging even when no phrase parsed as a demand. This mirrors how
             * DO_NOT_OPEN_LINK treats links below.
             */
            if (ObservationKind.PAYMENT_REQUEST in kinds ||
                ObservationKind.UPI_PAYMENT_REQUEST in kinds ||
                (indicators.upiIds.isNotEmpty() && level >= RiskLevel.MEDIUM)
            ) {
                add(SafeAction.DO_NOT_PAY)
            }
            // Warn about a link when something is actually wrong with it, or when the
            // overall risk is already meaningful. The mere presence of a link is not enough:
            // telling people not to open ordinary links is how a warning stops being read.
            if (LINK_CONCERNS.any { it in kinds } ||
                externallyFlaggedDestination ||
                (indicators.urls.isNotEmpty() && level >= RiskLevel.MEDIUM)
            ) {
                add(SafeAction.DO_NOT_OPEN_LINK)
            }
        }

        // Nothing specific to warn about and nothing much to worry about.
        if (specific.isEmpty() && level == RiskLevel.LOW) {
            return listOf(SafeAction.PROCEED_CAREFULLY)
        }

        return buildList {
            addAll(specific)
            add(SafeAction.VERIFY_VIA_OFFICIAL_CHANNEL)
            if (level >= RiskLevel.HIGH) add(SafeAction.REPORT_OR_BLOCK)
        }
    }

    private companion object {
        val USABLE_VERDICTS = setOf(
            ReputationVerdict.CONFIRMED,
            ReputationVerdict.SUSPICIOUS,
            ReputationVerdict.NO_DETECTION,
        )
        /**
         * Observations that say something is wrong with a link specifically.
         *
         * Membership here drives the "do not open the link" advice independently of the
         * numerical band, so a link with a real structural problem is always warned about
         * even when the total score is modest.
         */
        val LINK_CONCERNS = setOf(
            ObservationKind.SUSPICIOUS_URL,
            ObservationKind.URL_SHORTENER,
            ObservationKind.IP_ADDRESS_URL,
            ObservationKind.PUNYCODE_DOMAIN,
            ObservationKind.DOMAIN_ORGANISATION_MISMATCH,
            ObservationKind.EMBEDDED_CREDENTIALS,
            ObservationKind.EXECUTABLE_DOWNLOAD_PATH,
            ObservationKind.DEEP_SUBDOMAIN,
            ObservationKind.NON_STANDARD_PORT,
        )
        val UNUSABLE_VERDICTS = setOf(
            ReputationVerdict.UNKNOWN,
            ReputationVerdict.ERROR,
            ReputationVerdict.CONFLICTING,
        )
    }
}
