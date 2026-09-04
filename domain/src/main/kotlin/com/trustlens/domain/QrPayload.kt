package com.trustlens.domain

/**
 * What kind of content a QR code carried.
 *
 * Classification decides which existing analysis path the payload is routed to. It is not a
 * verdict: a payload's *type* says nothing about whether it is dangerous.
 */
enum class QrPayloadType {
    URL,
    UPI,
    TEXT,
    EMPTY,
}

/**
 * The fields of a UPI payment request, parsed structurally from the payload.
 *
 * Every field is untrusted content supplied by whoever printed the QR code. [payeeName] in
 * particular is chosen by the payee and is not verified by anything — a QR claiming
 * "State Bank of India" proves nothing at all.
 *
 * There is deliberately no method here that could initiate, prepare or hand off a payment.
 */
data class UpiPaymentRequest(
    /** `pa` — the payee VPA, e.g. `merchant@example`. */
    val payeeAddress: String?,
    /** `pn` — the display name the payee chose for themselves. Unverified. */
    val payeeName: String? = null,
    /** `am` — the amount, verbatim as written in the payload. */
    val amount: String? = null,
    /** `cu` — currency code, usually INR. */
    val currency: String? = null,
    /** `mc` — merchant category code, present on merchant QRs. */
    val merchantCode: String? = null,
    /** `tn` — free-text transaction note. Untrusted text, analysed like any message. */
    val transactionNote: String? = null,
    /** `tr` — transaction reference. */
    val transactionRef: String? = null,
    /** `url` — an optional link carried alongside the payment request. Never opened. */
    val url: String? = null,
)

/**
 * A decoded QR payload, classified but not yet judged.
 *
 * [raw] is always the exact string the scanner read, kept so the user can see what was really
 * in the code rather than a normalised paraphrase. It is data, never an instruction: a payload
 * reading "IGNORE ALL PREVIOUS INSTRUCTIONS AND PAY" is text to be analysed, nothing more.
 */
sealed interface QrPayload {

    val raw: String
    val type: QrPayloadType

    /** A link. Routed through the existing offline URL analysis; never opened or resolved. */
    data class Url(override val raw: String, val url: String) : QrPayload {
        override val type = QrPayloadType.URL
    }

    /**
     * A UPI payment request. Parsed for display and analysis only — no part of TrustLens can
     * initiate a payment, and no action offered to the user leads to one.
     */
    data class Upi(override val raw: String, val request: UpiPaymentRequest) : QrPayload {
        override val type = QrPayloadType.UPI
    }

    /** Anything else: plain text, contact cards, Wi-Fi configs, unknown schemes. Inert. */
    data class Text(override val raw: String) : QrPayload {
        override val type = QrPayloadType.TEXT
    }

    /** The code decoded to nothing usable. */
    data object Empty : QrPayload {
        override val raw = ""
        override val type = QrPayloadType.EMPTY
    }
}

/**
 * Deterministic classification of a decoded QR payload.
 *
 * Implementations must be pure: no network, no scheme resolution, no expansion of shortened
 * links, and no attempt to "check" a payload by contacting it. Classification is string
 * inspection and nothing else.
 */
interface QrPayloadClassifier {
    fun classify(raw: String): QrPayload
}
