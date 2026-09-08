# TrustLens — Design System

**Branch:** `phase16-liveguard-email` · **Base:** `abccb49` (`v4.0-freeze`)
**Scope:** UI layer only. No RiskEngine, RiskWeights, SafeAction, threat-intelligence, ML,
investigation or scoring change of any kind — see §14.

> **See the risk before you click.**

---

## 1. Design philosophy

TrustLens is a **premium mobile security product**, not a gaming dashboard, a hacker terminal,
a crypto tracker or a generic AI chat surface. The visual language is calm because the product
is calm: it never panics, never flashes, and never decorates a claim it cannot support.

Three principles drive every decision below, and all three are inherited from the product's
security invariants rather than from taste:

1. **Consistency is a trust property.** A user who learns what CRITICAL looks like on a pasted
   message must see the identical thing on a QR code. Before this phase, five screens carried
   five private copies of the same disclosure card and two private copies of the risk palette;
   that drift is a correctness problem, not an untidy one. Everything shared now lives in
   `:core`.
2. **Never colour alone.** Every risk band travels with its number, its band name and a
   position on a scale. Every investigation step states its outcome in words next to its
   marker. Every reputation verdict is spelled out.
3. **Absence of evidence is never drawn as safety.** There is no green tick anywhere in the
   external-verification section, no "SAFE" string in the app, and "unable to confirm" has its
   own component so it cannot be mistaken for a clean result.

### Primary language: minimalism

Flat surfaces, hairline borders instead of drop shadows, one filled button per screen, one
accent colour, and no gradient, glow, blur, neon or 3D anywhere. Depth is drawn with a 1dp
border and a background step, not with elevation — a stack of shadowed cards turns a column of
evidence into visual noise.

### Secondary system: selective Bento

Bento is used where it earns its place — the Home grid, and the two-column live verdict on
Live Guard — and nowhere else. Result, Text, URL, Email, Voice and both camera screens are
single-column, because a verdict read under pressure should not require the eye to choose a
path through it.

---

## 2. The #09 navigation reference

**#09 "Layered Card Navigation"** was the navigation reference, and #07 "Cyber Neon Dock" was
explicitly rejected.

What was taken from #09:

| Taken | How it appears in TrustLens |
|---|---|
| The bar as a **floating card**, inset from the screen edge, not a strip welded to the bottom | `TrustLensBottomBar` — inset 12dp, `Corner.hero` radius, 1dp border, surface fill |
| **Layered hierarchy** — screens stack over the navigation rather than replacing it | Four top-level destinations keep the bar; Link, Email, Voice and Live Guard are *layers* that cover it and are dismissed with Back |
| **Card hierarchy** on the landing surface | One dominant hero, then a grid of equal tiles, then a pair of half-width tiles |
| **Compact** navigation | Four items, glyph + label, 56dp targets |
| **Clean separation** between layers | The screen background is one step darker than card surfaces, so every card reads as a distinct layer |

What was deliberately *not* taken: #09's literal colours, its icon set, its animation
character, and any of its ornament. The reference informed the *structure*; the surface is
TrustLens's own.

**Why #09 rather than #07.** A scam-detection app's credibility is the product. Neon and glow
read as entertainment, and a user deciding whether a bank message is real should not be looking
at an interface that signals "game". #09's restraint fits a security product; #07's does not.

---

## 3. Tokens — `core/src/main/kotlin/com/trustlens/core/design/Tokens.kt`

### Colour

Material's `ColorScheme` covers primary/surface/error. Everything the product needs beyond
that lives in `TrustLensColors`, provided through `LocalTrustLensColors` and read as
`TrustLens.colors`.

| Token | Light | Dark | Used for |
|---|---|---|---|
| `screen` | `#F3F5F9` | `#0A0D12` | Page background, one step below cards |
| `surface` | `#FFFFFF` | `#141922` | Card fill |
| `surfaceRaised` | `#FFFFFF` | `#1B2230` | Nested surfaces in dark |
| `border` | `#E3E8F0` | `#232C3B` | The 1dp hairline that replaces elevation |
| `borderStrong` | `#CBD4E1` | `#354154` | Outlines, unknown-state border |
| `text` | `#0E1726` | `#E9EEF6` | Body ink |
| `textMuted` | `#5A6B80` | `#98A6BA` | Captions, section labels, secondary lines |
| `accent` | `#14477D` | `#9AC0F5` | The one brand colour: hero, primary button, mark |
| `accentSoft` | `#E8EFF9` | `#17243A` | Notice cards, glyph pads, nav selection |
| `checked` | `#2F6B4F` | `#6FC28E` | An investigation step that **ran** — never "safe" |
| `caution` | `#9A5B00` | `#E0A44A` | Degraded, unavailable, suspicious |
| `danger` | `#A8231C` | `#FF8A80` | Alerts, confirmed-malicious |
| `unknown` | `#5A6B80` | `#98A6BA` | Absence of information. **Never green.** |

