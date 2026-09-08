package com.trustlens.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.trustlens.core.design.Corner
import com.trustlens.core.design.Glyph
import com.trustlens.core.design.Space
import com.trustlens.core.design.TrustLens
import com.trustlens.core.design.TrustLensCard
import com.trustlens.core.design.TrustLensGlyph
import com.trustlens.core.design.TrustLensMark
import com.trustlens.core.design.TrustLensTheme

/**
 * TrustLens home.
 *
 * Every entry maps to one phone-native input path, and every path converges on the same
 * investigation pipeline — so this screen is a router, not a feature list. The layout says so:
 * one dominant hero for the commonest case, a compact grid of the rest, and a strip along the
 * bottom naming the pipeline they all share.
 *
 * The question it has to answer in about two seconds is *what can TrustLens investigate?*
 */
@Composable
fun HomeScreen(
    onCheckMessage: () -> Unit = {},
    onCheckUrl: () -> Unit = {},
    onScanWithCamera: () -> Unit = {},
    onScanQr: () -> Unit = {},
    onVoiceCheck: () -> Unit = {},
    onLiveGuard: () -> Unit = {},
    onCheckEmail: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = TrustLens.colors.screen,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = Space.gutter, vertical = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            Header()

            HeroCard(onClick = onCheckMessage)

            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                BentoTile(
                    glyph = Glyph.LINK,
                    title = "Link",
                    description = "Investigate a suspicious URL without opening it",
                    onClick = onCheckUrl,
                )
                BentoTile(
                    glyph = Glyph.CAMERA,
                    title = "Camera",
                    description = "Read suspicious text through the camera",
                    onClick = onScanWithCamera,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                BentoTile(
                    glyph = Glyph.QR,
                    title = "QR",
                    description = "Inspect a QR payload safely — never opened, never paid",
                    onClick = onScanQr,
                )
                BentoTile(
                    glyph = Glyph.VOICE,
                    title = "Voice",
                    description = "Say back what you heard and have it checked",
                    onClick = onVoiceCheck,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                WideTile(
                    glyph = Glyph.EMAIL,
                    title = "Email",
                    description = "Share one email in",
                    onClick = onCheckEmail,
                )
                WideTile(
                    glyph = Glyph.LIVE,
                    title = "Live Guard",
                    description = "Listen on speakerphone",
                    onClick = onLiveGuard,
                )
            }

            PipelineStrip(modifier = Modifier.padding(top = Space.sm))
        }
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrustLensMark(size = 34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "TrustLens",
                style = MaterialTheme.typography.headlineSmall,
                color = TrustLens.colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "See the risk before you click.",
                style = MaterialTheme.typography.bodySmall,
                color = TrustLens.colors.textMuted,
            )
        }
    }
}

/**
 * The dominant action.
 *
 * A pasted message is the commonest thing anyone wants checked, and it is the surface with
 * the most device validation behind it, so it gets the weight. The field shown here is a
 * preview of the real one rather than a second copy of it — Check Message owns that state,
 * and duplicating it here would mean two boxes that can disagree about what is being analysed.
 */
@Composable
private fun HeroCard(onClick: () -> Unit) {
    TrustLensCard(
        modifier = Modifier.fillMaxWidth(),
        background = TrustLens.colors.accent,
        border = TrustLens.colors.accent,
        shape = Corner.hero,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.xl),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrustLensGlyph(
                glyph = Glyph.MESSAGE,
                size = 22.dp,
                tint = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                text = "CHECK SOMETHING",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }

        Text(
            text = "Paste a suspicious message or link",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = "Analysed on this device. Your message never leaves it — only a " +
                "link's hostname is ever checked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.sm)
                .clip(Corner.inner)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f))
                .padding(horizontal = Space.lg, vertical = Space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tap to paste and analyse",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
            Text(
                text = "Analyse",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** One square of the bento grid: glyph, name, and what it is for in plain words. */
@Composable
private fun RowScope.BentoTile(
    glyph: Glyph,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    TrustLensCard(
        modifier = Modifier.weight(1f).heightIn(min = 148.dp),
        onClick = onClick,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(TrustLens.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            TrustLensGlyph(glyph = glyph, size = 20.dp)
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = TrustLens.colors.text)
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )
    }
}

/** The half-width pair along the bottom of the grid. Same card, shorter. */
@Composable
private fun RowScope.WideTile(
    glyph: Glyph,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    TrustLensCard(
        modifier = Modifier.weight(1f),
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.lg),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrustLensGlyph(glyph = glyph, size = 18.dp)
            Text(title, style = MaterialTheme.typography.titleSmall, color = TrustLens.colors.text)
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )
    }
}

/**
 * What every surface above actually does with what it is given.
 *
 * The product's whole claim in one line: most scam detectors stop at detection, and TrustLens
 * investigates before the user acts. It sits on Home so the sequence is familiar by the time
 * the result screen renders it as an actual trail.
 */
@Composable
private fun PipelineStrip(modifier: Modifier = Modifier) {
    val stages = listOf("Input", "Investigation", "Evidence", "Risk", "Safe action")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Decorative: the sentence below states the same thing in prose.
                .clearAndSetSemantics { },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            stages.forEachIndexed { index, stage ->
                Text(
                    text = stage,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (index == stages.lastIndex) {
                        TrustLens.colors.accent
                    } else {
                        TrustLens.colors.textMuted
                    },
                )
                if (index != stages.lastIndex) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(TrustLens.colors.border),
                    )
                }
            }
        }
        Text(
            text = "Every surface runs the same pipeline: input, investigation, evidence, " +
                "risk, safe action. TrustLens explains why, and never acts for you.",
            style = MaterialTheme.typography.bodySmall,
            color = TrustLens.colors.textMuted,
        )
    }
}

@Preview
@Composable
private fun HomeScreenPreview() {
    TrustLensTheme { HomeScreen() }
}
