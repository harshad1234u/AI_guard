package com.trustlens.investigation

import com.trustlens.domain.IndicatorExtractor
import com.trustlens.domain.IndicatorKind
import com.trustlens.domain.IndicatorMatch
import com.trustlens.domain.Indicators

/**
 * Hindi, Hinglish and Indian-English coverage for the deterministic signals.
 *
 * ## Why this is a decorator and not an edit to [RuleBasedIndicatorExtractor]
 *
 * The frozen extractor is correct; it is only monolingual. Measured before this class
 * existed, a complete Hindi bank-impersonation script —
 * *"मैं बैंक से बोल रहा हूँ। आपका अकाउंट आज बंद हो जाएगा। अभी आपको एक OTP आया होगा, वो मुझे बताइए।"*
 * — produced **every flag false** and scored 3/100, LOW. The OTP *reference* matched
 * (`\botp\b` is script-agnostic), but `RuleBasedIndicatorExtractor.isRequested` needs an
 * asking verb from `share|send|tell|give|…` nearby, and "बताइए" is not one. So the single
 * most reliable fraud indicator in India was invisible the moment the caller switched
 * language.
 *
 * This class fixes that by **wrapping** rather than rewriting, which buys one property worth
 * more than the code it costs:
 *
 * > **It can only ever add signals. It never clears one the delegate set.**
 *
 * Every flag is OR-ed and every list is unioned, so English content reaches the same
 * indicators it always did. `MultilingualParityTest` pins that against the frozen extractor
 * rather than leaving it as an assurance in a comment.
 *
 * ## The three deliberate exceptions
 *
 * Three rules here change English behaviour on purpose, because detecting them only in Hindi
 * would be arbitrary:
 *
 *  1. the **OTP euphemism** — "the six digit number" as well as "छह अंकों का नंबर";
 *  2. **police / cyber-crime** impersonation, which the frozen organisation list has no
 *     entry for in any language;
 *  3. **customs department** impersonation, likewise.
 *
 * ## What it does not do
 *
 * **No new [com.trustlens.domain.ObservationKind], and no new weight.** A Hindi OTP request
 * becomes the same `OTP_REQUEST` at the same `+22` an English one produces. `:riskengine` has
 * zero changed files and `RiskWeights.kt` is byte-identical, so "OTP बताइए" and "share the
 * OTP" reach the same score — a property the tests assert directly. There is no Hindi bonus
 * and no language multiplier: a scam is scored by what is being asked for, not by the
 * language it is asked in.
 *
 * **No translation, and no model.** This is regex over the original text. Translating first
 * would put a generative model between the caller's words and the risk score, which
 * Principle 1 forbids.
 *
 * **No digits are ever captured.** The OTP rules match the *request phrasing*, never the
 * number, so no one-time code can reach an [IndicatorMatch] and from there the screen.
 *
 * ## A note on `\b`
 *
 * Java's `\b` is defined against ASCII `\w` unless `UNICODE_CHARACTER_CLASS` is set — and
 * that flag throws `IllegalArgumentException` on Android's ICU-backed engine (see
 * `TfidfVectoriser`, where the same trap was found on a physical device). So Devanagari
 * patterns here use explicit `(?<![\u0900-\u097F])` / `(?![\u0900-\u097F])` lookarounds and
 * never `\b`, while ASCII patterns keep using `\b` normally.
 */