The `checked` / "safe" distinction is deliberate and load-bearing: `checked` marks that a stage
of the investigation executed, and is never used to describe content.

### Risk bands (unchanged values, now defined once)

| Band | Range | Light | Dark |
|---|---|---|---|
| LOW | 0–19 | `#1B7F3B` | `#5BC47F` |
| GUARDED | 20–39 | `#6E7B12` | `#B9C64A` |
| MEDIUM | 40–60 | `#B26A00` | `#E7A83E` |
| HIGH | 61–80 | `#C2410C` | `#F97A4A` |
| CRITICAL | 81–100 | `#B3261E` | `#FF6B63` |

Ranges are read from `RiskLevel.range` at render time, so the segmented scale cannot drift from
the model. LOW is a muted green rather than a bright one — a low score means "little evidence
of a scam", not "verified safe", and a triumphant green would say the second thing.

### Spacing — `Space`

`xs 4 · sm 8 · md 12 · lg 16 · xl 24 · xxl 32`, plus `gutter 20` for the horizontal page
margin. Every gap in the app is one of these.

### Shape — `Corner`

`chip 10 · inner 14 · card 20 · hero 24`. Rounded, modern, and deliberately **not** pill-shaped
— the only fully-rounded elements are the nav selection indicator and the step markers, where
a circle carries meaning.

### Type — `Theme.kt`

The platform font, on purpose: no bundled display face lacks Devanagari coverage and silently
breaks Hindi evidence quoting.

| Style | Size / weight | Used for |
|---|---|---|
| `displayLarge` | 64 Bold, -2 tracking | The risk score. Nothing else. |
| `displayMedium` | 40 Bold | The Live Guard current score |
| `headlineMedium` | 26 Bold | Hero headline |
| `headlineSmall` | 22 Bold | Screen titles |
| `titleLarge/Medium/Small` | 19/16/14 SemiBold | Card titles, primary safe action, buttons |
| `bodyLarge/Medium/Small` | 16/14/13 | Input text, body, captions |
| `labelLarge` | 12 Bold, **+1.4 tracking** | Section headers, small caps. The one flourish. |
| `labelMedium/Small` | 12/11 Medium | Chips, nav labels, state words |

---

## 4. Components — one definition each

| Component | Replaces | Notes |
|---|---|---|
| `TrustLensCard` | 5 ad-hoc `Card` usages | Border-not-shadow; optional `onClick` |
| `NoticeCard` | **5 private copies of `Notice`** | Disclosures and caveats. Accent bar + soft fill |
| `AlertCard` | 2 private copies of `Alert` | A condition that changes what the screen means |
| `SectionHeader` | Ad-hoc bold text + divider | Small caps + rule to the edge |
| `PrimaryButton` / `SecondaryButton` | Raw `Button` / `OutlinedButton` | 52dp min height |
| `LoadingState` | 4 bare `CircularProgressIndicator`s | Spinner + label, **never a percentage** |
| `EmptyState` | 3 private guidance blocks | "Nothing was read", with hints |
| `ErrorState` | Inline error text | Wraps `AlertCard` |
| `UnknownState` | *new* | "Unable to confirm". Its own component so it cannot drift toward reading as safe |
| `QuotedContent` | `TranscriptCard`, `RecognisedTextCard` | Recessed surface = quoted, not offered |
| `FieldRow`, `BulletRow`, `StatusChip` | Per-screen copies | — |
| `ScreenHeader` | Ad-hoc `TextButton("Back")` rows | Back (48dp), title, subtitle, trailing slot |
| `TrustLensBottomBar` | *new* | §2 |
| `TrustLensMark`, `TrustLensLockup`, `TrustLensGlyph` | *new* | §5 |

### Risk components

- **`RiskScoreCard`** — band name, then the number at 64sp, then `RiskScale`, then
  `RiskLevel.userAction`, then confidence. Carries one merged `contentDescription` so a screen
  reader gets the whole verdict in one utterance rather than six fragments.
