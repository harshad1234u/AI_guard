package com.trustlens.investigation

import com.trustlens.domain.IndicatorExtractor
import com.trustlens.domain.IndicatorKind
import com.trustlens.domain.IndicatorMatch
import com.trustlens.domain.Indicators

/**
 * Deterministic, rule-based indicator extraction.
 *
 * Pure string and regex work: no model, no network, and — importantly — no URL resolution.
 * Extracted URLs are copied verbatim and never opened, fetched, expanded or DNS-resolved.
 * The risk engine treats these values as facts about the content, so a hallucinating model
 * must never be substituted here.
 *
 * All input is untrusted (Principle 1). It is scanned as data and quoted back as evidence;
 * nothing in it is ever interpreted as an instruction.
 */
class RuleBasedIndicatorExtractor : IndicatorExtractor {

    override fun extract(text: String): Indicators {
        if (text.isBlank()) return Indicators()

        val matches = mutableListOf<IndicatorMatch>()

        val urls = extractUrls(text, matches)
        val domains = urls.mapNotNull { hostOf(it) }.distinct()
        domains.forEach { host ->
            // The domain list is canonicalised to lower case, but the match must quote the
            // source verbatim so the span and the text it reports always agree.
            val owner = urls.first { hostOf(it) == host }
            val urlStart = text.indexOf(owner)
            val hostOffset = owner.indexOf(host, ignoreCase = true)
            if (urlStart < 0 || hostOffset < 0) return@forEach

            val start = urlStart + hostOffset
            val end = start + host.length
            matches += IndicatorMatch(IndicatorKind.DOMAIN, text.substring(start, end), start, end)
        }

        val emails = collect(text, EMAIL, IndicatorKind.EMAIL, matches)
        val upiIds = collect(text, UPI_ID, IndicatorKind.UPI_ID, matches)
        val phoneNumbers = collect(text, PHONE, IndicatorKind.PHONE_NUMBER, matches)
        val amounts = collect(text, AMOUNT_PREFIXED, IndicatorKind.AMOUNT, matches) +
            collect(text, AMOUNT_SUFFIXED, IndicatorKind.AMOUNT, matches)
        val organisations = extractOrganisations(text, matches)

        val urgency = collect(text, URGENCY, IndicatorKind.URGENCY_PHRASE, matches).isNotEmpty()
        val accountThreat =
            collect(text, ACCOUNT_THREAT, IndicatorKind.ACCOUNT_THREAT_PHRASE, matches).isNotEmpty()

        val otpRequest = isRequested(text, OTP_REFERENCE, IndicatorKind.OTP_REFERENCE, matches)
        val credentialRequest =
            isRequested(text, CREDENTIAL_REFERENCE, IndicatorKind.CREDENTIAL_REFERENCE, matches)
        val paymentRequest = isPaymentRequested(text, matches)

        val coerced = urgency || accountThreat || otpRequest || credentialRequest || paymentRequest

        return Indicators(
            urls = urls,
            domains = domains,
            phoneNumbers = phoneNumbers,
            upiIds = upiIds,
            emails = emails,
            organisations = organisations,
            amounts = amounts.distinct(),
            requestedActions = requestedActions(text, otpRequest, credentialRequest, paymentRequest),
            urgency = urgency,
            otpRequest = otpRequest,
            paymentRequest = paymentRequest,
            credentialRequest = credentialRequest,
            accountThreat = accountThreat,
            // A named organisation only counts as impersonation alongside a coercive ask;
            // "your SBI statement is ready" is not an impersonation signal on its own.
            impersonatedOrganisation = organisations.firstOrNull().takeIf { coerced },
            matches = matches.sortedWith(compareBy({ it.startIndex }, { it.kind.ordinal })),
        )
    }

    // --- URLs -------------------------------------------------------------------------

