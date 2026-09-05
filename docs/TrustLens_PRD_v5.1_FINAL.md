# TrustLens — Product Requirements Document v5.1 (FINAL — Pre-Event + Presentation Guardrails)

> **See the risk before you click.**

**Version:** 5.1 — Final Pre-Event Plan, with presentation-language guardrails
**Date:** 2026-09-05
**Supersedes:** `TrustLens_PRD_v5.0_FINAL.md` (2026-09-05) — v5.0's plan is unchanged; this version only adds §15. v4.0 remains the frozen core baseline and the historical record of what shipped through Phase 15. Both v5.0 and v4.0 stay on file, unchanged.
**Target Event:** iQOO Hackathon 2026 — Chennai City Battle, 12–13 September 2026
**Build state:** Phases 1–15 **FROZEN** (468 tests, 0 failures) · Phase 16 extensions **IN PROGRESS**, hard cutoff **2026-09-09**
**Validation device (interim):** POCO X6 5G — a temporary stand-in for development only. No number in this document is final until re-measured on the actual iQOO 15 at the event.
**Release artefact (baseline):** `app-release.apk`, 64,155,230 bytes, versionName 0.1.0 — Phase 16 additions will increment this on merge.

---

## 0. How this document differs from v4.0

v4.0 was closed: as-built, frozen, no further scope. Since then, three previously-descoped features (§12 of v4.0) were reconsidered for pre-event inclusion — scoped narrowly enough that none of them touch the three non-negotiable security principles in §2.

**This document is the actual final plan for what goes to the event**, whether Phase 16 lands in full, in part, or not at all.

**Reading rules:**
- If a claim isn't in this document, don't make it on stage.
- If a Phase 16 feature isn't fully tested and device-validated by the cutoff, it is feature-flagged off, and the app ships exactly as v4.0 describes it for that surface. The frozen core is the guaranteed fallback, always.

---

## 1. Product statement

TrustLens is an Android, phone-first, on-device scam-analysis app. Input surfaces converge on **one** analysis pipeline and **one** deterministic risk engine.

> **Most scam detectors stop at detection. TrustLens investigates the threat before the user acts.**

It explains *why* something is risky, in arithmetic the user can audit. **It never acts on the user's behalf.**

---

## 2. Non-negotiable security principles (as enforced in code)

| # | Principle | Enforcement mechanism |
|---|---|---|
| 1 | **Untrusted content is DATA, never instructions** | No LLM in the *scoring* path. Deterministic extraction + fixed weight table. The shipped ONNX graph contains exactly one operator: `LinearClassifier`. 8 prompt-injection tests assert injected text scores as evidence and changes no action. |
| 2 | **UNKNOWN ≠ SAFE** | `ReputationVerdict` has no `SAFE` case. `NO_DETECTION`, `UNKNOWN`, `ERROR` contribute +0, never a reduction. |
| 3 | **Agent ≠ action executor** | `SafeAction` has 7 members; every one declines or defers. Enforced by absence — zero `startActivity` / `ACTION_VIEW` / `WebView` / `Runtime.exec` outside the explicit, user-initiated share/report actions added in Phase 16 (§12.3). |

**Phase 16 addition to Principle 1:** the new report-generation LLM (§12.1) sits strictly *after* the RiskEngine finalizes a verdict. It receives the completed Evidence/Verdict object, never raw pipeline internals, and its output is display-only narrative text. It cannot change a score, a band, or a safe action — those remain 100% deterministic, unchanged from v4.0.

**Corollaries, unchanged from v4.0:**
- URL analysis never opens, resolves, expands, redirects to, or fetches a URL.
- Camera never persists or uploads an image, never auto-opens a URL, never launches a payment app.
- Voice never records calls, intercepts calls, or retains raw audio.
- Threat intel is transmitted hostname-only.

---

## 3. Architecture

```
:app            Compose UI host, Hilt wiring, MainActivity, integration tests
:feature:*      home · textcheck · urlcheck · lensscan · qrscan · voicecheck ·
                emailshare (NEW, Phase 16) · phonecheck (NEW, Phase 16, optional) · result
:domain         pure Kotlin/JVM — models + use cases + contracts. Depends on nothing.
:investigation  pure JVM — deterministic extractors (indicators, URL, QR, phone-pattern [NEW])
:riskengine     pure JVM — RiskEngine + RiskWeights + RiskWeightsPhone (NEW, additive, opt-in).  →  :domain ONLY
:ai             ONNX classifier + TF-IDF vectoriser + ML Kit OCR + ReportNarrator (NEW, Phase 16)
:threatintel    VirusTotal + URLhaus behind ReputationSource. Only networking module —
                joined by ReportNarrator's optional cloud call (NEW, Phase 16, clearly disclosed)
:core           shared UI/theme
```

