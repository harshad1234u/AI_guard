package com.trustlens.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trustlens.domain.Confidence
import com.trustlens.domain.RiskLevel
import com.trustlens.domain.RiskResult
import com.trustlens.domain.SafeAction

/**
 * How TrustLens shows a risk verdict.
 *
 * Three rules, all of them security requirements rather than styling preferences:
 *
 *  1. **Never colour alone.** Every band carries its number, its name and a position on a
 *     scale. A user with a colour-vision deficiency, or a phone in bright sun, reads the same
 *     verdict as everyone else.
 *  2. **The score is what the engine produced.** Nothing here computes, rounds, rescales or
 *     re-bands anything. `RiskResult` arrives finished.
 *  3. **Confidence sits beside risk, never inside it.** A HIGH score held with LOW confidence
 *     is a real state, and merging the two would hide exactly the uncertainty that matters.
 */

/** The band's colour. The single resolution point, so a band cannot be drawn two ways. */
@Composable
fun riskColor(level: RiskLevel): Color = TrustLens.colors.forRisk(level)

/**
 * The dominant element of the result screen: the band, then the number, then what to do.
 *
 * The band name is stated *above* the number because it is the part a user acts on. "74" is
 * only meaningful once you know the scale; "HIGH RISK" is meaningful immediately.
 */
@Composable
fun RiskScoreCard(
    risk: RiskResult,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val tone = riskColor(risk.level)

    TrustLensCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${risk.level.name} risk. Score ${risk.score} out of 100. " +
                        "Confidence ${risk.confidence.name.lowercase()}. ${risk.level.userAction}"
            },
        background = tone.copy(alpha = 0.08f),
        border = tone.copy(alpha = 0.30f),
        shape = Corner.hero,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.xl),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(
                text = "${risk.level.name} RISK",
                style = MaterialTheme.typography.labelLarge,
                color = tone,
            )

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = risk.score.toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = tone,
                )
                Text(
                    text = "/100",
                    style = MaterialTheme.typography.titleMedium,
                    color = tone.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 10.dp, start = 2.dp),
                )
            }

            RiskScale(level = risk.level, score = risk.score)

            Text(
                text = risk.level.userAction,
                style = MaterialTheme.typography.titleMedium,
                color = TrustLens.colors.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Space.xs),
            )

            // Confidence in the assessment, kept visibly separate from the risk itself.
            Text(
                text = "Confidence in this assessment: ${confidenceLabel(risk.confidence)}",
                style = MaterialTheme.typography.bodySmall,
                color = TrustLens.colors.textMuted,
                textAlign = TextAlign.Center,
            )

            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TrustLens.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun confidenceLabel(confidence: Confidence): String = when (confidence) {
    Confidence.UNKNOWN -> "unknown"
    Confidence.LOW -> "low"
    Confidence.MEDIUM -> "medium"
    Confidence.HIGH -> "high"
}

/**
 * The five bands as a segmented scale, with the current one filled and marked.
 *
 * This is the non-colour channel: the position of the marker states the band even if every
 * segment rendered identically. Segment widths are proportional to the real band ranges from
 * `RiskLevel`, so the picture cannot drift from the model.
 */
@Composable
fun RiskScale(level: RiskLevel, score: Int, modifier: Modifier = Modifier) {
    val tone = riskColor(level)
    val progress by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(durationMillis = 450),
        label = "riskScale",
    )

    Column(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            RiskLevel.entries.forEach { band ->
                val reached = band.ordinal <= level.ordinal
                Box(
                    Modifier
                        .weight((band.range.last - band.range.first + 1).toFloat())
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (reached) {
                                TrustLens.colors.forRisk(band).copy(alpha = if (band == level) 1f else 0.45f)
                            } else {
                                TrustLens.colors.border
                            },
                        ),
                )
            }
        }

        // The marker's exact position on the 0..100 line, so the number and the picture agree.
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(constraints.maxWidth, placeable.height) {
                            val x = ((constraints.maxWidth - placeable.width) * progress).toInt()
                            placeable.placeRelative(x.coerceAtLeast(0), 0)
                        }
                    }
                    .width(2.dp)
                    .height(6.dp)
                    .background(tone),
            )
        }
    }
}