- **`RiskScale`** — five segments sized by the real band ranges, filled up to the current band,
  with a marker placed at `score/100`. This is the non-colour channel.
- **`RiskBadge`** — compact band marker for the Live Guard peak.
- **`ReasonRow`** — splits the engine's trailing `(+N)` into a points column. **The number and
  the wording both come from the engine's own string**; a reason without the pattern renders
  whole rather than being guessed at.
- **`SafeActionCard`** — the recommended actions, first one emphasised. **It has no `onClick`
  and cannot have one.** Principle 3 as UI: nothing on the card is tappable, so nothing on it
  can act for the user.

### Investigation components

`investigationStepsFor(result)` derives six stages from real fields on `AnalysisResult`, and
`InvestigationTrail` draws them as one connected rail:

| Stage | Source | `UNAVAILABLE` when |
|---|---|---|
| Content read | `context.source`, `rawContent.length` | never |
| Indicators extracted | `context.indicators` | never — an empty result is worded as *absence of these signals, not a verdict of safety* |
| Link structure examined | `indicators.urls` | `NOT_APPLICABLE` when there are no links |
| On-device model | `mlSignal`, `classifierUnavailableReason`, `usedMockClassifier` | model failed or a mock ran; `NOT_APPLICABLE` for flows with no wording to read |
| Threat intelligence | `externalEvidence`, `risk.degraded` | nothing consulted, or the picture is incomplete |
| Risk calculated | `risk.reasons.size`, `score`, `level` | never |

State words are `checked` / `not applicable` / `unable to confirm`. There is no "passed", no
"clear" and no tick outside the trail.

---

## 5. Brand and iconography

**TrustLens = Trust + Lens.** A shield with a lens ring at its centre: *look through TrustLens
before trusting something.*

The mark is drawn from one geometry in three places — `TrustLensMark` (Compose, any size,
inherits theme ink), `ic_trustlens_mark.xml` (the startup screen), and
`ic_launcher_foreground.xml` (the adaptive icon, inset to the 66dp safe zone, with a
`monochrome` layer for themed icons).

Glyphs are drawn as Canvas line art in `Brand.kt` — nine shapes at one stroke weight. Material 3
1.4 no longer puts `material-icons` on the classpath, and adding it for nine shapes would cost a
dependency and several thousand unused vectors. Every glyph is decorative and excluded from the
accessibility tree; the label beside it always carries the meaning.

---

## 6. Splash

`Theme.TrustLens.Splash` is the launcher activity's theme. On API 26–30 it is a `layer-list`
window background (brand navy + centred mark); on API 31+ it is the platform splash screen via
`windowSplashScreenBackground` / `windowSplashScreenAnimatedIcon`. `MainActivity.onCreate` calls
`setTheme(R.style.Theme_TrustLens)` to leave it behind.

**There is no timer and no progress indicator, and the tagline is not shown.** This is the
honest reading of "do not fake a 2–3 second loading screen": TrustLens has nothing asynchronous
to wait on at launch — Hilt builds its graph lazily and the ONNX session loads on first use, not
at startup — so there is no work to show progress against. A Compose splash carrying the
wordmark and a `• • •` indicator could only flash for one frame or hold behind a fake delay. The
window background appears instantly and disappears exactly when initialisation completes,
because it *is* the window until then. The full lockup (mark + `TRUSTLENS` + tagline) exists as
`TrustLensLockup` and is used in the Home header.

---

## 7. Home

```
[mark] TrustLens                      ← lockup, tagline beneath
       See the risk before you click.

┌─────────────────────────────────────┐
│ ▣ CHECK SOMETHING                   │   ← hero, accent-filled, dominant
│ Paste a suspicious message or link  │
│ Analysed on this device.            │
│ ┌─────────────────────────────────┐ │
│ │ Tap to paste and analyse  Analyse│ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘

┌──────────────┐ ┌──────────────┐
│ ◌ Link       │ │ ▢ Camera     │        ← bento, 2×2
└──────────────┘ └──────────────┘
┌──────────────┐ ┌──────────────┐
│ ⊞ QR         │ │ ◈ Voice      │
└──────────────┘ └──────────────┘
┌──────────────┐ ┌──────────────┐
│ ✉ Email      │ │ ((• Live Gd  │        ← half-width pair
└──────────────┘ └──────────────┘

Input ── Investigation ── Evidence ── Risk ── Safe action
```

