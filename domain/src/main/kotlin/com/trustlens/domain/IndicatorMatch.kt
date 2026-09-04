package com.trustlens.domain

/** The category of a single extracted indicator. */
enum class IndicatorKind {
    URL,
    DOMAIN,
    PHONE_NUMBER,
    UPI_ID,
    EMAIL,
    AMOUNT,
    ORGANISATION,
    OTP_REFERENCE,
    URGENCY_PHRASE,
    CREDENTIAL_REFERENCE,
    PAYMENT_PHRASE,
    ACCOUNT_THREAT_PHRASE,
}

/**
 * One indicator, tied to the exact span of source text it came from.
 *
 * Keeping [startIndex]/[endIndex] lets the result screen quote the original message rather
 * than a paraphrase, so a user can see precisely which words drove the score. [text] is
 * untrusted content copied verbatim: display it, never act on it.
 */
data class IndicatorMatch(
    val kind: IndicatorKind,
    val text: String,
    val startIndex: Int,
    val endIndex: Int,
)
