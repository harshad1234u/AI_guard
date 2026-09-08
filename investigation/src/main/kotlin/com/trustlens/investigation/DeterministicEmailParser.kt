package com.trustlens.investigation

import com.trustlens.domain.EmailMessage
import com.trustlens.domain.EmailParser

/**
 * Recovers an email's parts from an Android share intent by string inspection alone.
 *
 * Nothing here contacts, resolves or fetches anything. A shared email is a string that arrived
 * from another app; it is inspected, split into fields, and handed on.
 *
 * ## Why this is heuristic, and why that is safe
 *
 * `ACTION_SEND` has no sender field. Mail apps put the subject in `EXTRA_SUBJECT` and the body
 * in `EXTRA_TEXT`, and a *forwarded* message usually carries a quoted header block at the top
 * of that body:
 *
 * ```
 * ---------- Forwarded message ---------
 * From: SBI Alerts <alerts@sbi-verify.example.com>
 * Date: Mon, 8 Sep 2026 at 10:14
 * Subject: Your account will be blocked
 * ```
 *
 * So the sender is recovered from that block when it exists. When it does not, the sender is
 * simply null and the user can supply it — the analysis runs either way.
 *
 * Failing to find a sender is the safe direction to fail in: it removes one *additional*
 * check, and can never make a dangerous email look clean, because the body is scored
 * regardless and an unlooked-up host contributes nothing either way.
 */
class DeterministicEmailParser : EmailParser {

    override fun parse(subject: String?, sharedText: String): EmailMessage {
        val text = sharedText.replace("\r\n", "\n")

        return EmailMessage(
            senderAddress = findSenderAddress(text),
            // The share extra wins; a quoted `Subject:` header is the fallback for mail apps
            // that fold everything into the body.
            subject = subject?.trim()?.takeIf { it.isNotEmpty() } ?: findQuotedSubject(text),
            body = sharedText,
        )
    }

    /**
     * The address on the first `From:` header line, if there is one.
     *
     * Only the *first* is taken. A long forward chain contains many, and the outermost — the
     * one nearest the top — is the one that actually delivered this mail to the user.
     */
    private fun findSenderAddress(text: String): String? =
        text.lineSequence()
            .take(MAX_HEADER_LINES)
            .firstNotNullOfOrNull { line ->
                FROM_HEADER.find(line)?.let { ADDRESS.find(it.groupValues[1])?.value }
            }

    private fun findQuotedSubject(text: String): String? =
        text.lineSequence()
            .take(MAX_HEADER_LINES)
            .firstNotNullOfOrNull { line ->
                SUBJECT_HEADER.find(line)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
            }

    private companion object {
        /**
         * Header scanning stops well before the body.
         *
         * A quoted header block sits at the top of a forward. Reading the whole message would
         * find `From:` lines inside older quoted replies and inside the body prose itself,
         * and would pick a sender that never sent this mail.
         */
        const val MAX_HEADER_LINES = 40

        val FROM_HEADER = Regex("""^\s*>*\s*from\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)

        val SUBJECT_HEADER = Regex("""^\s*>*\s*subject\s*:\s*(.+)$""", RegexOption.IGNORE_CASE)

        /** Same shape the indicator extractor uses, so both agree on what an address is. */
        val ADDRESS = Regex(
            """[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""",
        )
    }
}