Seven surfaces, no duplication: the hero *is* Check Message. The pipeline strip at the foot
states the architecture in one line, so the sequence is familiar by the time the Result screen
renders it as an actual trail.

**The hero does not contain a live text field.** Check Message owns that state; a second box
here could disagree with it about what is being analysed, and would need Hilt in
`:feature:home` to avoid it. One tap either way.

---

## 8. Result — the highest-priority screen

```
RISK  →  SAFE ACTION  →  WHY  →  INVESTIGATION  →  EVIDENCE  →  EXTERNAL  →  DISCLOSURES
```

The verdict leads because someone reading it may be mid-scam and has seconds; the safe action
sits directly beneath it because that is the point of the screen. Everything after is the audit
trail, in the order the pipeline produced it.

| Section | Fed by |
|---|---|
| `RiskScoreCard` | `risk.score`, `.level`, `.confidence`, `.level.userAction` |
| `SafeActionCard` | `risk.recommendedActions` |
| Why this score | `risk.reasons` — verbatim, split only for layout |
| Investigation | `investigationStepsFor(result)` |
| Evidence | `context.indicators` + `OTP_REFERENCE` matches, quoted inert |
| External verification | `risk.externalEvidence`, verdict spelled out in words |
| What this suggests | `risk.inferences` |
| How this was produced | mock-classifier, degraded, model-attribution notices |

**What changed structurally:** the four caveat cards used to sit *above* the score, so the
model's training-data note was the first thing between the user and the verdict. They now
collapse into one "How this was produced" disclosure at the foot — expanded on arrival whenever
a caveat changes what the verdict *means* (mock classifier, degraded threat intel, model did not
run), collapsed when it is only routine model attribution. Nothing was removed; every string is
still present.

**Not invented:** every heading is filled from a real field. No score, reason, weight or action
is computed, rounded, re-banded or hard-coded anywhere in the presentation layer.

---

## 9. Investigation UI

The differentiator, and the reason the app is not just a classifier with a label. A connected
rail of six stages, each with a marker, a state word and a sentence describing what actually
happened — `2 links taken apart offline — host, domain, port and path. Nothing was opened,
resolved or visited.`

A stage that could not run **breaks the rail's colour rather than disappearing from it**. The
threat-intelligence step names the providers asked and the hostnames sent, and states that
hostnames only ever leave the device.

---

## 10. Per-screen notes

### Text / Message
Single column, analyser not chatbot: one input, one verdict, and the verdict stays put. Arriving
from `ACTION_PROCESS_TEXT` shows a `Selected text` chip in the header, the subtitle *"TrustLens
analysed the text you highlighted, on this device"*, and the full no-mailbox-access notice
before the verdict. The truncation notice is unchanged and still states the original length.

### URL
Subtitle is the promise: **"Link analysed without opening it."** Structure is its own section —
link, host, domain, scheme, port, subdomains, then structural findings — rendered before the
verdict. **There is no Open affordance anywhere on the screen**, and unparseable input gets
`UnknownState`, not an error tone: nothing was analysed, so nothing is claimed either way.

