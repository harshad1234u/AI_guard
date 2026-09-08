package com.trustlens.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trustlens.domain.RiskLevel

/**
 * The TrustLens design tokens.
 *
 * One source for every colour, gap and corner in the app. Five screens previously carried
 * five private copies of the same notice card and two private copies of the risk palette,
 * which meant a Critical verdict could drift apart between surfaces. That is a trust problem
 * rather than a cosmetic one: a user who learns what CRITICAL looks like on a message must
 * see the identical thing on a QR code.
 *
 * Nothing here scores, weighs or interprets anything. It reads [RiskLevel] to resolve a
 * band's colour and does nothing else with :domain.
 */

/** The spacing scale. Every gap in TrustLens is one of these. */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal page gutter, phone-first. */
    val gutter = 20.dp
}

/** Corner radii. Rounded, but never pill-shaped except for the one indicator that earns it. */
object Corner {
    val card = RoundedCornerShape(20.dp)
    val inner = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)
    val hero = RoundedCornerShape(24.dp)
}

/**
 * Colours that Material's [androidx.compose.material3.ColorScheme] has no slot for.
 *
 * Risk bands live here rather than in the Material scheme because they are not theme
 * decoration — they are the product's vocabulary, and they are resolved from [RiskLevel] so
 * the same band cannot be drawn two different ways.
 */
@Immutable
data class TrustLensColors(
    val screen: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val accentSoft: Color,
    /**
     * A step that completed. Deliberately *not* named "safe": it marks that a check ran,
     * never that the content is trustworthy (Principle 2).
     */
    val checked: Color,
    val caution: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** Absence of information — the colour of "unable to confirm". Never green. */
    val unknown: Color,
    val riskLow: Color,
    val riskGuarded: Color,
    val riskMedium: Color,
    val riskHigh: Color,
    val riskCritical: Color,
) {
    fun forRisk(level: RiskLevel): Color = when (level) {
        RiskLevel.LOW -> riskLow
        RiskLevel.GUARDED -> riskGuarded
        RiskLevel.MEDIUM -> riskMedium
        RiskLevel.HIGH -> riskHigh
        RiskLevel.CRITICAL -> riskCritical
    }
}

/**
 * Calm, high-contrast, and deliberately not neon.
 *
 * LOW is a muted green, never a bright one. A low score means "little evidence of a scam",
 * not "verified safe", and a triumphant green would say the second thing.
 */
internal val LightTrustLensColors = TrustLensColors(
    screen = Color(0xFFF3F5F9),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFE3E8F0),
    borderStrong = Color(0xFFCBD4E1),
    text = Color(0xFF0E1726),
    textMuted = Color(0xFF5A6B80),
    accent = Color(0xFF14477D),
    accentSoft = Color(0xFFE8EFF9),
    checked = Color(0xFF2F6B4F),
    caution = Color(0xFF9A5B00),
    danger = Color(0xFFA8231C),
    dangerSoft = Color(0xFFFDECEA),
    unknown = Color(0xFF5A6B80),
    riskLow = Color(0xFF1B7F3B),
    riskGuarded = Color(0xFF6E7B12),
    riskMedium = Color(0xFFB26A00),
    riskHigh = Color(0xFFC2410C),
    riskCritical = Color(0xFFB3261E),
)

internal val DarkTrustLensColors = TrustLensColors(
    screen = Color(0xFF0A0D12),
    surface = Color(0xFF141922),
    surfaceRaised = Color(0xFF1B2230),
    border = Color(0xFF232C3B),
    borderStrong = Color(0xFF354154),
    text = Color(0xFFE9EEF6),
    textMuted = Color(0xFF98A6BA),
    accent = Color(0xFF9AC0F5),
    accentSoft = Color(0xFF17243A),
    checked = Color(0xFF6FC28E),
    caution = Color(0xFFE0A44A),
    danger = Color(0xFFFF8A80),
    dangerSoft = Color(0xFF2A1614),
    unknown = Color(0xFF98A6BA),
    riskLow = Color(0xFF5BC47F),
    riskGuarded = Color(0xFFB9C64A),
    riskMedium = Color(0xFFE7A83E),
    riskHigh = Color(0xFFF97A4A),
    riskCritical = Color(0xFFFF6B63),
)

val LocalTrustLensColors = staticCompositionLocalOf { LightTrustLensColors }
