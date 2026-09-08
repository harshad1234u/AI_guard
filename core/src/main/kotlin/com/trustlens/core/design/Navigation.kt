package com.trustlens.core.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The TrustLens bottom navigation.
 *
 * Layered-card navigation: the bar is a floating card that sits *over* the content rather
 * than a full-width strip welded to the bottom edge, so a screen reads as a card resting on a
 * surface with the navigation as its own layer. The inset and the hairline border are what
 * make it read as premium; there is no glow, no gradient and no blur.
 *
 * Only top-level destinations appear here. Anything reached from Home — Check URL, Check
 * Email, Voice Check, Live Guard — is a layer *above* the bar and hides it, which is what
 * keeps six surfaces from feeling like six separate applications.
 */
@Composable
fun <T> TrustLensBottomBar(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    glyph: (T) -> Glyph,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md, vertical = Space.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Corner.hero)
                .background(TrustLens.colors.surface)
                .border(1.dp, TrustLens.colors.border, Corner.hero)
                .padding(horizontal = Space.sm, vertical = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavItem(
                    label = label(item),
                    glyph = glyph(item),
                    isSelected = item == selected,
                    onClick = { onSelect(item) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    glyph: Glyph,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (isSelected) TrustLens.colors.accent else TrustLens.colors.textMuted,
        animationSpec = tween(180),
        label = "navTint",
    )
    // The selected item is marked by a filled pad plus an underline, so selection survives a
    // colour-blind reading of the bar.
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 0.dp,
        animationSpec = tween(180),
        label = "navIndicator",
    )

    Column(
        modifier = modifier
            .clip(Corner.inner)
            .background(
                if (isSelected) TrustLens.colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent,
            )
            // 56dp: comfortably above the 48dp minimum touch target.
            .heightIn(min = 56.dp)
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(vertical = Space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        TrustLensGlyph(glyph = glyph, size = 20.dp, tint = tint)
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
        Box(
            Modifier
                .width(indicatorWidth)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(tint),
        )
    }
}
