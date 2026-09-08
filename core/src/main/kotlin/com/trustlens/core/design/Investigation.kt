package com.trustlens.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trustlens.domain.AnalysisResult
import com.trustlens.domain.InvestigationSource
import com.trustlens.domain.ReputationVerdict

/**
 * The investigation trail — the part of TrustLens that is not a classifier.
 *
 * Most scam detectors show a label. This shows the work: what was read, what was pulled out
 * of it, which links were taken apart, whether the model could speak to this content, and who
 * was asked about the hostnames. It is the visible form of the product's whole claim, so
 * every step is derived from what actually happened in [AnalysisResult] and nothing is
 * asserted that the pipeline did not do.
 *
 * A step that did not run says so. A step that ran and found nothing says *that*, worded so
 * it can never be read as a clean bill of health (Principle 2).
 */

/** What became of one stage of the investigation. */
enum class StepState {
    /** The stage ran. */
    DONE,

    /** The stage does not apply to this kind of content — a bare URL has no wording to read. */
    NOT_APPLICABLE,

    /** The stage should have run and could not. Never quietly hidden. */
    UNAVAILABLE,
}

data class InvestigationStep(
    val title: String,
    val detail: String,
    val state: StepState,
)

/**
 * Builds the trail from a finished result.
 *
 * Read-only: it inspects [AnalysisResult] and produces strings. It cannot change a score, a
 * band or an action, and it invents no stage that the pipeline does not have.
 */
fun investigationStepsFor(result: AnalysisResult): List<InvestigationStep> {
    val context = result.context
    val indicators = context.indicators

    val steps = mutableListOf<InvestigationStep>()

    steps += InvestigationStep(
        title = "Content read",
        detail = "${sourceLabel(context.source)} · ${context.rawContent.length} characters, " +
            "analysed on this device",
        state = StepState.DONE,
    )

    val extracted = buildList {
        if (indicators.urls.isNotEmpty()) add("${indicators.urls.size} link".plural(indicators.urls.size))
        if (indicators.upiIds.isNotEmpty()) add("${indicators.upiIds.size} UPI ID".plural(indicators.upiIds.size))
        if (indicators.emails.isNotEmpty()) add("${indicators.emails.size} address".plural(indicators.emails.size, "es"))
        if (indicators.phoneNumbers.isNotEmpty()) {
            add("${indicators.phoneNumbers.size} phone number".plural(indicators.phoneNumbers.size))
        }
        if (indicators.organisations.isNotEmpty()) add("named organisations")
        if (indicators.otpRequest) add("an OTP request")
        if (indicators.credentialRequest) add("a credential request")
        if (indicators.paymentRequest) add("a payment request")
        if (indicators.accountThreat) add("an account threat")
        if (indicators.urgency) add("urgency wording")
    }
    steps += InvestigationStep(
        title = "Indicators extracted",
        detail = if (extracted.isEmpty()) {
            "Deterministic rules found none of the patterns they look for. That is an " +
                "absence of these signals, not a verdict of safety."
        } else {
            "Deterministic rules found ${extracted.joinToString(", ")}."
        },
        state = StepState.DONE,
    )

    steps += if (indicators.urls.isEmpty()) {
        InvestigationStep(
            title = "Link structure examined",
            detail = "No links were present in this content.",
            state = StepState.NOT_APPLICABLE,
        )
    } else {
        InvestigationStep(
            title = "Link structure examined",
            detail = "${indicators.urls.size} link".plural(indicators.urls.size) +
                " taken apart offline — host, domain, port and path. " +
                "Nothing was opened, resolved or visited.",
            state = StepState.DONE,
        )
    }

    steps += when {
        result.usedMockClassifier -> InvestigationStep(
            title = "On-device model",
            detail = "A keyword stand-in ran, not a trained model. Its probability is not a " +
                "real prediction.",
            state = StepState.UNAVAILABLE,
        )

        result.classifierUnavailableReason != null -> InvestigationStep(
            title = "On-device model",
            detail = result.classifierUnavailableReason!!,
            state = if ("does not use the text classifier" in result.classifierUnavailableReason!!) {
                StepState.NOT_APPLICABLE
            } else {
                StepState.UNAVAILABLE
            },
        )

        context.mlSignal != null -> InvestigationStep(
            title = "On-device model",
            detail = "${context.mlSignal!!.modelVersion} ran on " +
                "${context.mlSignal!!.backend.name}, entirely on this device.",
            state = StepState.DONE,
        )

        else -> InvestigationStep(
            title = "On-device model",
            detail = "Did not run. This assessment rests on deterministic rules alone.",
            state = StepState.UNAVAILABLE,
        )
    }

    val evidence = result.externalEvidence
    val subjects = evidence.map { it.subject }.distinct()
    val providers = evidence.map { it.sourceName }.distinct()
    steps += when {
        evidence.isEmpty() -> InvestigationStep(
            title = "Threat intelligence",
            detail = "No external source could be consulted. Nothing here confirms this " +
                "content is safe — it means nobody was asked.",
            state = StepState.UNAVAILABLE,
        )

        else -> InvestigationStep(
            title = "Threat intelligence",
            detail = "${providers.size} provider".plural(providers.size) +
                " asked about ${subjects.size} hostname".plural(subjects.size) +
                " (${subjects.joinToString(", ")}). Hostnames only — never the content.",
            state = if (result.risk.degraded) StepState.UNAVAILABLE else StepState.DONE,
        )
    }

    steps += InvestigationStep(
        title = "Risk calculated",
        detail = "${result.risk.reasons.size} scored reason".plural(result.risk.reasons.size) +
            " combined by the deterministic engine into ${result.risk.score}/100, " +
            "${result.risk.level.name}.",
        state = StepState.DONE,
    )

    return steps
}

