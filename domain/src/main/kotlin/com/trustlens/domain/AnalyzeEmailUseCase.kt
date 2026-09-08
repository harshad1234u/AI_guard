package com.trustlens.domain

import javax.inject.Inject

/**
 * Routes a shared email into the analysis that already exists for text:
 *
 * ```
 * share intent -> EmailParser -> AnalyzeTextUseCase -> EmailCheckOutcome
 * ```
 *
 * There is no email-specific scam logic here and no email-specific scoring anywhere. A
 * phishing mail and the same wording pasted into Check Message reach an identical score,
 * because they run through identical code — a property a test pins directly.
 *
 * The one thing this surface adds is the **sender-domain check**, and it adds no weight to do
 * it. The sender's host is handed to [AnalyzeTextUseCase] as an extra lookup subject, so it is
 * graded by the same reputation providers, on the same scale, as any host found in a link. If
 * URLhaus has never heard of it, that contributes nothing — `UNKNOWN` is not `SAFE`.
 *
 * What leaves the device is a hostname. The subject, the body, and the local part of the
 * sender address do not.
 */
class AnalyzeEmailUseCase @Inject constructor(
    private val parser: EmailParser,
    private val analyzeText: AnalyzeTextUseCase,
) {

    /**
     * Reads a share into its parts without analysing anything.
     *
     * Exists so the sender can be shown to the user, and corrected, *before* a lookup happens
     * — the sender's host is the only part of an email that ever leaves the device, so the
     * user gets to see it first.
     */
    fun parseOnly(subject: String?, sharedText: String): EmailMessage =
        parser.parse(subject, sharedText)

    /**
     * @param senderOverride the address the user supplied or corrected. Wins over anything
     *   parsed out of a forwarded header, because the share intent carries no sender field and
     *   the user is the more reliable source.
     */
    suspend operator fun invoke(
        subject: String?,
        sharedText: String,
        senderOverride: String? = null,
    ): EmailCheckOutcome {
        val parsed = parser.parse(subject, sharedText)
        val message = parsed.copy(
            senderAddress = senderOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: parsed.senderAddress,
        )

        val analysable = message.analysableText()

        // An empty share is not a clean email. It is nothing, and it is reported as nothing.
        if (analysable.isBlank()) return EmailCheckOutcome.NothingToAnalyse

        val senderDomain = message.senderAddress?.let(::domainOf)

        return EmailCheckOutcome.Analysed(
            result = analyzeText(
                text = analysable,
                source = InvestigationSource.EMAIL,
                extraLookupSubjects = listOfNotNull(senderDomain),
            ),
            message = message,
            senderDomain = senderDomain,
        )
    }

    /**
     * The host part of an address, lowercased.
     *
     * Plain string work rather than a mail-address library: this runs on whatever a mail app
     * put in a share extra, and something that throws on malformed input would turn a scrappy
     * forward into a crash instead of an analysis.
     */
    private fun domainOf(address: String): String? =
        address.substringAfterLast('@', missingDelimiterValue = "")
            .trim()
            .trimEnd('>', '.', ',', ';', ')')
            .lowercase()
            .takeIf { it.isNotEmpty() && it.contains('.') }
}
