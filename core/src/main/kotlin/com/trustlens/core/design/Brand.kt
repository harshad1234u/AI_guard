package com.trustlens.core.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The TrustLens mark: a shield with a lens at its centre.
 *
 * Trust + Lens, and the instruction the product gives: look through TrustLens before trusting
 * something. Drawn rather than bundled as a drawable so one definition serves the splash, the
 * home header and any toolbar at any size, and so it inherits the current theme's ink instead
 * of needing separate light and dark assets.
 */
@Composable
fun TrustLensMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tint: Color = TrustLens.colors.accent,
) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.085f

        // Shield: flat shoulders tapering to a rounded point.
        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.08f)
            lineTo(w * 0.86f, h * 0.22f)
            lineTo(w * 0.86f, h * 0.52f)
            cubicTo(w * 0.86f, h * 0.76f, w * 0.70f, h * 0.88f, w * 0.50f, h * 0.95f)
            cubicTo(w * 0.30f, h * 0.88f, w * 0.14f, h * 0.76f, w * 0.14f, h * 0.52f)
            lineTo(w * 0.14f, h * 0.22f)
            close()
        }
        drawPath(
            path = shield,
            color = tint,
            style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round),
        )

        // The lens: a ring set slightly high, with a short handle. An aperture being looked
        // through, not a magnifier hovering over the shield.
        val r = w * 0.155f
        val cx = w * 0.46f
        val cy = h * 0.45f
        drawCircle(color = tint, radius = r, center = Offset(cx, cy), style = Stroke(stroke))
        drawLine(
            color = tint,
            start = Offset(cx + r * 0.72f, cy + r * 0.72f),
            end = Offset(cx + r * 1.55f, cy + r * 1.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The mark stacked over the wordmark and tagline. Used by the splash and the home header. */
@Composable
fun TrustLensLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 64.dp,
    showTagline: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        TrustLensMark(size = markSize)
        Text(
            text = "TRUSTLENS",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp),
            color = TrustLens.colors.text,
        )
        if (showTagline) {
            Text(
                text = "See the risk before you click.",
                style = MaterialTheme.typography.bodyMedium,
                color = TrustLens.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The line-art glyph set.
 *
 * Nine shapes at one stroke weight, drawn here rather than pulled from an icon library:
 * Material 3 1.4 no longer brings `material-icons` onto the classpath, and adding it for nine
 * shapes would cost a dependency and several thousand unused vectors.
 *
 * Every glyph is decorative. The label beside it always carries the meaning, so they are
 * excluded from the accessibility tree rather than given invented descriptions.
 */
enum class Glyph { MESSAGE, LINK, CAMERA, QR, EMAIL, VOICE, LIVE, HOME, SHIELD }

@Composable
fun TrustLensGlyph(
    glyph: Glyph,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = TrustLens.colors.accent,
) {
    Canvas(modifier.size(size).clearAndSetSemantics { }) {
        val s = this.size.minDimension
        val w = s * 0.09f
        val st = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(
            tint,
            Offset(s * x1, s * y1),
            Offset(s * x2, s * y2),
            w,
            StrokeCap.Round,
        )

        fun box(x: Float, y: Float, bw: Float, bh: Float, corner: Float = 0.06f) = drawRoundRect(
            color = tint,
            topLeft = Offset(s * x, s * y),
            size = Size(s * bw, s * bh),
            cornerRadius = CornerRadius(s * corner),
            style = st,
        )

        fun arc(cx: Float, cy: Float, r: Float, start: Float, sweep: Float) = drawArc(
            color = tint,
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(s * (cx - r), s * (cy - r)),
            size = Size(s * r * 2f, s * r * 2f),
            style = st,
        )

        when (glyph) {
            Glyph.MESSAGE -> {
                box(0.10f, 0.18f, 0.80f, 0.52f, 0.14f)
                line(0.26f, 0.70f, 0.34f, 0.90f)
                line(0.34f, 0.90f, 0.46f, 0.70f)
                line(0.26f, 0.36f, 0.64f, 0.36f)
                line(0.26f, 0.52f, 0.52f, 0.52f)
            }

            Glyph.LINK -> {
                // Two chain links held apart: inspected, never followed.
                arc(0.28f, 0.51f, 0.21f, 40f, 260f)
                arc(0.72f, 0.51f, 0.21f, 220f, 260f)
                line(0.40f, 0.51f, 0.60f, 0.51f)
            }

            Glyph.CAMERA -> {
                box(0.08f, 0.28f, 0.84f, 0.54f, 0.14f)
                line(0.34f, 0.28f, 0.40f, 0.14f)
                line(0.40f, 0.14f, 0.60f, 0.14f)
                line(0.60f, 0.14f, 0.66f, 0.28f)
                drawCircle(tint, s * 0.15f, Offset(s * 0.50f, s * 0.56f), style = st)
            }

            Glyph.QR -> {
                box(0.10f, 0.10f, 0.30f, 0.30f, 0.05f)
                box(0.60f, 0.10f, 0.30f, 0.30f, 0.05f)
                box(0.10f, 0.60f, 0.30f, 0.30f, 0.05f)
                line(0.60f, 0.62f, 0.60f, 0.78f)
                line(0.76f, 0.60f, 0.90f, 0.60f)
                line(0.90f, 0.74f, 0.90f, 0.90f)
                line(0.62f, 0.90f, 0.76f, 0.90f)
            }

            Glyph.EMAIL -> {
                box(0.08f, 0.24f, 0.84f, 0.52f, 0.10f)
                line(0.10f, 0.28f, 0.50f, 0.56f)
                line(0.50f, 0.56f, 0.90f, 0.28f)
            }

            Glyph.VOICE -> {
                box(0.37f, 0.10f, 0.26f, 0.44f, 0.13f)
                arc(0.50f, 0.50f, 0.28f, 0f, 180f)
                line(0.50f, 0.78f, 0.50f, 0.92f)
            }

            Glyph.LIVE -> {
                // Arcs radiating from a point: audio arriving, live.
                drawCircle(tint, s * 0.075f, Offset(s * 0.22f, s * 0.50f))
                listOf(0.24f, 0.44f, 0.64f).forEach { r -> arc(0.22f, 0.50f, r, -55f, 110f) }
            }

            Glyph.HOME -> {
                line(0.12f, 0.48f, 0.50f, 0.14f)
                line(0.50f, 0.14f, 0.88f, 0.48f)
                line(0.22f, 0.42f, 0.22f, 0.88f)
                line(0.78f, 0.42f, 0.78f, 0.88f)
                line(0.22f, 0.88f, 0.78f, 0.88f)
            }

            Glyph.SHIELD -> {
                val p = Path().apply {
                    moveTo(s * 0.50f, s * 0.10f)
                    lineTo(s * 0.84f, s * 0.24f)
                    lineTo(s * 0.84f, s * 0.52f)
                    cubicTo(s * 0.84f, s * 0.74f, s * 0.68f, s * 0.84f, s * 0.50f, s * 0.90f)
                    cubicTo(s * 0.32f, s * 0.84f, s * 0.16f, s * 0.74f, s * 0.16f, s * 0.52f)
                    lineTo(s * 0.16f, s * 0.24f)
                    close()
                }
                drawPath(p, tint, style = st)
            }
        }
    }
}