    /**
     * Scheme-qualified URLs are matched first and their spans reserved, so a bare-domain rule
     * cannot re-match the host inside a URL that was already captured.
     */
    private fun extractUrls(text: String, sink: MutableList<IndicatorMatch>): List<String> {
        val claimed = mutableListOf<IntRange>()
        val found = LinkedHashMap<String, IndicatorMatch>()

        listOf(URL_WITH_SCHEME, URL_WWW, URL_BARE_DOMAIN).forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                if (claimed.any { match.range.first in it }) return@forEach

                val trimmed = match.value.trimEnd(*TRAILING_PUNCTUATION)
                if (!isUsableUrl(trimmed)) return@forEach

                claimed += match.range
                found.putIfAbsent(
                    trimmed,
                    IndicatorMatch(
                        kind = IndicatorKind.URL,
                        text = trimmed,
                        startIndex = match.range.first,
                        endIndex = match.range.first + trimmed.length,
                    ),
                )
            }
        }

        sink += found.values
        return found.keys.toList()
    }

    /** Rejects a scheme with nothing after it, e.g. a bare `http://`. */
    private fun isUsableUrl(candidate: String): Boolean {
        val host = hostOf(candidate) ?: return false
        return host.isNotBlank() && host.contains('.')
    }

    /** Pure string parsing — deliberately not [java.net.URI], which throws on malformed input. */
    private fun hostOf(url: String): String? {
        val afterScheme = url.substringAfter("://", url)
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.lowercase().ifBlank { null }
    }

    // --- Organisations ----------------------------------------------------------------

    private fun extractOrganisations(
        text: String,
        sink: MutableList<IndicatorMatch>,
    ): List<String> {
        val found = LinkedHashMap<String, IndicatorMatch>()

        ORGANISATIONS.forEach { (canonical, pattern) ->
            pattern.find(text)?.let { match ->
                found.putIfAbsent(
                    canonical,
                    IndicatorMatch(
                        kind = IndicatorKind.ORGANISATION,
                        text = match.value,
                        startIndex = match.range.first,
                        endIndex = match.range.last + 1,
                    ),
                )
            }
        }

        sink += found.values
        return found.keys.toList()
    }

    // --- Request detection ------------------------------------------------------------

    /**
     * A sensitive term only counts as a *request* when an asking verb sits near it and that
     * verb is not negated.
     *
     * This is what separates "share your OTP immediately" from a genuine bank message like
     * "123456 is your OTP. Do not share it with anyone." — both contain "OTP" and "share",
     * and only the first is an attack.
     */
    private fun isRequested(
        text: String,
        reference: Regex,
        kind: IndicatorKind,
        sink: MutableList<IndicatorMatch>,
    ): Boolean {
        val references = collectMatches(text, reference, kind)
        sink += references

        return references.any { reference0 ->
            REQUEST_VERB.findAll(text).any { verb ->
                val distance = kotlin.math.min(
                    kotlin.math.abs(verb.range.first - reference0.startIndex),
                    kotlin.math.abs(verb.range.first - reference0.endIndex),
                )
                distance <= REQUEST_WINDOW && !isNegated(text, verb.range.first)
            }
        }
    }

    /**
     * A payment phrase only counts as a *request* when it is neither negated nor describing
     * a payment that already happened.
     *
     * Without the first guard, TrustLens scores its own advice: "Do not send money to
     * fraudster@ybl" is the warning, not the attack. Without the second, a receipt —
     * "Previous payment was sent to merchant@example" — reads as a fresh demand.
     */
    private fun isPaymentRequested(text: String, sink: MutableList<IndicatorMatch>): Boolean {
        val phrases = collectMatches(text, PAYMENT_PHRASE, IndicatorKind.PAYMENT_PHRASE)
        sink += phrases

        return phrases.any { phrase ->
            !isNegated(text, phrase.startIndex) && !isAlreadySettled(text, phrase)
        }
    }

    /**
     * True when the text around [phrase] describes a completed payment rather than a
     * requested one.
     *
     * Deliberately narrow: it matches explicit completion wording near the phrase, not tense
     * in general. Regexes cannot parse tense, and a guard that tried would start silencing
     * real demands.
     */
    private fun isAlreadySettled(text: String, phrase: IndicatorMatch): Boolean {
        val start = (phrase.startIndex - COMPLETION_WINDOW).coerceAtLeast(0)
        val end = (phrase.endIndex + COMPLETION_WINDOW).coerceAtMost(text.length)
        return PAYMENT_COMPLETED.containsMatchIn(text.substring(start, end))
    }

    /** True when a negation ("do not", "never", "will never ask") shortly precedes [index]. */
    private fun isNegated(text: String, index: Int): Boolean {
        val windowStart = (index - NEGATION_WINDOW).coerceAtLeast(0)
        return NEGATION.containsMatchIn(text.substring(windowStart, index))
    }

    private fun requestedActions(
        text: String,
        otpRequest: Boolean,
        credentialRequest: Boolean,
        paymentRequest: Boolean,
    ): List<String> = buildList {
        if (KYC.containsMatchIn(text)) add("complete_kyc")
        if (otpRequest) add("share_otp")
        if (credentialRequest) add("share_credentials")
        if (paymentRequest) add("make_payment")
        if (CLICK_LINK.containsMatchIn(text)) add("click_link")
        if (INSTALL_APP.containsMatchIn(text)) add("install_app")
    }

    // --- Shared helpers ---------------------------------------------------------------

    private fun collect(
        text: String,
        pattern: Regex,
        kind: IndicatorKind,
        sink: MutableList<IndicatorMatch>,
    ): List<String> {
        val found = collectMatches(text, pattern, kind)
        sink += found
        return found.map { it.text }.distinct()
    }

    private fun collectMatches(
        text: String,
        pattern: Regex,
        kind: IndicatorKind,
    ): List<IndicatorMatch> {
        val seen = LinkedHashMap<String, IndicatorMatch>()
        pattern.findAll(text).forEach { match ->
            val value = match.value.trim()
            if (value.isEmpty()) return@forEach
            val start = match.range.first + match.value.indexOf(value)
            seen.putIfAbsent(
                value,
                IndicatorMatch(kind, value, start, start + value.length),
            )
        }
        return seen.values.toList()
    }

    private companion object {
        val TRAILING_PUNCTUATION = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'')

        const val REQUEST_WINDOW = 60
        const val NEGATION_WINDOW = 30

        /** How far either side of a payment phrase to look for completion wording. */
        const val COMPLETION_WINDOW = 30

        // Restricting bare-domain matching to a known TLD list is what stops ordinary prose
        // ("blocked. Do not delay", "Rs.500") from being read as a link.
        // The five free-registration TLDs are listed for the opposite reason to the rest:
        // not because links there are common, but because they are disproportionately
        // hostile. Omitting them meant a bare `sbi.bank.tk/verify` was not recognised as a
        // link at all, so it reached no URL analysis and no reputation lookup — while the
        // same string inside a QR code did, because DeterministicQrPayloadClassifier already
        // knew `tk`. The two surfaces now agree.
        const val TLDS =
            "com|org|net|in|io|co|me|app|xyz|top|info|biz|ru|cn|site|online|link|click|" +
                "live|shop|store|icu|vip|work|help|fit|pro|dev|tech|space|website|" +
                "tk|ml|ga|cf|gq"

        val URL_WITH_SCHEME = Regex("""\bhttps?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

        val URL_WWW = Regex(
            """(?<![@\w.])www\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?""" +
                """(?:\.[A-Za-z0-9-]+)+(?::\d{1,5})?(?:/[^\s]*)?""",
            RegexOption.IGNORE_CASE,
        )

        val URL_BARE_DOMAIN = Regex(
            """(?<![@\w.\-/])(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+(?:$TLDS)\b""" +
                """(?::\d{1,5})?(?:/[^\s]*)?""",
            RegexOption.IGNORE_CASE,
        )

        val EMAIL = Regex(
            """(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*""" +
                """\.[A-Za-z]{2,}\b""",
        )

        // A UPI handle carries no dot (name@okhdfcbank); an email domain always does
        // (name@example.com). That single structural difference separates the two.
        // The trailing guards must reject an email domain (`@example.com`) while still
        // allowing a VPA that ends a sentence (`...to fraudster@ybl.`), so a bare full stop
        // is fine but a dot followed by more domain characters is not.
        val UPI_ID = Regex(
            """(?<![A-Za-z0-9._%+\-@])[A-Za-z0-9][A-Za-z0-9._-]{1,60}@[A-Za-z]{2,20}""" +
                """(?![A-Za-z0-9@-])(?!\.[A-Za-z0-9])""",
        )

        // Indian mobile: optional +91/0 prefix, then 10 digits starting 6-9. The digit
        // boundaries stop card numbers and OTP codes from matching.
        val PHONE = Regex("""(?<!\d)(?:\+?91[\s-]?|0)?[6-9]\d{4}[\s-]?\d{5}(?!\d)""")

        val AMOUNT_PREFIXED = Regex(
            """(?:₹|\bRs\.?|\bINR)\s?\d[\d,]*(?:\.\d{1,2})?""",
            RegexOption.IGNORE_CASE,
        )
        val AMOUNT_SUFFIXED = Regex(
            """\b\d[\d,]*(?:\.\d{1,2})?\s?(?:rupees|rs\b)""",
            RegexOption.IGNORE_CASE,
        )

        val URGENCY = Regex(
            """\b(?:urgent(?:ly)?|immediately|right now|act now|asap|at once|hurry|""" +
                """last chance|final notice|expir(?:es|ing|ed)|within \d+ (?:hours?|minutes?|days?)|""" +
                """before it'?s too late|limited time|without delay)\b""",
            RegexOption.IGNORE_CASE,
        )

        val ACCOUNT_THREAT = Regex(
            """\b(?:account|card|service|sim|number)\s+(?:will be|shall be|is going to be|would be)\s+""" +
                """(?:blocked|suspended|frozen|deactivated|closed|terminated|discontinued)\b""",
            RegexOption.IGNORE_CASE,
        )

        val OTP_REFERENCE = Regex(
            """\b(?:otp|one[\s-]?time[\s-]?password|verification code|security code|""" +
                """authentication code)\b""",
            RegexOption.IGNORE_CASE,
        )

        val CREDENTIAL_REFERENCE = Regex(
            """\b(?:password|passcode|m-?pin|atm pin|pin|cvv|card number|net ?banking""" +
                """(?: password| details)?|login (?:details|credentials)|account (?:details|credentials)|""" +
                """aadhaar number|pan number)\b""",
            RegexOption.IGNORE_CASE,
        )

        val PAYMENT_PHRASE = Regex(
            """\b(?:pay(?:ment)?(?: now| immediately)?|transfer(?: the)?(?: money| amount| funds)?|""" +
                """send (?:money|cash|funds|the amount)|deposit|remit|scan (?:and|&) pay|""" +
                """make a payment|""" +
                // "Send Rs. 5000 to ..." names an amount instead of the word "money", which
                // is how a demand is usually phrased and was previously missed entirely.
                """(?:send|transfer|remit|deposit)\s+(?:rs\.?|inr|₹)\s*[\d,]+)\b""",
            RegexOption.IGNORE_CASE,
        )

        val REQUEST_VERB = Regex(
            """\b(?:share|send|tell|give|provide|forward|enter|submit|confirm|disclose|""" +
                """reveal|update|type|reply with)\b""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Explicit "this already happened" wording. Narrow on purpose — see
         * [isAlreadySettled] for why tense is not attempted in general.
         */
        val PAYMENT_COMPLETED = Regex(
            """\b(?:(?:was|were|has been|have been)\s+(?:sent|paid|made|received|""" +
                """completed|processed|debited|credited)|""" +
                """(?:already|successfully|previously)\s+(?:paid|sent|transferred)|""" +
                """(?:previous|last)\s+payment|payment\s+(?:received|successful|confirmation))\b""",
            RegexOption.IGNORE_CASE,
        )

        val NEGATION = Regex(
            """\b(?:do not|don'?t|never|no need to|will not|won'?t|should not|shouldn'?t|not)\b""",
            RegexOption.IGNORE_CASE,
        )

        val KYC = Regex("""\bkyc\b""", RegexOption.IGNORE_CASE)
        val CLICK_LINK = Regex("""\b(?:click|tap|open)\b[^.]{0,30}\blink\b""", RegexOption.IGNORE_CASE)
        val INSTALL_APP = Regex(
            """\b(?:install|download)\b[^.]{0,30}\b(?:app|apk|application)\b""",
            RegexOption.IGNORE_CASE,
        )

        /**
         * Organisations commonly impersonated in Indian scam messages. An allowlist keeps
         * extraction deterministic; the trade-off is that an unlisted brand is simply not
         * detected, which is the safe direction to fail in.
         */
        val ORGANISATIONS: List<Pair<String, Regex>> = listOf(
            "SBI" to """\b(?:sbi|state bank of india)\b""",
            "HDFC Bank" to """\bhdfc(?: bank)?\b""",
            "ICICI Bank" to """\bicici(?: bank)?\b""",
            "Axis Bank" to """\baxis bank\b""",
            "Kotak Mahindra Bank" to """\bkotak(?: mahindra)?(?: bank)?\b""",
            "Punjab National Bank" to """\b(?:pnb|punjab national bank)\b""",
            "Bank of Baroda" to """\bbank of baroda\b""",
            "Canara Bank" to """\bcanara bank\b""",
            "Union Bank" to """\bunion bank\b""",
            "Yes Bank" to """\byes bank\b""",
            "IDFC First Bank" to """\bidfc(?: first)?(?: bank)?\b""",
            "IndusInd Bank" to """\bindusind(?: bank)?\b""",
            "RBI" to """\b(?:rbi|reserve bank of india)\b""",
            "Paytm" to """\bpaytm\b""",
            "PhonePe" to """\bphonepe\b""",
            "Google Pay" to """\b(?:google pay|gpay)\b""",
            "Amazon" to """\bamazon\b""",
            "Flipkart" to """\bflipkart\b""",
            "IRCTC" to """\birctc\b""",
            "EPFO" to """\bepfo\b""",
            "UIDAI" to """\b(?:uidai|aadhaar)\b""",
            "Income Tax Department" to """\bincome tax\b""",
            "Airtel" to """\bairtel\b""",
            "Jio" to """\bjio\b""",
            "India Post" to """\bindia post\b""",
            "Blue Dart" to """\bblue ?dart\b""",
            "FedEx" to """\bfedex\b""",
            "Netflix" to """\bnetflix\b""",
        ).map { (name, pattern) -> name to Regex(pattern, RegexOption.IGNORE_CASE) }
    }
}
