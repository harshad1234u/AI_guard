package com.trustlens.domain

/**
 * Risk bands from the product spec. Boundaries are inclusive on both ends and contiguous,
 * so every score in 0..100 maps to exactly one level.
 */
enum class RiskLevel(val range: IntRange, val userAction: String) {
    LOW(0..19, "Proceed carefully"),
    GUARDED(20..39, "Verify before acting"),
    MEDIUM(40..60, "Do not act until verified"),
    HIGH(61..80, "Stop and verify independently"),
    CRITICAL(81..100, "Do not open, pay, or share sensitive information"),
    ;

    companion object {
        /** @throws IllegalArgumentException if [score] is outside 0..100. */
        fun forScore(score: Int): RiskLevel =
            entries.firstOrNull { score in it.range }
                ?: throw IllegalArgumentException("Risk score out of range: $score")
    }
}

/**
 * How much the assessment can be trusted. Deliberately independent of [RiskLevel]: a high
 * risk score held with low confidence is a real and common state, and collapsing the two
 * would hide exactly the uncertainty the user needs to see.
 */
enum class Confidence {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH,
}

/**
 * A safe action offered to the user. There is no action here that opens, pays, sends or
 * installs anything — Principle 3 is enforced by the absence of such a case, not by a check.
 */
enum class SafeAction(val label: String) {
    DO_NOT_OPEN_LINK("Do not open the link"),
    DO_NOT_PAY("Do not pay"),
    DO_NOT_SHARE_OTP("Do not share your OTP or PIN"),
    DO_NOT_SHARE_CREDENTIALS("Do not share account credentials"),
    VERIFY_VIA_OFFICIAL_CHANNEL("Verify through the official app or helpline"),
    REPORT_OR_BLOCK("Report or block the sender"),
    PROCEED_CAREFULLY("Proceed carefully"),
}

/**
 * The verdict of the deterministic risk engine.
 *
 * [inferences] are conclusions the engine drew; they are outputs and are never fed back in as
 * scoring inputs. See [Finding] for why that separation matters.
 */
data class RiskResult(
    val score: Int,
    val level: RiskLevel,
    val confidence: Confidence,
    val reasons: List<String>,
    val observations: List<Observation>,
    val externalEvidence: List<ExternalEvidence>,
    val inferences: List<Inference>,
    val recommendedActions: List<SafeAction>,
    val degraded: Boolean,
) {
    init {
        require(score in 0..100) { "Risk score must be within 0..100, was $score" }
        require(level == RiskLevel.forScore(score)) {
            "Risk level $level does not match score $score"
        }
    }
}
