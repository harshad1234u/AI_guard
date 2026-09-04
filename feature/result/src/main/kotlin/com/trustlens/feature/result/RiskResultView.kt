package com.trustlens.feature.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trustlens.domain.AnalysisResult
import com.trustlens.domain.IndicatorKind
import com.trustlens.domain.ReputationVerdict
import com.trustlens.domain.RiskLevel

/**
 * Shared risk verdict presentation, used by every input flow so a Critical result looks
 * identical whether it came from text, camera, QR or voice.
 *
 * This is presentation only. It renders what the risk engine decided and never computes,
 * re-weights or re-interprets a score.
 */
@Composable
fun RiskResultView(
    result: AnalysisResult,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ScoreBanner(result)

        if (result.usedMockClassifier) {
            MockClassifierNotice()
        }
        ModelAttribution(result)
        if (result.risk.degraded) {
            DegradedNotice()
        }

        if (result.risk.reasons.isNotEmpty()) {
            Section(title = "Why?") {
                result.risk.reasons.forEach { BulletRow("•", it) }
            }
        }

        val evidenceItems = evidenceItems(result)
        if (evidenceItems.isNotEmpty()) {
            Section(title = "Evidence") {
                evidenceItems.forEach { BulletRow("•", it) }
            }
        }

        ExternalVerification(result)

        if (result.inferences.isNotEmpty()) {
            // Kept visually distinct from evidence: these are conclusions, not observations.
            Section(title = "What this suggests") {
                result.inferences.forEach { BulletRow("→", it.summary) }
            }
        }

        if (result.risk.recommendedActions.isNotEmpty()) {
            Section(title = "Recommended") {
                result.risk.recommendedActions.forEach {
                    Text(
                        text = it.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = riskColor(result.risk.level),
                    )
                }
            }
        }
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

@Composable
private fun ScoreBanner(result: AnalysisResult) {
    val color = riskColor(result.risk.level)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${result.risk.score} / 100",
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                text = "${result.risk.level.name} RISK",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            // Confidence is shown next to risk, never merged into it.
            Text(
                text = "Confidence: ${result.risk.confidence.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = result.risk.level.userAction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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

    Section(title = "External verification") {
        if (evidence.isEmpty()) {
            Text(
                text = "No external source could be consulted. Nothing here confirms this " +
                    "content is safe — it means nobody was asked.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Section
        }

        evidence.forEach { report ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = verdictMarker(report.verdict),
                    style = MaterialTheme.typography.bodyMedium,
                    color = verdictColour(report.verdict),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = report.sourceName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(text = report.summary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (result.risk.degraded) {
            Text(
                text = "This external picture is incomplete.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun verdictMarker(verdict: ReputationVerdict): String = when (verdict) {
    ReputationVerdict.CONFIRMED -> "⚠"
    ReputationVerdict.SUSPICIOUS -> "⚠"
    ReputationVerdict.CONFLICTING -> "!"
    // Neither a tick nor a cross: this provider simply did not answer the question.
    ReputationVerdict.NO_DETECTION, ReputationVerdict.UNKNOWN, ReputationVerdict.ERROR -> "–"
}

@Composable
private fun verdictColour(verdict: ReputationVerdict): Color = when (verdict) {
    ReputationVerdict.CONFIRMED -> Color(0xFFB3261E)
    ReputationVerdict.SUSPICIOUS, ReputationVerdict.CONFLICTING -> Color(0xFFB26A00)
    // Deliberately not green. Nothing in this section is ever an all-clear.
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Says which model produced the ML signal, what it was trained on, and — when it did not
 * run at all — that the verdict rests on rules alone.
 *
 * The training-data caveat is shown next to the result rather than buried in an about
 * screen, because a model trained on English SMS spam scoring an Indian banking scam is
 * exactly the situation where a user deserves to know what they are looking at.
 */
@Composable
private fun ModelAttribution(result: AnalysisResult) {
    if (result.usedMockClassifier) return

    result.classifierUnavailableReason?.let { reason ->
        // A flow that never intended to classify (a bare URL, a payment payload) is not a
        // model failure, and must not be dressed up as one — the two read very differently
        // to someone deciding whether to trust the verdict.
        val byDesign = "does not use the text classifier" in reason
        Notice(
            text = if (byDesign) {
                "No text classifier applies here — there is no message wording to read. " +
                    "This assessment comes from deterministic analysis."
            } else {
                "The on-device model did not run: $reason. This assessment comes from " +
                    "deterministic analysis only."
            },
        )
        return
    }

    val signal = result.context.mlSignal ?: return
    Notice(
        text = "Model: ${signal.modelVersion}, running on ${signal.backend.name}. " +
            "It is trained on a public English SMS spam corpus, not on Indian banking " +
            "scams — the scam-specific findings above come from deterministic analysis.",
    )
}

@Composable
private fun MockClassifierNotice() {
    Notice(
        text = "Mock classifier in use — this is a keyword stand-in, not a trained model. " +
            "Its probability is not a real prediction.",
    )
}

@Composable
private fun DegradedNotice() {
    Notice(
        text = "Threat intelligence unavailable. This assessment is based on on-device " +
            "analysis only — it is not an external verification.",
    )
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider()
        content()
    }
}

@Composable
private fun BulletRow(marker: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(marker, style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun riskColor(level: RiskLevel): Color = when (level) {
    RiskLevel.LOW -> Color(0xFF1B7F3B)
    RiskLevel.GUARDED -> Color(0xFF6E7B12)
    RiskLevel.MEDIUM -> Color(0xFFB26A00)
    RiskLevel.HIGH -> Color(0xFFC2410C)
    RiskLevel.CRITICAL -> Color(0xFFB3261E)
}
