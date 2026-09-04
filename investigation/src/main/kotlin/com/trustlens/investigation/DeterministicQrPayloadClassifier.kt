package com.trustlens.investigation

import com.trustlens.domain.QrPayload
import com.trustlens.domain.QrPayloadClassifier
import com.trustlens.domain.UpiPaymentRequest

/**
 * Classifies a decoded QR payload by string inspection alone.
 *
 * Nothing here contacts, resolves or expands anything. A payload is a string that arrived from
 * a photograph of a printed square; it is inspected, split into fields, and handed on.
 *
 * Parsing is hand-written rather than delegated to `java.net.URI`, for the same reason the URL
 * analyzer is: `URI` throws on the malformed input this layer exists to survive, and a scanner
 * that crashes on a bad QR code is worse than one that calls it text.
 *
 * Misclassification is designed to fail safe. A link that is not recognised as one falls
 * through to [QrPayload.Text], where the indicator extractor still finds URLs in the content —
 * so the worst case is a longer path to the same analysis, never a skipped one.
 */
class DeterministicQrPayloadClassifier : QrPayloadClassifier {

    override fun classify(raw: String): QrPayload {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return QrPayload.Empty

        val scheme = schemeOf(trimmed)?.lowercase()
        val params by lazy { queryParametersOf(trimmed) }

        return when {
            // A UPI intent, or a payment-app scheme that actually carries a payee address.
            scheme == UPI_SCHEME ||
                (scheme in PAYMENT_APP_SCHEMES && params.containsKey(PAYEE_ADDRESS)) ->
                QrPayload.Upi(raw = trimmed, request = upiRequestOf(params))

            scheme in WEB_SCHEMES -> QrPayload.Url(raw = trimmed, url = trimmed)

            // Any other explicit scheme (mailto:, tel:, WIFI:, otpauth:, geo:) stays inert
            // text. Recognising more schemes would mean handling more of them, and TrustLens
            // has no business acting on any of them.
            scheme != null -> QrPayload.Text(trimmed)

            looksLikeBareUrl(trimmed) -> QrPayload.Url(raw = trimmed, url = trimmed)

            else -> QrPayload.Text(trimmed)
        }
    }

    /** The scheme of `scheme:rest`, or null when there is no syntactically valid one. */
    private fun schemeOf(text: String): String? {
        val colon = text.indexOf(':')
        if (colon <= 0) return null
        val candidate = text.substring(0, colon)
        if (!candidate.first().isLetter()) return null
        if (!candidate.all { it.isLetterOrDigit() || it in "+-." }) return null
        return candidate
    }

    /**
     * Query parameters of a payload, first occurrence winning.
     *
     * Duplicate keys are a known trick for making a human-readable QR differ from the one an
     * app parses; taking the first value consistently at least makes what TrustLens shows and
     * what it scores the same value.
     */
    private fun queryParametersOf(text: String): Map<String, String> {
        val start = text.indexOf('?')
        if (start < 0 || start == text.lastIndex) return emptyMap()

        val query = text.substring(start + 1).substringBefore('#')
        val parameters = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val separator = pair.indexOf('=')
            val key = if (separator < 0) pair else pair.substring(0, separator)
            val value = if (separator < 0) "" else pair.substring(separator + 1)
            val decodedKey = percentDecode(key).lowercase()
            if (decodedKey.isNotEmpty() && decodedKey !in parameters) {
                parameters[decodedKey] = percentDecode(value)
            }
        }
        return parameters
    }

    private fun upiRequestOf(params: Map<String, String>) = UpiPaymentRequest(
        payeeAddress = params.nonBlank(PAYEE_ADDRESS),
        payeeName = params.nonBlank("pn"),
        amount = params.nonBlank("am"),
        currency = params.nonBlank("cu"),
        merchantCode = params.nonBlank("mc"),
        transactionNote = params.nonBlank("tn"),
        transactionRef = params.nonBlank("tr"),
        url = params.nonBlank("url"),
    )

    private fun Map<String, String>.nonBlank(key: String): String? =
        this[key]?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Percent-decoding that cannot fail.
     *
     * A malformed escape is left exactly as written rather than throwing, because the input is
     * a photograph of a printed code and partially damaged payloads are routine.
     */
    private fun percentDecode(text: String): String {
        if ('%' !in text && '+' !in text) return text

        val bytes = ArrayList<Byte>(text.length)
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character == '%' && index + 2 < text.length -> {
                    val hex = text.substring(index + 1, index + 3)
                    val value = hex.toIntOrNull(radix = 16)
                    if (value == null) {
                        bytes.add(character.code.toByte())
                        index++
                    } else {
                        bytes.add(value.toByte())
                        index += 3
                    }
                }
                character == '+' -> {
                    bytes.add(' '.code.toByte())
                    index++
                }
                else -> {
                    character.toString().toByteArray(Charsets.UTF_8).forEach(bytes::add)
                    index++
                }
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    /**
     * Whether a scheme-less payload is confidently a web address.
     *
     * Deliberately conservative. `www.`-prefixed hosts are unambiguous; otherwise a common TLD
     * is required, so ordinary sentences containing a full stop are not paraded as links.
     */
    private fun looksLikeBareUrl(text: String): Boolean {
        if (text.any { it.isWhitespace() }) return false
        val host = text.substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':')
        if (host.isEmpty() || '.' !in host) return false
        if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return false

        if (host.lowercase().startsWith("www.")) return true

        val tld = host.substringAfterLast('.').lowercase()
        return tld in COMMON_TLDS
    }

    private companion object {
        const val UPI_SCHEME = "upi"
        const val PAYEE_ADDRESS = "pa"

        /**
         * Indian payment apps register their own schemes and carry the same `pa`/`am` fields.
         * They only count as payment payloads when a payee address is actually present.
         */
        val PAYMENT_APP_SCHEMES = setOf("tez", "phonepe", "paytmmp", "gpay", "bhim", "credpay")

        val WEB_SCHEMES = setOf("http", "https")

        /**
         * A small, high-traffic list. Recognising fewer TLDs than exist is intentional: an
         * unrecognised host is analysed as text, where URL extraction still runs.
         */
        val COMMON_TLDS = setOf(
            "com", "net", "org", "in", "io", "co", "app", "dev", "me", "info", "biz",
            "xyz", "site", "online", "shop", "store", "live", "link", "page", "top",
            "club", "vip", "cc", "tk", "ly", "gl", "gov", "edu", "ac", "uk", "us",
            "ru", "cn", "de", "fr", "jp", "au", "ca", "br", "za", "ae", "sg",
        )
    }
}