### Email
Laid out as the three parts of a mail — `FROM`, `SUBJECT`, `BODY / SELECTED CONTENT` — inside
one card, because those are the three things a user reads when judging a mail, and exactly one
of them (the sender's domain) is the only thing on the screen that leaves the device. Header
subtitle *"Analysing selected content only"* with a `No mailbox access` chip. No OAuth, no
permissions, no inbox scanning — none was added.

### Camera / OCR
Instruction *"Point your camera at suspicious text."* The framing guide is a **wide,
text-shaped rectangle** (92% × 46%), deliberately unlike the QR reticle. No gallery affordance
was invented.

### QR
A **square reticle** (68% width, 1:1). When the engine returns `DO_NOT_PAY`, a **DO NOT PAY**
alert is hoisted above the payload and the verdict — the action still comes from the engine;
only where it is drawn changed. No Open, no Pay, no Call anywhere on the screen; the payload is
inert `FieldRow`s, and a claimed payee name is labelled `(unverified)`.

### Live Guard
Header carries a **listening pulse** that animates only while audio is arriving and goes still
on `NO_AUDIO` — a pulse that kept animating through silence would say the microphone is working
when it is not. All ten states are covered: idle, permission-denied, listening, partial,
completed, analysing, risk-detected, no-audio, error, stopped.

- Interim transcript: italic, quoted, muted, **on no card** — visually unlike the finished
  `QuotedContent` transcript, because it has not been scored.
- Live verdict is a two-column bento: `CURRENT RISK` at 40sp on the left, `HIGHEST SO FAR` as a
  `RiskBadge` on the right.
- No-audio uses `AlertCard`: *"Nothing is being checked until audio arrives."* Silence never
  renders as a clean result.
- Detected language is shown as *"Recogniser reports: …"* — a report, not a claim of
  comprehension. A missing Hindi model is stated as **"Hindi speech model is not installed"**.

### Voice Check
Same treatment; `Unavailable` uses `UnknownState`, `Failed` uses `ErrorState` — the two are
different statements and now look different.

### `ACTION_PROCESS_TEXT`
`ProcessTextActivity` hosts the same Check Message screen with **no intermediate screen** —
selection arrives, analysis starts, verdict renders. The framing is generic ("selected text",
"that app"), never email-specific, because the source may be Chrome, WhatsApp or anything else
offering text processing.

---

## 11. Navigation

```
Bottom bar (layer 0):   Home · Message · Scan · QR
Layers over it:         Link · Email · Voice · Live Guard   (Back returns to Home)
```

Still a plain state switch in `MainActivity` — no navigation library was added; with eight
destinations it would still cost more than it gives. `Screen` gained a label and a glyph so the
bar is driven by the same enum that routes.

The bar sits *below* the content in a `Column` rather than floating over it, so camera previews
are never occluded. When it is present it consumes the navigation-bar inset (screens
`consumeWindowInsets`), so there is no double gap.

---

## 12. Motion

Four animations, all of them explanatory:

| Where | What | Duration |
|---|---|---|
| `RiskScale` | Marker slides to the score | 450ms |
| Bottom bar | Tint + indicator width on selection | 180ms |
| Disclosures | Fade + expand | default |
| Live Guard | Listening pulse (only while audio arrives) | 900ms, reversing |

No bouncing, no glow, no scanning sweeps, no decorative motion.

---

## 13. Accessibility

- **Risk is never colour alone** — number + band name + segmented scale + action text.
- `RiskScoreCard` merges its children into one `contentDescription` so the verdict is read as a
  sentence, not six fragments.
- Investigation steps carry `"<title>: <state word>. <detail>"`.
- Decorative glyphs, markers and rails use `clearAndSetSemantics { }` rather than invented
  descriptions.
- Touch targets: nav items 56dp, buttons 52dp, Back 48dp, disclosure toggle 48dp.
- `SectionHeader` and screen titles are marked `heading()`.
- Long hostnames and payloads **wrap** rather than truncate — a link the user cannot fully read
  is a link they cannot check. No `maxLines` on any evidence, payload or URL field.
- No fixed pixel positioning; the score marker is placed proportionally via a `layout` modifier.
- Devanagari renders on the platform font, so Hindi evidence quoting is unaffected by the type
  scale.

---

## 14. Security UI principles — what did not change

| Invariant | Status |
|---|---|
| RiskEngine, RiskWeights | **Zero changed files** |
| `SafeAction` | Still exactly 7 members |
| Scoring, extraction, investigation, ML, threat intel | Untouched |
| ViewModels | **Zero behavioural changes** — the only edits in this phase are to Compose files, `MainActivity`, build files and resources |
| `startActivity` / `ACTION_VIEW` / `WebView` / `Runtime.exec` | Still zero occurrences |
| UNKNOWN ≠ SAFE | Reinforced: no `SAFE` string, no green tick in external verification, dedicated `UnknownState` |
| Gmail OAuth, mailbox permissions, `CallScreeningService` | Not added |

Three UI rules follow directly from the invariants:

1. **`SafeActionCard` has no click handler and never will.** The absence is the enforcement.
2. **Nothing quoted from untrusted content is ever interactive.** URLs, payloads, transcripts
   and OCR output are inert text on recessed surfaces.
3. **A missing check is drawn, not hidden.** Every stage that could not run appears in the
   investigation trail marked `unable to confirm`.

---

## 15. Known gaps

- The splash shows the mark but not the wordmark or tagline — §6.
- The Home hero is a tap target rather than a live input field — §7.
- No Compose UI tests exist in the repo; this phase did not add any. The redesign is verified by
  compilation, the unchanged JVM suite, and a release build.
- The four platform colours in `res/values/colors.xml` are kept in step with `TrustLensColors`
  by hand — a resource cannot read a Compose token.
