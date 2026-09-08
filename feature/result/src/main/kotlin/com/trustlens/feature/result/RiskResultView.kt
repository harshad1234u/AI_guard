package com.trustlens.feature.result

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.trustlens.core.design.BulletRow
import com.trustlens.core.design.Corner
import com.trustlens.core.design.NoticeCard
import com.trustlens.core.design.ReasonRow
import com.trustlens.core.design.RiskScoreCard
import com.trustlens.core.design.SafeActionCard
import com.trustlens.core.design.SectionHeader
import com.trustlens.core.design.Space
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensCard
import com.trustlens.core.design.InvestigationTrail
import com.trustlens.core.design.UnknownState
import com.trustlens.core.design.investigationStepsFor
import com.trustlens.core.design.verdictTone
import com.trustlens.core.design.verdictWord
import com.trustlens.domain.AnalysisResult
import com.trustlens.domain.IndicatorKind

/**
 * Shared risk verdict presentation, used by every input flow so a Critical result looks
 * identical whether it came from text, camera, QR, voice or email.
 *
 * This is presentation only. It renders what the risk engine decided and never computes,
 * re-weights or re-interprets a score.
 *
 * ## The order, and why it is this order
 *
 * ```
 * RISK  ->  WHY  ->  INVESTIGATION  ->  EVIDENCE  ->  EXTERNAL  ->  SAFE ACTION
 * ```
 *
 * The verdict leads because a user reading this may be mid-scam and has seconds. What follows
 * is the audit trail behind it, in the order the pipeline produced it, ending in what to
 * actually do. Every section is a rendering of a field on [AnalysisResult]; there is no
 * heading here that TrustLens cannot fill from work it really did.
 */
@Composable
fun RiskResultView(
    result: AnalysisResult,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        RiskScoreCard(risk = result.risk)

        // The one action to take, directly under the score. It is the point of the screen.
        SafeActionCard(actions = result.risk.recommendedActions, level = result.risk.level)

        if (result.risk.reasons.isNotEmpty()) {
            Section(
                title = "Why this score",
                caption = "Each line is a scored input. They are the arithmetic behind the " +
                    "number above, not a summary of it.",
            ) {
                result.risk.reasons.forEach { ReasonRow(it) }
            }
        }

        Section(
            title = "Investigation",
            caption = "What TrustLens did before deciding — not just what it concluded.",
        ) {
            InvestigationTrail(steps = investigationStepsFor(result))
        }

        val evidenceItems = evidenceItems(result)
        if (evidenceItems.isNotEmpty()) {
            Section(
                title = "Evidence",
                caption = "Quoted from the content itself. Nothing here is a link, and " +
                    "nothing here is tappable.",
            ) {
                TrustLensCard(background = TrustLens.colors.screen, shape = Corner.inner) {
                    evidenceItems.forEach { BulletRow(it) }
                }
            }
        }

        Section(title = "External verification") {
            ExternalVerification(result)
        }

        if (result.inferences.isNotEmpty()) {
            // Kept visually distinct from evidence: these are conclusions, not observations.
            Section(
                title = "What this suggests",
                caption = "Conclusions TrustLens drew. They are outputs of the score and are " +
                    "never scored themselves.",
            ) {
                result.inferences.forEach { BulletRow(it.summary, marker = "→") }
            }
        }

        Disclosures(result)
    }
}

/**
 * Extracted entities, quoted from the message.
 *
 * Rendered as inert text. A URL here is never a link and is never clickable — the user has
 * to make their own decision outside the app, which is the whole point of the product.
 */
private fun evidenceItems(result: AnalysisResult): List<String> {
    val indicators = result.context.indicators
    return buildList {
        indicators.urls.forEach { add("Link: $it") }
        indicators.upiIds.forEach { add("UPI ID: $it") }
        indicators.phoneNumbers.forEach { add("Phone: $it") }
        indicators.amounts.forEach { add("Amount: $it") }
        indicators.organisations.forEach { add("Mentions: $it") }
        indicators.emails.forEach { add("Email: $it") }
        result.context.indicators.matches
            .filter { it.kind == IndicatorKind.OTP_REFERENCE }
            .forEach { add("Mentions an OTP: \"${it.text}\"") }
        // External reports are no longer listed here — they have their own section, so that
        // what an outside source claimed stays distinct from what was in the content.
    }
}

/**
 * What outside sources said, kept in its own section and visually separate from the
 * on-device findings above.
 *
 * The separation is the point. "TrustLens noticed this message asks for an OTP" and
 * "VirusTotal reports twelve engines flagging this host" are different kinds of claim with
 * different reliability, and blending them into one list would hide which is which.
 *
 * A provider with nothing to report is shown, not omitted — worded as an absence of a
 * report rather than as a clean bill of health — and when nothing could be consulted at all
 * the section says so plainly instead of quietly disappearing.
 */