private fun String.plural(count: Int, suffix: String = "s") = if (count == 1) this else this + suffix

private fun sourceLabel(source: InvestigationSource): String = when (source) {
    InvestigationSource.SMS -> "Message"
    InvestigationSource.TEXT -> "Text"
    InvestigationSource.URL -> "Link"
    InvestigationSource.CAMERA_OCR -> "Camera / OCR"
    InvestigationSource.QR -> "QR code"
    InvestigationSource.VOICE -> "Speech"
    InvestigationSource.PHONE_NUMBER -> "Phone number"
    InvestigationSource.EMAIL -> "Email"
}

/**
 * The trail, drawn as a connected sequence.
 *
 * The connecting rule is the point: it reads as one process with stages, not as a checklist
 * of unrelated features. A stage that could not run breaks the rule's colour rather than
 * disappearing from it.
 */
@Composable
fun InvestigationTrail(
    steps: List<InvestigationStep>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            InvestigationRow(step = step, isLast = index == steps.lastIndex)
        }
    }
}

@Composable
private fun InvestigationRow(step: InvestigationStep, isLast: Boolean) {
    val tone = when (step.state) {
        StepState.DONE -> TrustLens.colors.checked
        StepState.NOT_APPLICABLE -> TrustLens.colors.textMuted
        StepState.UNAVAILABLE -> TrustLens.colors.caution
    }

    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${step.title}: ${stateWord(step.state)}. ${step.detail}"
        },
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        // The rail: a marker, and a line down to the next stage.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(18.dp).clearAndSetSemantics { },
        ) {
            StepMarker(step.state, tone)
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(if (step.detail.length > 90) 56.dp else 34.dp)
                        .background(TrustLens.colors.border),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else Space.xs),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TrustLens.colors.text,
                )
                // The state in words, so the marker is never the only signal.
                Text(
                    text = stateWord(step.state),
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                )
            }
            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodySmall,
                color = TrustLens.colors.textMuted,
            )
        }
    }
}

@Composable
private fun StepMarker(state: StepState, tone: Color) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = when (state) {
                StepState.DONE -> "✓"
                StepState.NOT_APPLICABLE -> "–"
                StepState.UNAVAILABLE -> "?"
            },
            style = MaterialTheme.typography.labelSmall,
            color = tone,
        )
    }
}

private fun stateWord(state: StepState): String = when (state) {
    StepState.DONE -> "checked"
    StepState.NOT_APPLICABLE -> "not applicable"
    StepState.UNAVAILABLE -> "unable to confirm"
}

/**
 * A marker for what an outside source reported.
 *
 * There is no tick here for any verdict. `ReputationVerdict` has no SAFE case, and a green
 * tick beside "no report" would invent one on screen — which is precisely the failure
 * Principle 2 exists to prevent.
 */
@Composable
fun verdictTone(verdict: ReputationVerdict): Color = when (verdict) {
    ReputationVerdict.CONFIRMED -> TrustLens.colors.danger
    ReputationVerdict.SUSPICIOUS, ReputationVerdict.CONFLICTING -> TrustLens.colors.caution
    ReputationVerdict.NO_DETECTION, ReputationVerdict.UNKNOWN, ReputationVerdict.ERROR ->
        TrustLens.colors.unknown
}

fun verdictWord(verdict: ReputationVerdict): String = when (verdict) {
    ReputationVerdict.CONFIRMED -> "REPORTED MALICIOUS"
    ReputationVerdict.SUSPICIOUS -> "INDICATORS OF CONCERN"
    ReputationVerdict.CONFLICTING -> "SOURCES DISAGREE"
    ReputationVerdict.NO_DETECTION -> "NO REPORT"
    ReputationVerdict.UNKNOWN -> "COULD NOT ASSESS"
    ReputationVerdict.ERROR -> "LOOKUP FAILED"
}
