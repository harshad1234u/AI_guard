package com.trustlens.domain

/**
 * TrustLens keeps three kinds of statement strictly apart, because conflating them is how a
 * scam detector ends up asserting things it cannot support:
 *
 *  - [Observation]      what TrustLens itself saw in the content (local, always available)
 *  - [ExternalEvidence] what an outside source reported (may be absent, stale, or in error)
 *  - [Inference]        what TrustLens concluded from the combination
 *
 * Observations and external evidence are *inputs* to the risk engine. Inferences are
 * *outputs*. An inference is never scored, so a conclusion can never inflate the score that
 * produced it.
 */
sealed interface Finding {
    val summary: String
}

/** Something directly present in the analysed content. Carries no external claim. */
data class Observation(
    val kind: ObservationKind,
    override val summary: String,
    /** The exact snippet or value observed, for display as evidence. Untrusted content. */
    val detail: String? = null,
) : Finding

enum class ObservationKind {
    ML_SCAM_SIGNAL,
    URGENCY,
    OTP_REQUEST,
    PAYMENT_REQUEST,
    CREDENTIAL_REQUEST,
    ACCOUNT_THREAT,
    ORGANISATION_IMPERSONATION,
    /**
     * A structural oddity with no more specific kind of its own. Kept as the fallback for
     * findings that have not earned individual weighting.
     */
    SUSPICIOUS_URL,
    DOMAIN_ORGANISATION_MISMATCH,
    URL_SHORTENER,
    IP_ADDRESS_URL,
    PUNYCODE_DOMAIN,

    /*
     * These four were previously merged into SUSPICIOUS_URL, which meant a link that buried
     * a brand in deep subdomains AND served an installable file AND used an odd port scored
     * exactly the same as a link doing any one of them. They are independent properties of a
     * URL and are now observed — and weighted — independently.
     */

    /** Credentials before an `@`, hiding the real host behind a familiar-looking one. */
    EMBEDDED_CREDENTIALS,

    /** The path points at an installable or executable file. */
    EXECUTABLE_DOWNLOAD_PATH,

    /** Unusually deep subdomain nesting, often used to bury a brand in a fake host. */
    DEEP_SUBDOMAIN,

    /** An explicit port other than the scheme default. */
    NON_STANDARD_PORT,
    UPI_PAYMENT_REQUEST,
    VOICE_SCAM_LANGUAGE,
}

/**
 * A report from an external reputation source.
 *
 * A source that returns [ReputationVerdict.NO_DETECTION], [ReputationVerdict.UNKNOWN] or
 * [ReputationVerdict.ERROR] still produces evidence — evidence of *absence of information*.
 * It must never be silently dropped, because dropping it is how "we don't know" turns into
 * "it's safe".
 */
data class ExternalEvidence(
    val sourceName: String,
    val verdict: ReputationVerdict,
    val subject: String,
    override val summary: String,
    /**
     * Provider-specific facts worth showing, such as VirusTotal's detection counts.
     *
     * Deliberately a flat map of already-formatted strings rather than a raw response
     * body: provider output is untrusted external data, and normalising it at the edge
     * means nothing downstream ever has to interpret a provider's own JSON.
     */
    val details: Map<String, String> = emptyMap(),
) : Finding

/** A conclusion drawn by TrustLens. Output only — never an input to scoring. */
data class Inference(
    override val summary: String,
) : Finding

/**
 * Result states for a reputation lookup.
 *
 * Principle 2 is encoded here: there is no SAFE case. The strongest negative result a source
 * can give is [NO_DETECTION] — "this source has no report" — which is not a clean bill of
 * health and never reduces a risk score.
 */
enum class ReputationVerdict {
    /** The source positively reports the subject as malicious. */
    CONFIRMED,

    /** The source reports indicators of concern short of confirmation. */
    SUSPICIOUS,

    /** The source responded, and holds no report for this subject. NOT a safety guarantee. */
    NO_DETECTION,

    /** No usable answer: unsupported subject type, or no provider configured. */
    UNKNOWN,

    /** The lookup failed: offline, rate-limited, timed out, or malformed response. */
    ERROR,

    /** Multiple sources disagreed materially. */
    CONFLICTING,
}
