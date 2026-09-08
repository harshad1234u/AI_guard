package com.trustlens

/**
 * Text that arrived from another app, and which surface should read it.
 *
 * TrustLens has three of these and they mean different things, so they are kept apart rather
 * than collapsed into one string:
 *
 *  - [Shared] — `ACTION_SEND` carrying email context. A mail app supplied a subject alongside
 *    the body, so it goes to Check Email, where a sender can be entered and shown.
 *  - [Message] — `ACTION_SEND` carrying nothing but text. Messages, WhatsApp, Chrome and Notes
 *    all share this way, and none of them is an email, so it goes to Check Message.
 *  - [Selection] — `ACTION_PROCESS_TEXT` from the text-selection toolbar. The user highlighted
 *    a *fragment* of something. That is not an email, has no sender, and must not be presented
 *    as one, so it goes to Check Message.
 *
 * All three are untrusted content (Principle 1): carried as values to be analysed and
 * displayed, never interpreted as instructions to TrustLens, however imperative they sound.
 */
internal sealed interface IncomingText {

    /** A message shared from a mail app, identified by the subject it carried. */
    data class Shared(val subject: String?, val body: String) : IncomingText

    /**
     * Plain text shared from an app that supplied no email context.
     *
     * It carries no subject and no sender, because none was sent. Neither is invented: the
     * screen shows exactly the characters the other app handed over.
     */
    data class Message(val text: String) : IncomingText

    /**
     * A fragment highlighted in another app.
     *
     * @param text what will actually be analysed, after the length cap.
     * @param originalLength how long the selection was before the cap, so the screen can say
     *   so rather than quietly analysing less than the user selected.
     */
    data class Selection(val text: String, val originalLength: Int) : IncomingText {
        val truncated: Boolean get() = originalLength > text.length
    }
}

/**
 * Reads the two supported incoming intents.
 *
 * A pure function over the extras rather than over an `Intent`, so every branch — including
 * the malformed ones — is testable on the JVM without an emulator. `MainActivity` pulls the
 * extras and calls this; nothing else here touches the Android framework.
 */
internal object IncomingTextReader {

    /**
     * The most selected text that will be analysed.
     *
     * The existing Text pipeline imposes **no limit of its own** — `TextCheckViewModel` hands
     * whatever it holds straight to `AnalyzeTextUseCase`, and the extractor and vectoriser
     * both run over the whole string. That is fine for a pasted message, which is what the
     * pipeline was built for, but `ACTION_PROCESS_TEXT` can deliver a "select all" from a long
     * web page, and the extractor's regex work runs on the caller's dispatcher.
     *
     * So a cap exists, and it is set far above any real message: a scam SMS is a few hundred
     * characters and a long phishing email a few thousand. Twenty thousand leaves every
     * realistic case untouched while bounding the worst case.
     *
     * **Truncation is never silent.** [IncomingText.Selection.truncated] carries the fact up
     * to the screen, which says how much was analysed — dropping a URL off the end of a
     * selection without saying so would be exactly the kind of quiet failure this app exists
     * to avoid.
     */
    const val MAX_SELECTION_CHARS = 20_000

    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_PROCESS_TEXT = "android.intent.action.PROCESS_TEXT"

    /**
     * @return null for an ordinary launcher start, an unrecognised action, or an intent that
     *   carried nothing worth reading. Null means "open the app normally" — never "analyse an
     *   empty string", because a verdict about nothing would be worse than no verdict.
     */
    fun read(
        action: String?,
        subject: String? = null,
        sendText: String? = null,
        processText: CharSequence? = null,
    ): IncomingText? = when (action) {
        ACTION_SEND -> readShare(subject, sendText)
        ACTION_PROCESS_TEXT -> readSelection(processText)
        else -> null
    }

    /**
     * `ACTION_SEND` is generic. It carries a share from Messages, WhatsApp, Chrome, Notes or a
     * mail app, and the action alone says nothing about which — only `EXTRA_SUBJECT` does.
     * Mail apps set it; ordinary messaging apps do not. So the subject, not the action, is
     * what separates an email share from an ordinary one.
     *
     * With no subject the text is read as an ordinary message. Sending every share to Check
     * Email would put an SMS inside a mail-shaped screen and invite the user to supply a
     * sender that was never part of what they shared.
     */
    private fun readShare(subject: String?, sendText: String?): IncomingText? {
        val body = sendText.orEmpty()
        if (body.isBlank() && subject.isNullOrBlank()) return null
        if (subject.isNullOrBlank()) return IncomingText.Message(body)
        return IncomingText.Shared(subject = subject, body = body)
    }

    private fun readSelection(processText: CharSequence?): IncomingText? {
        // toString() rather than a cast: EXTRA_PROCESS_TEXT is a CharSequence and callers do
        // send Spanned instances with styling attached, which must not reach the pipeline.
        val raw = processText?.toString().orEmpty()
        if (raw.isBlank()) return null

        return IncomingText.Selection(
            text = raw.take(MAX_SELECTION_CHARS),
            originalLength = raw.length,
        )
    }
}
