package com.trustlens.domain

/**
 * Suppresses repeat work while the same QR code stays in front of the camera.
 *
 * A continuous scanner sees the same code in every frame. Without this, holding a phone steady
 * for one second would run the analysis pipeline dozens of times over identical input and
 * redraw an identical verdict — wasted battery, and a screen that flickers while the user
 * tries to read it.
 *
 * Time is passed in rather than read, so the whole thing is deterministic and testable.
 */
class ScanDebouncer(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {

    private var lastPayload: String? = null
    private var lastAcceptedAt: Long = 0

    /**
     * Whether [payload] should be analysed now.
     *
     * A payload that is still being seen keeps its window alive, so a code held in frame is
     * analysed exactly once no matter how long it is held.
     */
    fun shouldProcess(payload: String, nowMillis: Long): Boolean {
        if (payload.isBlank()) return false

        val isRepeat = payload == lastPayload && nowMillis - lastAcceptedAt < windowMillis
        lastPayload = payload
        lastAcceptedAt = nowMillis
        return !isRepeat
    }

    /** Forgets what was last seen, so the user can deliberately rescan the same code. */
    fun reset() {
        lastPayload = null
        lastAcceptedAt = 0
    }

    companion object {
        /** Long enough to cover a steady hold, short enough that a deliberate retry works. */
        const val DEFAULT_WINDOW_MILLIS = 3_000L
    }
}