class MultilingualIndicatorExtractor(
    private val delegate: IndicatorExtractor = RuleBasedIndicatorExtractor(),
) : IndicatorExtractor {

    override fun extract(text: String): Indicators {
        val base = delegate.extract(text)
        if (text.isBlank()) return base

        val added = mutableListOf<IndicatorMatch>()

        val otpRequest = base.otpRequest ||
            isRequested(text, OTP_REFERENCE, IndicatorKind.OTP_REFERENCE, added)
        val credentialRequest = base.credentialRequest ||
            isRequested(text, CREDENTIAL_REFERENCE, IndicatorKind.CREDENTIAL_REFERENCE, added)
        val paymentRequest = base.paymentRequest || isPaymentRequested(text, added)

        val urgency = base.urgency ||
            collect(text, URGENCY, IndicatorKind.URGENCY_PHRASE, added).isNotEmpty()
        val accountThreat = base.accountThreat ||
            collect(text, ACCOUNT_THREAT, IndicatorKind.ACCOUNT_THREAT_PHRASE, added).isNotEmpty()

        val organisations = (base.organisations + extractOrganisations(text, added)).distinct()

        val coerced = urgency || accountThreat || otpRequest || credentialRequest || paymentRequest

        return base.copy(
            organisations = organisations,
            requestedActions = requestedActions(
                base = base.requestedActions,
                text = text,
                otpRequest = otpRequest,
                credentialRequest = credentialRequest,
                paymentRequest = paymentRequest,
            ),
            urgency = urgency,
            otpRequest = otpRequest,
            paymentRequest = paymentRequest,
            credentialRequest = credentialRequest,
            accountThreat = accountThreat,
            // Recomputed rather than inherited: the delegate may have found a brand with no
            // coercive ask beside it — "your SBI statement is ready" — and a Hindi demand
            // discovered here is exactly what turns that into an impersonation signal.
            impersonatedOrganisation = organisations.firstOrNull().takeIf { coerced },
            matches = (base.matches + added)
                .distinctBy { Triple(it.kind, it.startIndex, it.endIndex) }
                .sortedWith(compareBy({ it.startIndex }, { it.kind.ordinal })),
        )
    }

    // --- request detection, mirroring the delegate's rule ---------------------------------

    /**
     * A sensitive term counts as a *request* only when an asking verb sits near it and is
     * not negated — the same rule [RuleBasedIndicatorExtractor] applies, with Hindi and
     * romanised-Hindi verbs and negations added.
     *
     * This is what keeps a genuine bank message out of the results.
     * *"आपका ओटीपी 123456 है। इसे किसी को न बताएं।"* contains both an OTP reference and the
     * verb "बताएं", and is a warning rather than an attack; the "न" before it is the whole
     * difference.
     */
    private fun isRequested(
        text: String,
        reference: Regex,
        kind: IndicatorKind,
        sink: MutableList<IndicatorMatch>,
    ): Boolean {
        val references = matches(text, reference, kind)
        sink += references

        return references.any { found ->
            REQUEST_VERB.findAll(text).any { verb ->
                val distance = minOf(
                    kotlin.math.abs(verb.range.first - found.startIndex),
                    kotlin.math.abs(verb.range.first - found.endIndex),
                )
                distance <= REQUEST_WINDOW && !isNegated(text, verb.range.first)
            }
        }
    }

    private fun isPaymentRequested(text: String, sink: MutableList<IndicatorMatch>): Boolean {
        val phrases = matches(text, PAYMENT_PHRASE, IndicatorKind.PAYMENT_PHRASE)
        sink += phrases
        return phrases.any { !isNegated(text, it.startIndex) }
    }

    private fun isNegated(text: String, index: Int): Boolean {
        val windowStart = (index - NEGATION_WINDOW).coerceAtLeast(0)
        return NEGATION.containsMatchIn(text.substring(windowStart, index))
    }

    // --- organisations --------------------------------------------------------------------

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

    private fun requestedActions(
        base: List<String>,
        text: String,
        otpRequest: Boolean,
        credentialRequest: Boolean,
        paymentRequest: Boolean,
    ): List<String> = buildList {
        addAll(base)
        if (KYC.containsMatchIn(text)) add("complete_kyc")
        if (otpRequest) add("share_otp")
        if (credentialRequest) add("share_credentials")
        if (paymentRequest) add("make_payment")
        if (REMOTE_ACCESS.containsMatchIn(text)) add("install_app")
    }.distinct()

    // --- helpers --------------------------------------------------------------------------

    private fun collect(
        text: String,
        pattern: Regex,
        kind: IndicatorKind,
        sink: MutableList<IndicatorMatch>,
    ): List<IndicatorMatch> {
        val found = matches(text, pattern, kind)
        sink += found
        return found
    }

    private fun matches(
        text: String,
        pattern: Regex,
        kind: IndicatorKind,
    ): List<IndicatorMatch> {
        val seen = LinkedHashMap<String, IndicatorMatch>()
        pattern.findAll(text).forEach { match ->
            val value = match.value.trim()
            if (value.isEmpty()) return@forEach
            val start = match.range.first + match.value.indexOf(value)
            seen.putIfAbsent(value, IndicatorMatch(kind, value, start, start + value.length))
        }
        return seen.values.toList()
    }

    private companion object {
        /** Matched to the delegate's own windows, so the two rules stay comparable. */
        const val REQUEST_WINDOW = 60
        const val NEGATION_WINDOW = 30

        /** No `\b` for Devanagari — see the class documentation for why. */
        const val DEV_START = """(?<![\u0900-\u097F])"""
        const val DEV_END = """(?![\u0900-\u097F])"""

        private fun hindi(pattern: String) = Regex("$DEV_START(?:$pattern)$DEV_END")

        private fun mixed(pattern: String) = Regex(pattern, setOf(RegexOption.IGNORE_CASE))

        /**
         * Asking verbs — **English included**.
         *
         * The English half is copied from the frozen extractor rather than delegated to it,
         * because the combination that matters is the *cross product*: an English reference
         * ("OTP", which every Indian scammer says in English) with a Hindi verb ("बताइए"),
         * and a Hindi reference ("छह अंकों का नंबर") with an English verb ("tell"). Neither
         * extractor alone can see those, and the frozen companion object is private.
         *
         * The duplication is the price of not editing a frozen file. `MultilingualParityTest`
         * is what stops it drifting: if these lists and the frozen ones ever disagree about
         * English, that test fails.
         *
         * Romanised entries all carry a Hindi verb ending (`-iye`, `-o`, `-na`, `-dijiye`),
         * so they cannot match an English word. Bare "do" and "de" are deliberately absent.
         */
        val REQUEST_VERB = Regex(
            // Devanagari
            """(?:बताइए|बताईए|बताएं|बताएँ|बताओ|बता\s?दीजिए|बता\s?दो|बोल\s?दीजिए|""" +
                """भेजिए|भेजो|भेजें|भेज\s?दीजिए|दीजिए|दे\s?दीजिए|दे\s?दो|लिखिए|""" +
                """डालिए|डालो|शेयर\s?कीजिए|शेयर\s?करो|वेरीफाई\s?कीजिए|कन्फर्म\s?कीजिए)""" +
                // romanised Hindi
                """|(?i:\b(?:bata(?:iye|ie|ye|o|na)|bata\s?d(?:ijiye|ijie|o)|""" +
                """bol\s?d(?:ijiye|o)|bhej(?:iye|o|na|en)|bhej\s?d(?:ijiye|o)|""" +
                """de\s?d(?:ijiye|o)|d(?:ijiye|ijie)|likh(?:iye|o)|dal(?:iye|o)|""" +
                """(?:share|verify|confirm|update)\s+kar(?:iye|o|na|ke|ni))\b)""" +
                // English, copied verbatim from the frozen extractor
                """|(?i:\b(?:share|send|tell|give|provide|forward|enter|submit|confirm|""" +
                """disclose|reveal|update|type|reply with)\b)""" +
                // Indian-English phrasings the frozen list does not carry
                """|(?i:\b(?:read\s+out|repeat\s+back|dictate)\b)""",
        )

        /**
         * Negations, English half copied for the same reason.
         *
         * This set must stay a *superset* of the frozen one. If it were not, an English
         * message the frozen extractor correctly suppressed — "123456 is your OTP. Do not
         * share it." — would be re-detected here as an attack, and OR-ing the flags would
         * make the wrapper strictly worse than the thing it wraps.
         *
         * Over-matching is the safe direction: it suppresses a signal, and a genuine bank
         * message saying "never share this" must never be scored as the attack it warns of.
         */
        val NEGATION = Regex(
            """(?:नहीं|नही|""" + DEV_START + """न""" + DEV_END + """|मत)""" +
                """|(?i:\b(?:nahin?|mat|kabhi\s+nahin?|kisi\s+ko\s+na)\b)""" +
                // English, copied verbatim from the frozen extractor
                """|(?i:\b(?:do not|don'?t|never|no need to|will not|won'?t|should not|""" +
                """shouldn'?t|not)\b)""",
        )

        /**
         * OTP references.
         *
         * The euphemism rules are the point of this set, and they are one of the three
         * places this class deliberately changes **English** behaviour too. A caller who has
         * learned that saying "OTP" ends the call says *"छह अंकों का नंबर"* or *"the six
         * digit number"* instead, and the frozen extractor detects neither. Detecting only
         * the Hindi phrasing would be arbitrary.
         *
         * Only the phrasing is captured — never the digits.
         */
        val OTP_REFERENCE = Regex(
            """(?:ओटीपी|ओ\s?टी\s?पी|वन\s?टाइम\s?पासवर्ड|""" +
                """(?:छह|छे|छः|चार|आठ|6|4|8)\s*अंकों?\s*(?:का|वाला)?\s*(?:नंबर|कोड|पिन)|""" +
                """वेरिफिकेशन\s?कोड|सत्यापन\s?कोड)""" +
                """|(?i:\b(?:(?:six|four|eight|6|4|8)[\s-]?digits?[\s-]?""" +
                """(?:number|code|pin|password)|(?:chh?a?[eh]|char|aath)\s+ank(?:on)?\s*""" +
                """(?:ka|wala)?\s*(?:number|code|pin))\b)""" +
                // English, copied verbatim from the frozen extractor
                """|(?i:\b(?:otp|one[\s-]?time[\s-]?password|verification code|""" +
                """security code|authentication code)\b)""",
        )

        /** Credential references, again a superset of the frozen Latin spellings. */
        val CREDENTIAL_REFERENCE = Regex(
            """(?:पिन|पासवर्ड|सीवीवी|कार्ड\s?नंबर|यूपीआई\s?पिन|एटीएम\s?पिन|""" +
                """नेट\s?बैंकिंग|आधार\s?नंबर|पैन\s?नंबर|खाता\s?नंबर|अकाउंट\s?नंबर|""" +
                """लॉगिन\s?(?:डिटेल्स|पासवर्ड))""" +
                """|(?i:\b(?:upi[\s-]?pin|mpin|khata[\s-]?number)\b)""" +
                // English, copied verbatim from the frozen extractor
                """|(?i:\b(?:password|passcode|m-?pin|atm pin|pin|cvv|card number|""" +
                """net ?banking(?: password| details)?|login (?:details|credentials)|""" +
                """account (?:details|credentials)|aadhaar number|pan number)\b)""",
        )

        /**
         * Time pressure.
         *
         * "abhi" doubles as a personal name, and is accepted anyway: urgency is +10 and can
         * never raise an alarm on its own, so the cost of the occasional false positive is
         * far below the cost of missing "अभी बताइए".
         */
        val URGENCY = Regex(
            """(?:अभी|तुरंत|तुरन्त|फौरन|फ़ौरन|जल्दी|आज\s?ही|आखिरी\s?मौका|समय\s?सीमा)""" +
                """|(?i:\b(?:abhi|turant|jaldi|f[oa]ran|aaj\s+hi)\b)""",
        )

        /**
         * Threats to an account.
         *
         * Hindi puts the verb last — *"अकाउंट बंद हो जाएगा"* — so the frozen
         * `account will be blocked` shape cannot match it. The bounded gaps allow the
         * intervening words ("आज", "कल") without letting the subject and the threat drift
         * into different sentences.
         */
        val ACCOUNT_THREAT = Regex(
            """(?:अकाउंट|खाता|खाते|कार्ड|सिम|नंबर)[^।!?\n]{0,40}?""" +
                """(?:बंद|ब्लॉक|सस्पेंड|फ्रीज|निष्क्रिय)[^।!?\n]{0,15}?""" +
                """(?:हो\s?जाएगा|हो\s?जायेगा|कर\s?दिया\s?जाएगा|हो\s?जाएगी|कर\s?देंगे)""" +
                """|(?i:\b(?:account|khata|khate|card|sim)\b[^.!?\n]{0,40}?""" +
                """\b(?:band|block|suspend|freeze|deactivate)\b[^.!?\n]{0,15}?""" +
                """\b(?:ho\s+ja(?:yega|yegi|ega)|kar\s+d(?:iya\s+jayega|enge))\b)""",
        )

        /**
         * Payment demands — **Hindi and romanised only**.
         *
         * English payment wording is left entirely to the frozen extractor, which pairs it
         * with an `isAlreadySettled` guard this class does not reproduce. Adding English
         * phrases here without that guard would turn a receipt — "the previous payment was
         * sent to merchant@okaxis" — into a fresh demand.
         */
        val PAYMENT_PHRASE = Regex(
            """(?:पैसे\s?भेज|पैसा\s?भेज|पेमेंट\s?कर|भुगतान\s?कर|ट्रांसफर\s?कर|""" +
                """जमा\s?कर|रुपये\s?भेज|फीस\s?भर|जुर्माना\s?भर)""" +
                """|(?i:\b(?:pais[ae]|paisa|rupay[ae])\s+(?:bhej|dal|transfer|de)\w*""" +
                """|\b(?:payment|transfer|jama)\s+kar\w*""" +
                """|\b(?:fees?|jurmana)\s+bhar\w*)""",
        )

        /**
         * A remote-access or screen-share demand — the setup step of nearly every
         * "digital arrest" and fake-refund call.
         */
        val REMOTE_ACCESS = Regex(
            """(?:स्क्रीन\s?शेयर|रिमोट\s?एक्सेस|ऐप\s?डाउनलोड|ऐप\s?इंस्टॉल)""" +
                """|(?i:\b(?:screen[\s-]?shar(?:e|ing)|remote[\s-]?access|anydesk|""" +
                """teamviewer|quick\s?support|rust\s?desk)\b)""",
        )

        val KYC = Regex("""(?:केवाईसी|के\s?वाई\s?सी)|(?i:\bkyc\b)""")

        /**
         * Authorities and brands, in Devanagari plus the two impersonation categories the
         * frozen list has no entry for at all: **police / cyber-crime** and **customs**.
         *
         * Those two are matched in English as well as Hindi. "Police station se bol raha
         * hoon" and "I am calling from the cyber crime branch" are the same scam, and
         * detecting only the Hindi half would be arbitrary. As in the frozen extractor a
         * named body only becomes an impersonation signal when a coercive ask sits beside
         * it, so an ordinary sentence mentioning the police scores nothing.
         */
        val ORGANISATIONS: List<Pair<String, Regex>> = listOf(
            "SBI" to hindi("""एसबीआई|भारतीय\s?स्टेट\s?बैंक|स्टेट\s?बैंक"""),
            "HDFC Bank" to hindi("""एचडीएफसी"""),
            "ICICI Bank" to hindi("""आईसीआईसीआई"""),
            "RBI" to hindi("""आरबीआई|रिज़र्व\s?बैंक|रिजर्व\s?बैंक"""),
            "Paytm" to hindi("""पेटीएम"""),
            "PhonePe" to hindi("""फोनपे|फ़ोनपे"""),
            "UIDAI" to hindi("""यूआईडीएआई|आधार"""),
            "Income Tax Department" to hindi("""आयकर\s?विभाग"""),
            "India Post" to hindi("""डाक\s?विभाग|इंडिया\s?पोस्ट"""),
            "Police" to mixed(
                """(?:पुलिस|साइबर\s?क्राइम|साइबर\s?सेल|अपराध\s?शाखा)""" +
                    """|\b(?:police(?:\s+station)?|cyber[\s-]?(?:crime|cell)|crime\s+branch|""" +
                    """narcotics\s+(?:department|bureau))\b""",
            ),
            "Customs Department" to mixed(
                """(?:सीमा\s?शुल्क\s?विभाग|कस्टम\s?विभाग)|\bcustoms?\s+department\b""",
            ),
        )
    }
}