/**
 * A compact band marker for places with no room for the full card — the live verdict on Live
 * Guard, or a peak figure.
 *
 * Still never colour alone: the band name is inside the chip.
 */
@Composable
fun RiskBadge(
    level: RiskLevel,
    score: Int,
    modifier: Modifier = Modifier,
    prefix: String? = null,
) {
    val tone = riskColor(level)
    Row(
        modifier = modifier
            .clip(Corner.chip)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = Space.md, vertical = 6.dp)
            .semantics {
                contentDescription =
                    "${prefix.orEmpty()} $score out of 100, ${level.name} risk".trim()
            },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.clearAndSetSemantics { }) {
            prefix?.let {
                Text(
                    text = "$it ",
                    style = MaterialTheme.typography.labelMedium,
                    color = TrustLens.colors.textMuted,
                )
            }
            Text(
                text = "$score/100 ${level.name}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tone,
            )
        }
    }
}

/**
 * One scored reason, split into its points column and its wording.
 *
 * The engine emits reasons already carrying their arithmetic — `"Requests an OTP or PIN
 * (+22)"`. This splits that trailing `(+N)` off for layout only; the number and the text both
 * come from the engine's own string, and a reason that does not carry one is rendered whole
 * rather than guessed at. Nothing here adds, re-weights or reorders anything.
 */
@Composable
fun ReasonRow(reason: String, modifier: Modifier = Modifier) {
    val (points, text) = splitPoints(reason)

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.Top,
    ) {
        if (points != null) {
            Text(
                text = points,
                style = MaterialTheme.typography.titleSmall,
                color = if (points == "+0") TrustLens.colors.textMuted else TrustLens.colors.text,
                textAlign = TextAlign.End,
                modifier = Modifier.width(38.dp),
            )
        } else {
            Spacer(Modifier.width(38.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = TrustLens.colors.textMuted,
        )
    }
}

/**
 * Pulls a trailing `(+N)` off an engine reason.
 *
 * Returns the points and the remaining wording, or `null` points and the untouched string
 * when the pattern is absent — an engine reason that stops carrying its arithmetic must still
 * appear in full rather than silently lose its tail.
 */
internal fun splitPoints(reason: String): Pair<String?, String> {
    val match = TRAILING_POINTS.find(reason) ?: return null to reason
    return "+${match.groupValues[1]}" to reason.removeRange(match.range).trim().trimEnd('—', '-', ',')
}

private val TRAILING_POINTS = Regex("""\s*\(\+(\d+)\)\s*$""")

/**
 * A recommended safe action.
 *
 * Every member of `SafeAction` declines or defers — there is no case that opens, pays, calls,
 * installs or sends, and this component deliberately has no `onClick`. It states what to do;
 * the user does it, outside the app. That is Principle 3 rendered as UI: nothing on this card
 * is tappable, so there is nothing here that could act on the user's behalf.
 */
@Composable
fun SafeActionCard(
    actions: List<SafeAction>,
    level: RiskLevel,
    modifier: Modifier = Modifier,
    /**
     * Drops the card chrome and the closing sentence, for the one place this appears *inside*
     * another card — the running Live Guard verdict. A bordered card nested in a bordered card
     * reads as noise, and that screen already has no button that could act on anything.
     */
    compact: Boolean = false,
) {
    if (actions.isEmpty()) return
    val tone = riskColor(level)

    val body: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        actions.forEachIndexed { index, action ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The first action is the one to act on; the rest support it.
                Box(
                    Modifier
                        .width(3.dp)
                        .height(if (index == 0) 22.dp else 18.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index == 0) tone else tone.copy(alpha = 0.4f)),
                )
                Text(
                    text = action.label,
                    style = if (index == 0) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (index == 0) TrustLens.colors.text else TrustLens.colors.textMuted,
                )
            }
        }

        if (!compact) {
            Text(
                text = "TrustLens does not act for you. It never opens a link, makes a " +
                    "payment, places a call or installs anything — these are steps for you " +
                    "to take.",
                style = MaterialTheme.typography.bodySmall,
                color = TrustLens.colors.textMuted,
            )
        }
    }

    if (compact) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
            content = body,
        )
    } else {
        TrustLensCard(
            modifier = modifier.fillMaxWidth(),
            background = tone.copy(alpha = 0.07f),
            border = tone.copy(alpha = 0.28f),
            content = body,
        )
    }
}
