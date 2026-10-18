---
name: compose-design-review
description: Material 3 / Jetpack Compose design review and screen-building guidance for TrustLens. Use when building a new Compose screen, reviewing an existing one for visual consistency, or asked whether a screen "looks right" / "looks like TrustLens" / needs restraint or hierarchy fixes. Complements design-taste-frontend (which is web/CSS-oriented) with Android-native conventions: Material3 theming, semantic color separation, restrained security-product visual language. Not for backend logic, RiskEngine changes, or non-UI architecture — use android-development or claude-android-ninja for those.
---

# Compose Design Review — TrustLens house style

TrustLens's design language exists to serve one goal: a user under stress (a live scam call, a
suspicious email) must find the verdict instantly, trust it, and never be confused about what
is a fact versus what is a recommendation. Every rule below traces back to that.

## The palette is deliberately restrained

`app/src/main/kotlin/com/trustlens/ui/TrustLensTheme.kt` defines exactly two color roles:
`primary` (a muted blue, `#1B4D8F` light / `#9CC4FF` dark) and the M3 surface/background
defaults. **Nothing else is a brand color.** No accent palette, no gradient, no illustration
style. This is intentional for a security product: color that isn't meaning-bearing is noise
that competes with the one color palette that *does* mean something — risk level.

**Never add a new brand color, gradient, or decorative accent** without checking whether it
competes with risk-level color for attention. If a new screen wants visual interest, get it
from typography weight, spacing, and card elevation — not from color.

## Risk color is semantic and lives outside the theme

Risk-level colors (LOW/GUARDED/MEDIUM/HIGH/CRITICAL) are resolved from `RiskLevel` itself, not
from `MaterialTheme.colorScheme`. This is not a style preference — it's the answer to "does a
CRITICAL verdict look the same on every screen?" A theme color can be reinterpreted per-screen;
a risk color must not be, because the user's safety decision depends on it looking identical
everywhere. See `riskColor(result.risk.level)` in `RiskResultView.kt` for the existing mapping.
**Any new screen that shows a risk verdict must call the same function, never invent its own
mapping.**

## The Notice / Section / Card vocabulary repeats everywhere on purpose

Every screen shares three primitives, and a design review should check that a new screen uses
them rather than inventing new ones:

- **`Notice`** — a `Card` with `surfaceVariant` background, used for a disclosure or limitation
  stated *before* an action ("Live Guard listens through the microphone...", "TrustLens cannot
  read your mailbox"). Never decorative — every Notice exists to correct a specific wrong
  inference the user might otherwise make.
- **`Section(title) { ... }`** — a titled block of bullet rows (`•` for observations/evidence,
  `→` for inferences). Keeps "what TrustLens saw" visually distinct from "what TrustLens
  concluded" — this maps directly to the Observation/Inference split in the domain model, and a
  design review should never let those two visually collapse into one list.
- Bold, colored text for `SafeAction` labels (`fontWeight = FontWeight.SemiBold`, colored by
  risk level) — recommendations are the one thing on screen that should look actionable.

**When building a new screen, reuse these instead of building bespoke card layouts.** A screen
that looks different from the rest of TrustLens for no reason is a regression, not a feature —
consistency here is what lets a user trust screen #7 as much as screen #1.

## Disclosure-before-permission is a design pattern, not just a privacy rule

Every screen that requests a sensitive capability (microphone, camera) shows the limitation
*before* the permission prompt fires — see `IdleGuardPane` in `LiveGuardScreen.kt`: two
`Notice` cards stating exactly what will and won't be heard, rendered before `Start Live Guard`
is enabled. When reviewing or building a screen with a permission gate, check the disclosure
renders in the composition *before* the permission launcher, not after.

## Anti-slop checklist for a new TrustLens screen

Borrowed from `design-taste-frontend`'s principles, translated to Compose:

1. **No generic "modern UI" filler.** No unnecessary illustrations, no decorative icons that
   don't map to a real state, no marketing-style hero text. This is a utility tool used at a
   moment of anxiety — every pixel should either inform or let the user act.
2. **Real hierarchy, not just font-size hierarchy.** `headlineSmall` for the screen title,
   `bodyMedium` for context, `bodySmall` for disclosures/attribution — check a new screen
   actually uses this scale rather than three near-identical text sizes.
3. **State the limitation, don't hide it.** If a feature is degraded, unavailable, or partial
   (no network, no Hindi model installed, classifier didn't run), that must render as visible
   text, never as a silently-omitted section. Silence is the one thing this app must never do
   with a risk-relevant fact.
4. **Every screen ends the same way an M3 screen should:** a `Scaffold`, a `Back` action at the
   top, content in a scrollable `Column` with `spacedBy` vertical rhythm (12–16.dp is the
   house rhythm — check `LiveGuardScreen.kt` and `TextCheckScreen.kt` for the exact spacing
   used elsewhere before picking a new value).
5. **Dark mode is not optional.** `TrustLensTheme` derives both light and dark schemes from the
   same two roles — verify a new screen's colors come from `MaterialTheme.colorScheme` or
   `riskColor()`, never a hardcoded `Color(...)` that only looks right in one mode.

## What this skill is not for

- RiskEngine, RiskWeights, or SafeAction changes — those are backend/domain, not design. See
  `claude-android-ninja` or `android-development` for architecture work.
- Web/HTML design analysis — that's `design-taste-frontend`, `image-to-code`, or the `taste`
  skill. Material3/Compose conventions do not map onto CSS conventions; don't apply web taste
  principles (e.g. box-shadow layering, CSS grid patterns) literally to Compose layouts.
- Reviewing whether a *feature* should exist (e.g. "should Live Guard show a peak score") —
  that's a product decision documented in `docs/PHASE-16-LIVE-GUARD-AND-EMAIL.md`, not a visual
  design question.