**Hard rule, unchanged:** `:riskengine` depends on `:domain` only. Phase 16 additions must not violate this — the new `RiskWeightsPhone` table lives in `:riskengine` alongside the frozen `RiskWeights.kt`, as a separate, additive file. The frozen weight table itself is not edited.

---

## 4. Input surfaces

| Surface | Input | Processing | Evidence produced | Privacy behaviour | Status |
|---|---|---|---|---|---|
| Text / SMS | Pasted message | ML + rules + threat intel | OTP, credential, payment, urgency, account threat, impersonation, UPI | Message never transmitted | Frozen (v4.0) |
| URL | Typed link | Structural only + threat intel | Punycode, IP literal, embedded creds, exec path, deep subdomain, shortener, port, org mismatch | Hostname only | Frozen (v4.0) |
| Camera / OCR | Single photo | ML Kit OCR → same text pipeline | Same as Text | Never stored, never uploaded | Frozen (v4.0) |
| QR | Camera frames | Decode → route URL / UPI / TEXT | Payload type + downstream evidence | Decode-only, no auto-open | Frozen (v4.0) |
| Voice | Microphone | On-device ASR → same text pipeline | Same as Text | No recording, no upload | Frozen (v4.0) |
| **Email** | Shared via Android Share Intent from any mail app | Header parse (from-address, subject) + body → same text pipeline | Same as Text, plus sender-domain check | Email content never leaves device except via the same pipeline as Text | **NEW — Phase 16** |
| **Phone number** (optional) | Typed or shared number | Local pattern heuristics only (premium-rate prefix, spoofed/invalid country code, known-format scam patterns) | New `PHONE_*` indicator kinds, additive weights | Number never leaves device — no third-party reputation lookup | **NEW — Phase 16, opt-in/beta** |

**Design invariant, unchanged:** new input paths add *input surfaces*, never new *detection logic* outside their own bounded weight additions. Email reuses the identical Text scoring path with zero changes to existing weights. Phone reuses the same deterministic engine with its own isolated, additive weight file.

---

## 5. Risk model

**Core (frozen — do not change without a blocker):**

Bands: LOW 0–19 · GUARDED 20–39 · MEDIUM 40–60 · HIGH 61–80 · CRITICAL 81–100

```
ML_SIGNAL_MAX 30      OTP_REQUEST 22        CREDENTIAL_REQUEST 18
PAYMENT_REQUEST 15    ORGANISATION_IMPERSONATION 12
DOMAIN_ORGANISATION_MISMATCH 12              URGENCY 10
ACCOUNT_THREAT 10     PUNYCODE_DOMAIN 14     EMBEDDED_CREDENTIALS 14
IP_ADDRESS_URL 12     EXECUTABLE_DOWNLOAD_PATH 12
SUSPICIOUS_URL 10     DEEP_SUBDOMAIN 8       URL_SHORTENER 6
NON_STANDARD_PORT 5   UPI_PAYMENT_REQUEST 10 VOICE_SCAM_LANGUAGE 8 (never emitted)

External, with diminishing corroboration:
  CONFIRMED  [40, 25, 10]   SUSPICIOUS [18, 10, 5]   CONFLICTING 10
  further agreeing source: 5
  NO_DETECTION / UNKNOWN / ERROR = 0, always
```

Additive, then clamped to 0..100. The reasons shown on screen still sum exactly to the score for every core surface.

**Phase 16 addendum (`RiskWeightsPhone` — new file, additive only, does not touch the table above):**

```
PREMIUM_RATE_PREFIX 10   SPOOFED_COUNTRY_CODE 12   KNOWN_SCAM_FORMAT 15
```

These only fire on the optional phone-number surface. They cannot contribute to a Text, URL, Email, QR, Camera, or Voice score. Clearly labeled "beta" in the UI so users understand it's a lighter-weight heuristic, not the same evidentiary depth as the core engine.

**Confidence, safe actions:** unchanged from v4.0.

---

## 6. On-device / cloud AI

**Production classifier (frozen, Phase 1–15):** TF-IDF vectoriser in Kotlin + Logistic Regression exported to ONNX, executed **on the CPU**, unchanged.

