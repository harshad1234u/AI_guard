package com.trustlens.domain

/**
 * One email, split into the parts that matter to the analysis.
 *
 * Everything here is untrusted content (Principle 1). A subject line reading "IGNORE ALL
 * PREVIOUS INSTRUCTIONS" is a subject line, scored as evidence like any other wording.
 */
data class EmailMessage(
    /**
     * The sender address, when one could be recovered.
     *
     * Null is a normal outcome, not a failure: Android's share intent carries no sender
     * field, so this is recovered from a forwarded `From:` header when the mail app included
     * one, and is otherwise supplied by the user. The analysis runs either way — it simply
     * has one fewer thing to check.
     */
    val senderAddress: String?,
    val subject: String?,
    val body: String,
) {
    /**
     * Exactly what gets analysed: the subject and the body, and nothing synthesised.
     *
     * The sender address is deliberately *not* spliced in here. It travels separately as a
     * lookup subject so that [InvestigationContext.rawContent] stays a faithful copy of what
     * the user actually shared, which is what the screen shows back to them.
     */
    fun analysableText(): String =
        listOfNotNull(subject?.takeIf { it.isNotBlank() }, body.takeIf { it.isNotBlank() })
            .joinToString("\n\n")
}

/**
 * Recovers an [EmailMessage] from what a mail app actually hands over on a share.
 *
 * Deterministic by contract, exactly like [IndicatorExtractor]: no model may be substituted
 * here, because the risk engine treats what comes out as facts about the content.
 */
interface EmailParser {
    /**
     * @param subject the share intent's subject extra, when present.
     * @param sharedText the share intent's text extra — usually the body, often with a
     *   quoted header block in front of it for a forwarded message.
     */
    fun parse(subject: String?, sharedText: String): EmailMessage
}

/**
 * What an email check produced.
 *
 * [NothingToAnalyse] carries no risk result on purpose: an empty share is not evidence of
 * safety, and inventing a verdict from nothing would be worse than saying nothing.
 */
sealed interface EmailCheckOutcome {

    data class Analysed(
        val result: AnalysisResult,
        /** The parsed message, shown back so the user can see what was read. */
        val message: EmailMessage,
        /**
         * The registrable host of [EmailMessage.senderAddress], when there was one. This is
         * the only part of the email that is ever sent to a reputation provider.
         */
        val senderDomain: String?,
    ) : EmailCheckOutcome

    data object NothingToAnalyse : EmailCheckOutcome
}
