package com.trustlens.riskengine

import com.trustlens.domain.ObservationKind
import com.trustlens.domain.ReputationVerdict

/**
 * The complete scoring table for TrustLens.
 *
 * Every number that can affect a risk score lives here, in one readable place, so the score
 * can be explained to a user and audited by a reviewer. Nothing outside this file may
 * contribute points.
 *
 * Weights are additive and then capped at 100. They are deliberately not probabilities: the
 * engine is a transparent rubric, not a model, and Architecture Rule 3 requires that the
 * final number come from code like this rather than from an LLM.
 */
object RiskWeights {

    /** Maximum contribution of the on-device classifier, scaled by its probability. */
    const val ML_SIGNAL_MAX = 30

    // Social-engineering requests: the strongest local signals, because a legitimate
    // organisation never asks for these over an unsolicited message. An unsolicited request
    // for an OTP is the single most reliable fraud indicator in Indian SMS scams, and was
    // previously weighted below the combination of two weaker identity signals.
    const val OTP_REQUEST = 22
    const val CREDENTIAL_REQUEST = 18
    const val PAYMENT_REQUEST = 15

    // Identity and pressure signals.
    const val ORGANISATION_IMPERSONATION = 12
    const val DOMAIN_ORGANISATION_MISMATCH = 12
    const val URGENCY = 10
    const val ACCOUNT_THREAT = 10

    // URL and destination signals, supplied by URL analysis. Each is an independent
    // property of a link and is scored on its own; ordering reflects how strongly the
    // property implies an intent to deceive rather than how unusual it is. Hiding a
    // destination behind an `@` is almost never innocent; a non-standard port often is.
    const val PUNYCODE_DOMAIN = 14
    const val EMBEDDED_CREDENTIALS = 14
    const val IP_ADDRESS_URL = 12
    const val EXECUTABLE_DOWNLOAD_PATH = 12
    const val SUSPICIOUS_URL = 10
    const val DEEP_SUBDOMAIN = 8
    const val URL_SHORTENER = 6
    const val NON_STANDARD_PORT = 5
    const val UPI_PAYMENT_REQUEST = 10

    const val VOICE_SCAM_LANGUAGE = 8

    /**
     * External reputation, scored with diminishing corroboration.
     *
     * The first provider to positively report a subject carries the most weight, because it
     * is the only evidence corroborated outside the device. A second *independent* provider
     * agreeing is genuinely stronger — but two reports are not worth twice one, and ten
     * would not be worth ten times one. The schedule below encodes that: first report at
     * full weight, each further independent source adding progressively less.
     *
     * Sources are counted by provider name, so one provider reporting several subjects
     * never corroborates itself.
     */
    val EVIDENCE_CONFIRMED_SCHEDULE = listOf(40, 25, 10)
    val EVIDENCE_SUSPICIOUS_SCHEDULE = listOf(18, 10, 5)
    const val EVIDENCE_CONFLICTING = 10

    /** What any further agreeing source beyond the schedule adds. */
    const val EVIDENCE_ADDITIONAL_SOURCE = 5

    /**
     * Points contributed by one observation.
     *
     * [ObservationKind.ML_SCAM_SIGNAL] is scaled by the classifier's probability where one is
     * available; a bare ML observation with no probability attached counts at full weight.
     */
    fun forObservation(kind: ObservationKind, mlProbability: Double?): Int = when (kind) {
        ObservationKind.ML_SCAM_SIGNAL ->
            if (mlProbability != null) (mlProbability * ML_SIGNAL_MAX).toInt() else ML_SIGNAL_MAX
        ObservationKind.OTP_REQUEST -> OTP_REQUEST
        ObservationKind.CREDENTIAL_REQUEST -> CREDENTIAL_REQUEST
        ObservationKind.PAYMENT_REQUEST -> PAYMENT_REQUEST
        ObservationKind.ORGANISATION_IMPERSONATION -> ORGANISATION_IMPERSONATION
        ObservationKind.DOMAIN_ORGANISATION_MISMATCH -> DOMAIN_ORGANISATION_MISMATCH
        ObservationKind.URGENCY -> URGENCY
        ObservationKind.ACCOUNT_THREAT -> ACCOUNT_THREAT
        ObservationKind.PUNYCODE_DOMAIN -> PUNYCODE_DOMAIN
        ObservationKind.IP_ADDRESS_URL -> IP_ADDRESS_URL
        ObservationKind.SUSPICIOUS_URL -> SUSPICIOUS_URL
        ObservationKind.URL_SHORTENER -> URL_SHORTENER
        ObservationKind.EMBEDDED_CREDENTIALS -> EMBEDDED_CREDENTIALS
        ObservationKind.EXECUTABLE_DOWNLOAD_PATH -> EXECUTABLE_DOWNLOAD_PATH
        ObservationKind.DEEP_SUBDOMAIN -> DEEP_SUBDOMAIN
        ObservationKind.NON_STANDARD_PORT -> NON_STANDARD_PORT
        ObservationKind.UPI_PAYMENT_REQUEST -> UPI_PAYMENT_REQUEST
        ObservationKind.VOICE_SCAM_LANGUAGE -> VOICE_SCAM_LANGUAGE
    }

    /**
     * Points contributed by one external report, given how many independent sources already
     * agreed with it.
     *
     * [agreeingSourceIndex] is 0 for the first source to give this verdict, 1 for the second,
     * and so on.
     */
    fun forEvidence(verdict: ReputationVerdict, agreeingSourceIndex: Int): Int = when (verdict) {
        ReputationVerdict.CONFIRMED -> EVIDENCE_CONFIRMED_SCHEDULE
            .getOrElse(agreeingSourceIndex) { EVIDENCE_ADDITIONAL_SOURCE }
        ReputationVerdict.SUSPICIOUS -> EVIDENCE_SUSPICIOUS_SCHEDULE
            .getOrElse(agreeingSourceIndex) { EVIDENCE_ADDITIONAL_SOURCE }
        // Sources disagreeing is one fact, however many of them disagree.
        ReputationVerdict.CONFLICTING -> if (agreeingSourceIndex == 0) EVIDENCE_CONFLICTING else 0
        ReputationVerdict.NO_DETECTION -> 0
        ReputationVerdict.UNKNOWN -> 0
        ReputationVerdict.ERROR -> 0
    }

    /**
     * Points contributed by one external reputation report.
     *
     * Principle 2 lives here: [ReputationVerdict.NO_DETECTION], [ReputationVerdict.UNKNOWN]
     * and [ReputationVerdict.ERROR] contribute exactly zero. They never *subtract*, so a
     * silent, offline or rate-limited provider can never launder risky content into a lower
     * score. "We found nothing" is not "there is nothing".
     */
    fun forEvidence(verdict: ReputationVerdict): Int = forEvidence(verdict, agreeingSourceIndex = 0)
}
