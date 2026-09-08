package com.trustlens.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The TrustLens theme.
 *
 * A restrained, premium security palette: deep navy accent, near-neutral surfaces, and no
 * gradients, glow or neon anywhere. Risk colours are not in the Material scheme — they belong
 * to the band itself and are resolved from `RiskLevel` through [TrustLensColors], so a
 * CRITICAL verdict looks identical on every surface that can produce one.
 */
@Composable
fun TrustLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) DarkTrustLensColors else LightTrustLensColors

    CompositionLocalProvider(LocalTrustLensColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(extended) else lightScheme(extended),
            typography = TrustLensTypography,
            shapes = TrustLensShapes,
            content = content,
        )
    }
}

/** The tokens Material has no slot for. Read as `TrustLens.colors` inside any composable. */
object TrustLens {
    val colors: TrustLensColors
        @Composable @ReadOnlyComposable get() = LocalTrustLensColors.current
}

private fun lightScheme(c: TrustLensColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = c.accentSoft,
    onPrimaryContainer = c.accent,
    secondary = c.textMuted,
    background = c.screen,
    onBackground = c.text,
    surface = c.surface,
    onSurface = c.text,
    surfaceVariant = c.accentSoft,
    onSurfaceVariant = c.textMuted,
    outline = c.borderStrong,
    outlineVariant = c.border,
    error = c.danger,
    errorContainer = c.dangerSoft,
    onErrorContainer = c.danger,
)

private fun darkScheme(c: TrustLensColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = androidx.compose.ui.graphics.Color(0xFF08172B),
    primaryContainer = c.accentSoft,
    onPrimaryContainer = c.accent,
    secondary = c.textMuted,
    background = c.screen,
    onBackground = c.text,
    surface = c.surface,
    onSurface = c.text,
    surfaceVariant = c.surfaceRaised,
    onSurfaceVariant = c.textMuted,
    outline = c.borderStrong,
    outlineVariant = c.border,
    error = c.danger,
    errorContainer = c.dangerSoft,
    onErrorContainer = c.danger,
)

private val TrustLensShapes = Shapes(
    extraSmall = Corner.chip,
    small = Corner.chip,
    medium = Corner.inner,
    large = Corner.card,
    extraLarge = Corner.hero,
)

/**
 * The type scale, on the platform font.
 *
 * No custom typeface is bundled: the system font renders Devanagari correctly on every
 * device TrustLens supports, and a downloaded display face that lacks those glyphs would
 * silently break Hindi evidence quoting. Tracking is tightened on the large sizes and
 * loosened on the small caps labels, which is where the "premium" reads.
 */
private val trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

internal val TrustLensTypography = Typography(
    // The risk score, and nothing else.
    displayLarge = TextStyle(
        fontSize = 64.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-2).sp,
        lineHeightStyle = trim,
    ),
    displayMedium = TextStyle(
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-1).sp,
        lineHeightStyle = trim,
    ),
    headlineMedium = TextStyle(
        fontSize = 26.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    // Section headings: small caps, wide tracking. The one "designed" flourish in the app.
    labelLarge = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
    ),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
)