**Report Narrator (NEW, Phase 16):** generates a plain-language summary from the already-finalized Evidence/Verdict object. Two implementation paths, decision pending confirmation of iQOO 15 free RAM (do not assume POCO X6 constraints carry over):

- **On-device small model** — preferred if RAM allows; keeps the "no cloud inference" privacy claim fully intact.
- **Cloud API call** — faster to ship, but sends scan evidence off-device. If used, the UI must explicitly disclose this ("AI-generated summary — sends scan details to a cloud service") rather than let it contradict the existing privacy story.

**Fallback, mandatory:** if the Report Narrator fails, times out, or is flagged off at the cutoff, the app shows the existing deterministic explanation string from v4.0 — never a blank state, never a retry loop blocking the verdict screen.

---

## 7. Threat intel

Unchanged from v4.0: VirusTotal + URLhaus behind `ReputationSource`, hostname-only, the only pre-existing networking module (now joined conditionally by the Report Narrator's optional cloud call, §6).

---

## 8. Measured performance

| Operation | Median (POCO X6, interim) | Status |
|---|---|---|
| App cold start | 440 ms | To re-measure on iQOO 15 |
| Text E2E, cold | 405 ms | To re-measure on iQOO 15 |
| Text E2E, warm | < ~12 ms | To re-measure on iQOO 15 |
| URL E2E, online | 1023 ms | To re-measure on iQOO 15 |
| URL E2E, offline | 125 ms | To re-measure on iQOO 15 |
| QR / Camera OCR / Voice → verdict | NOT MEASURED (tooling limitation, not a finding) | Re-attempt on iQOO 15 — MIUI-specific blockers may not apply |
| Email → verdict (NEW) | Not yet measured | Measure once Phase 16 stabilizes |
| Phone heuristic → verdict (NEW) | Not yet measured | Measure once Phase 16 stabilizes |

**All POCO X6 numbers above are provisional.** They validate that the pipeline works, not what it will measure on the actual event hardware. Re-run the full table on the iQOO 15 as the first task on-site, before any new feature work.

---

## 9. Verification status

**Core:** 468 tests, 0 failures — frozen, must not be modified or weakened by Phase 16 work.

**Phase 16 requirement:** every new feature (Email, Phone heuristic, Report Narrator) ships with its own test suite before merge. None of the three may touch, relax, or delete an existing test. `CalibrationTest`-style band/ordering assertions (never exact scores) apply to any new weight table too.

**Device validation:** the six-surface validation in v4.0 §9 stands. Email and Phone heuristic need their own device validation pass before the cutoff; Report Narrator needs a fallback-path validation (confirm the deterministic explanation still displays correctly when the narrator is disabled).

---

## 10. Privacy architecture

| Data | Leaves the device? |
|---|---|
| Message / SMS body | Never |
| OCR text | Never |
| Camera image | Never |
| Voice audio / transcript | Never |
| QR payload | Never |
| URL path / query | Never |
| Email content (NEW) | Never — processed through the same on-device pipeline as Text |
| Phone number (NEW) | Never — heuristics run entirely on-device, no reputation API call |
| Hostname | Yes — to VirusTotal and URLhaus, unchanged |
| Scan evidence, only if cloud Report Narrator is chosen (NEW, conditional) | Yes, if that path is used — must be disclosed on-screen, not buried |

---

## 11. Known limitations

Carried over from v4.0, all still true: extractable API keys (rotate post-event), unmeasured latencies pending iQOO 15, MIUI-incompatible instrumented tests, narrow `DO_NOT_PAY` heuristic on bare UPI IDs, narrow payment-completion guard, minor tone mismatch in "no external source" messaging, host-vs-URL naming nuance on `CONFIRMED`, URLhaus's 100-entry cap, debug-keystore signing, and the classifier's UK-English training data (still the single largest real-world gap — unaffected by Phase 16, still recommended for post-event work unless time allows earlier).

**New, Phase 16:**
11. Email and Phone-number surfaces are new and carry less device-validation history than the six core surfaces — treat any live demo of them as higher-risk than the frozen core.
12. If the cloud path is chosen for the Report Narrator, it is a genuine, disclosed exception to the "nothing but hostname leaves the device" claim — don't let the pitch imply otherwise.
13. The phone-number heuristic is pattern-based only, not a reputation database — it will miss anything outside prefix/format-level red flags. Labeled "beta" for this reason.

---

## 12. Feature status (supersedes v4.0 §12)

| Feature | v4.0 status | v5.0 status |
|---|---|---|
| Email surface | Not built | **Phase 16 — via Android Share Intent, reusing the Text pipeline. Not Gmail OAuth (avoids app-verification delay).** |
| Phone-number check | Not built | **Phase 16 — optional/beta, local pattern heuristics only. Not a third-party reputation API.** |
| Report-generation LLM | Not built | **Phase 16 — post-score narrative only, isolated from the scoring path, mandatory deterministic fallback.** |
| Gmail OAuth | Not built | Still not built — superseded by the Share Intent approach above |
| Cloud AI in the scoring path | Not built | Still not built — would violate Principle 1; not reconsidered |
| VLM / Gemma 3 4B | Not built | Still not built — RAM-constrained on POCO X6; not reconsidered pending iQOO 15 confirmation, and out of scope regardless for this event |
| NPU / QNN acceleration | Not built | Still not built — post-event, once iQOO 15 hardware is available |
| Backend / proxy | Not built | Still not built — post-event |
| CallScreeningService | Not built | Still not built — forbidden by Principle 3 |
| ScamDB India / UPI-reputation APIs | Not built | Still not built — no suitable provider in scope |

---

## 13. Demo specification

**Default demo (guaranteed, matches v4.0 §13):** Home → Text hero → URL control → URL shared-host → URL exact-match → Camera → QR → Voice. 4 min 30 s. This is the fallback regardless of Phase 16 outcome.

**Bonus beat, conditional:** if Email, Phone heuristic, or the Report Narrator pass full validation before the cutoff, add **one** additional beat showcasing the strongest of the three — do not add all three to the live sequence; a longer demo is a higher-risk demo. Choose on 2026-09-09 based on which is most stable, not which is most impressive on paper.

**Cut-for-time order, unchanged:** drop Voice, then QR, then Camera. Never drop Text or the URL pair. Any Phase 16 bonus beat is dropped before any core surface.

---

## 14. Freeze policy and cutoff

**Phases 1–15 remain frozen**, exactly as in v4.0. Phase 16 is scoped, additive work with its own gate:

**Hard cutoff: 2026-09-09.** At that point, for each of the three Phase 16 features:
- Full test suite green + device-validated → ships, added to the feature-status table as built.
- Not fully green → feature-flagged off. The app defaults to v4.0 behavior for that surface (Email/Phone simply aren't offered; Report Narrator's fallback text is used everywhere).

No feature may be half-shipped into the demo. The frozen core is the non-negotiable floor.

**Post-event roadmap (unchanged from v4.0, nothing new added before the event):**
1. Rotate both API keys.
2. Server proxy to remove key extractability.
3. Indian scam training data — still the largest real-world gap, whether or not Phase 16 lands.
4. Full email classifier adoption (already audited, Phase 15).
5. NPU/QNN path once iQOO 15 hardware is confirmed and available long-term.
6. Vision-model layout understanding for Lens — after, and only after, the above.

---

## 15. Presentation language guardrails

Same rule as §0, made concrete: if it's not verified in this document, don't say it on stage. Specific pairs to get right under pressure:

| Say | Don't say | Why |
|---|---|---|
| "On-device AI" | "NPU-powered AI" / "uses the Snapdragon NPU" | Production classifier runs on ONNX Runtime **CPU** execution provider. NPU is unvalidated (§12) — only claim it if it's actually confirmed working on the iQOO 15 before you go on stage. |
| `github.com` → **LOW, 18/100** (host-level) | "suspicious" | This is the actual locked demo value (§13). State exactly what the screen shows. |
| The exact-match URL step is refreshed morning-of and may or may not be `CONFIRMED` | Scripting "CONFIRMED" as a guaranteed outcome | §13's demo-day dependency: if no live entry exists that morning, drop the step and present the host-level case alone — don't assert a result you haven't just verified. |
| "Deterministic risk engine, LLM only for the summary text" (if Report Narrator ships) | "AI decides the risk score" | Principle 1 (§2): the LLM, if present, never touches scoring — only the display narrative. |
| "Sends only the hostname to threat intel" | "Fully offline" / "nothing ever leaves the device" (once Report Narrator's cloud path, if chosen, is live) | §10 — hostname always leaves for threat intel; scan evidence leaves too, only if the cloud Report Narrator path is used, and only with on-screen disclosure. |

If a judge asks something not covered by this document, the honest answer is a limitation acknowledged in §11, not an improvised claim.

---

*This document is the final pre-event plan and pitch-language reference. v5.0 and v4.0 remain the frozen-core records of what was built and planned through Phase 15/16. v3.2 remains the historical record of original intent. All are kept; none replaces another.*
