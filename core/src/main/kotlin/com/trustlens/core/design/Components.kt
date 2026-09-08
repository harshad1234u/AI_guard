package com.trustlens.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The shared surfaces, headings, buttons and states.
 *
 * Before this module existed, five screens each carried a private copy of the same disclosure
 * card. Duplicated styling is a correctness problem here rather than a tidiness one: a
 * privacy disclosure that looks like a heading on one screen and like body text on another
 * teaches the user to skip it.
 */

/**
 * The base surface. Flat, bordered, no drop shadow.
 *
 * Depth is drawn with a hairline border rather than elevation. A security screen is read, not
 * admired, and a stack of shadowed cards turns a list of evidence into visual noise.
 */
@Composable
fun TrustLensCard(
    modifier: Modifier = Modifier,
    background: Color = TrustLens.colors.surface,
    border: Color = TrustLens.colors.border,
    shape: Shape = Corner.card,
    onClick: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
        content = content,
    )
}

/**
 * A section heading: small caps, wide tracking, with a hairline rule running to the edge.
 *
 * The rule is what makes the result screen readable as a sequence — input, investigation,
 * evidence, risk, action — rather than as one long column of paragraphs.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = TrustLens.colors.textMuted,
        )
        Box(Modifier.weight(1f).height(1.dp).background(TrustLens.colors.border))
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = TrustLens.colors.textMuted,
            )
        }
    }
}

/**
 * A stated limitation, disclosure or caveat.
 *
 * One definition, used by every surface. These carry the sentences that keep the product
 * honest — "this is not a confirmation of safety", "TrustLens cannot read your mailbox" — so
 * they are given a deliberate, consistent, quiet treatment rather than five different ones.
 */
@Composable
fun NoticeCard(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(Corner.inner)
            .background(TrustLens.colors.accentSoft)
            .padding(Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(TrustLens.colors.accent.copy(alpha = 0.5f)),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )
    }
}

/**
 * Something the user must not miss: no audio arriving, listening stopped, a peak score.
 *
 * Distinct from [NoticeCard] on purpose. A notice is context; an alert is a condition that
 * changes what the screen currently means.
 */
@Composable
fun AlertCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    tone: Color = TrustLens.colors.danger,
) {
    TrustLensCard(
        modifier = modifier.fillMaxWidth(),
        background = tone.copy(alpha = 0.10f),
        border = tone.copy(alpha = 0.35f),
        shape = Corner.inner,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = tone,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.text,
        )
    }
}

/** The one filled button on a screen. There is never more than one. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = Corner.inner,
        colors = ButtonDefaults.buttonColors(
            containerColor = TrustLens.colors.accent,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = Corner.inner,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = TrustLens.colors.accent)
    }
}

/**
 * The screen header: a back affordance, the screen's name, and nothing else.
 *
 * The back control is a 48dp touch target with a real content description, because the glyph
 * beside it is a drawn chevron with no text of its own.
 */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("Back", style = MaterialTheme.typography.titleSmall)
                }
            }
            Box(Modifier.weight(1f))
            trailing()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = TrustLens.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = TrustLens.colors.textMuted,
            )
        }
    }
}

/**
 * The one loading state.
 *
 * A spinner and a label, never a percentage. The pipeline exposes no progress fraction, and
 * inventing one would be a claim about how far along an analysis is that nothing can support.
 */
@Composable
fun LoadingState(
    label: String = "Investigating…",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Space.xl),
        horizontalArrangement = Arrangement.spacedBy(Space.md, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = TrustLens.colors.accent,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TrustLens.colors.textMuted)
    }
}

/**
 * Nothing was read, heard or decoded.
 *
 * Worded as an absence of input, never as an absence of risk — "no assessment was made"
 * rather than anything a user could read as "nothing wrong here" (Principle 2).
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    hints: List<String> = emptyList(),
) {
    TrustLensCard(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TrustLens.colors.text)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = TrustLens.colors.textMuted)
        hints.forEach { hint ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("•", style = MaterialTheme.typography.bodyMedium, color = TrustLens.colors.textMuted)
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = TrustLens.colors.textMuted)
            }
        }
    }
}

/** Something went wrong in TrustLens itself — a camera failure, a recogniser that quit. */
@Composable
fun ErrorState(title: String, body: String, modifier: Modifier = Modifier) {
    AlertCard(title = title, body = body, modifier = modifier)
}

/**
 * The state that matters most: TrustLens could not establish something.
 *
 * Deliberately its own component, and deliberately never green. "Unable to confirm" and
 * "safe" are different statements, and Principle 2 exists because collapsing them is how a
 * scam detector ends up vouching for a scam.
 */
@Composable
fun UnknownState(
    title: String = "Unable to confirm",
    body: String,
    modifier: Modifier = Modifier,
) {
    TrustLensCard(
        modifier = modifier.fillMaxWidth(),
        background = TrustLens.colors.surface,
        border = TrustLens.colors.borderStrong,
        shape = Corner.inner,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.md),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.titleMedium,
                color = TrustLens.colors.unknown,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Text(title, style = MaterialTheme.typography.titleSmall, color = TrustLens.colors.text)
        }
        Text(body, style = MaterialTheme.typography.bodySmall, color = TrustLens.colors.textMuted)
    }
}

/**
 * Content quoted back to the user: a transcript, an OCR read, a decoded payload.
 *
 * Rendered as inert text on a recessed surface. Nothing in here is ever a link, a button or a
 * payment target — the recessed treatment is the visual promise that it is quoted, not
 * offered.
 */
@Composable
fun QuotedContent(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    TrustLensCard(
        modifier = modifier.fillMaxWidth(),
        background = TrustLens.colors.screen,
        shape = Corner.inner,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = TrustLens.colors.textMuted,
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TrustLens.colors.text)
    }
}

/** A label above its value. Used for URL structure and QR payload fields. */
@Composable
fun FieldRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TrustLens.colors.textMuted,
        )
        // Long hostnames and payloads wrap rather than truncate: a link the user cannot fully
        // read is a link they cannot check.
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TrustLens.colors.text,
        )
    }
}

/** A bulleted line. One definition, so every list in the app indents identically. */
@Composable
fun BulletRow(
    text: String,
    modifier: Modifier = Modifier,
    marker: String = "•",
    color: Color = TrustLens.colors.text,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            text = marker,
            style = MaterialTheme.typography.bodyMedium,
            color = TrustLens.colors.textMuted,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/**
 * A small status pill. Used for "Listening", "Analysing", and the payload type on QR.
 *
 * Never used to state risk on its own — a band always travels with its number and its name.
 */
@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = TrustLens.colors.accent,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(Corner.chip)
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = Space.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tone,
        )
    }
}