@Composable
private fun ExternalVerification(result: AnalysisResult) {
    val evidence = result.externalEvidence

    if (evidence.isEmpty()) {
        UnknownState(
            title = "No external source could be consulted",
            body = "Nothing here confirms this content is safe — it means nobody was asked.",
        )
        return
    }

    TrustLensCard(background = TrustLens.colors.screen, shape = Corner.inner) {
        evidence.forEach { report ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = report.sourceName,
                        style = MaterialTheme.typography.titleSmall,
                        color = TrustLens.colors.text,
                    )
                    // The verdict in words. There is no tick anywhere in this section:
                    // ReputationVerdict has no SAFE case, and a tick would invent one.
                    Text(
                        text = verdictWord(report.verdict),
                        style = MaterialTheme.typography.labelSmall,
                        color = verdictTone(report.verdict),
                    )
                }
                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrustLens.colors.textMuted,
                )
            }
        }

        if (result.risk.degraded) {
            Text(
                text = "This external picture is incomplete.",
                style = MaterialTheme.typography.bodySmall,
                color = TrustLens.colors.caution,
            )
        }
    }
}

/**
 * The caveats, collected under one disclosure that opens by default when it matters.
 *
 * They were previously scattered as four separate cards at the top of the screen, above the
 * verdict. That put the model's training-data caveat in front of the score, which is the
 * wrong order for someone deciding whether to trust a link — but hiding them is not an option
 * either, so they sit at the foot of the screen, and the two that change what the verdict
 * *means* (a mock classifier, missing threat intelligence) are expanded from the start.
 */
@Composable
private fun Disclosures(result: AnalysisResult) {
    val notices = disclosureTexts(result)
    if (notices.isEmpty()) return

    // Anything that qualifies the verdict itself is open on arrival; the routine model
    // attribution is not.
    val importantByDefault = result.usedMockClassifier ||
        result.risk.degraded ||
        result.classifierUnavailableReason != null

    var expanded by remember(result) { mutableStateOf(importantByDefault) }

    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "HOW THIS WAS PRODUCED",
                style = MaterialTheme.typography.labelLarge,
                color = TrustLens.colors.textMuted,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (expanded) "Hide" else "Show (${notices.size})",
                style = MaterialTheme.typography.labelMedium,
                color = TrustLens.colors.accent,
            )
        }

        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                notices.forEach { NoticeCard(it) }
            }
        }
    }
}

/**
 * Says which model produced the ML signal, what it was trained on, and — when it did not
 * run at all — that the verdict rests on rules alone.
 *
 * The training-data caveat is shown next to the result rather than buried in an about
 * screen, because a model trained on English SMS spam scoring an Indian banking scam is
 * exactly the situation where a user deserves to know what they are looking at.
 */
private fun disclosureTexts(result: AnalysisResult): List<String> = buildList {
    if (result.usedMockClassifier) {
        add(
            "Mock classifier in use — this is a keyword stand-in, not a trained model. " +
                "Its probability is not a real prediction.",
        )
    }

    if (result.risk.degraded) {
        add(
            "Threat intelligence unavailable. This assessment is based on on-device " +
                "analysis only — it is not an external verification.",
        )
    }

    if (!result.usedMockClassifier) {
        val reason = result.classifierUnavailableReason
        if (reason != null) {
            // A flow that never intended to classify (a bare URL, a payment payload) is not a
            // model failure, and must not be dressed up as one — the two read very differently
            // to someone deciding whether to trust the verdict.
            val byDesign = "does not use the text classifier" in reason
            add(
                if (byDesign) {
                    "No text classifier applies here — there is no message wording to read. " +
                        "This assessment comes from deterministic analysis."
                } else {
                    "The on-device model did not run: $reason. This assessment comes from " +
                        "deterministic analysis only."
                },
            )
        } else {
            result.context.mlSignal?.let { signal ->
                add(
                    "Model: ${signal.modelVersion}, running on ${signal.backend.name}. " +
                        "It is trained on a public English SMS spam corpus, not on Indian " +
                        "banking scams — the scam-specific findings above come from " +
                        "deterministic analysis.",
                )
            }
        }
    }
}

/** A titled block. One definition, so every section on the screen has the same rhythm. */
@Composable
private fun Section(
    title: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        SectionHeader(title)
        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = TrustLens.colors.textMuted,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        content()
    }
}
