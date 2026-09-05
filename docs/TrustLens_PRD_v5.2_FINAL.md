# TrustLens — Product Requirements Document v5.2 (FINAL — Codebase-Verified)

> **See the risk before you click.**

**Version:** 5.2 — Final Pre-Event Plan, corrected against a full codebase audit
**Date:** 2026-09-05
**Supersedes:** `TrustLens_PRD_v5.1_FINAL.md` (2026-09-05) — a codebase review (§16) found several claims in v5.1 the code didn't yet support. This version corrects them. v4.0 remains the frozen-core historical record.
**Target Event:** iQOO Hackathon 2026 — Chennai City Battle, 12–13 September 2026
**Build state:** Phases 1–15 **intended frozen** (468 tests, 0 failures, 4 skipped, in the current working tree) · Phase 16 extensions **NOT YET STARTED** in code · hard cutoff **2026-09-09**
**Validation device (interim):** POCO X6 5G — a temporary stand-in for development only. No number in this document is final until re-measured on the actual iQOO 15 at the event.
**Release artefact (baseline):** `app-release.apk`, 64,155,230 bytes, versionName 0.1.0 — Phase 16 additions will increment this on merge.

> ⚠️ **TOP-PRIORITY ACTION, BEFORE ANYTHING ELSE IN THIS DOCUMENT:** the repository has exactly one commit. Everything this document calls "frozen" — the 468-test suite, `RiskWeights.kt`, the whole Phase 15 baseline — exists only as uncommitted working-tree state on one machine. Commit and tag it today: `git add -A && git commit -m "Phase 15 freeze checkpoint" && git tag v4.0-freeze`. Until that happens, "frozen" is not a real guarantee — see §16.

---

## 0. How this document differs from v5.1

v4.0 was closed: as-built, frozen, no further scope. v5.1 laid out a plan to reconsider three previously-descoped features (§12 of v4.0) for pre-event inclusion. **A full codebase audit (§16) then checked v5.1's claims against the actual repository** and found the plan sound but several passages written as if further along than the code shows — plus one real structural risk: the "frozen" baseline is currently uncommitted.

**This document (v5.2) is the corrected version: same plan, but every claim now says what's actually true today versus what's designed-but-not-built.**

**Reading rules:**
- If a claim isn't in this document, don't make it on stage.
- If something is marked PLANNED or NOT BUILT below, it doesn't exist in code yet — don't describe it in the present tense to a judge.
- If a Phase 16 feature isn't fully built, tested, and device-validated by the cutoff, it is feature-flagged off, and the app ships exactly as v4.0 describes it for that surface. The frozen core is the guaranteed fallback, always — once it's actually committed (see the warning above and §16).

---

## 1. Product statement

TrustLens is an Android, phone-first, on-device scam-analysis app. Input surfaces converge on **one** analysis pipeline and **one** deterministic risk engine.

> **Most scam detectors stop at detection. TrustLens investigates the threat before the user acts.**

It explains *why* something is risky, in arithmetic the user can audit. **It never acts on the user's behalf.**

---

## 2. Non-negotiable security principles (as enforced in code)

| # | Principle | Enforcement mechanism |
|---|---|---|
| 1 | **Untrusted content is DATA, never instructions** | No LLM in the *scoring* path. Deterministic extraction + fixed weight table. The shipped ONNX graph contains exactly one operator: `LinearClassifier`. 9 prompt-injection tests (Hero, Scan, Upi, Voice, Qr, ScannedText, Transcript, Urlhaus, VirusTotal) assert injected text scores as evidence and changes no action. |
| 2 | **UNKNOWN ≠ SAFE** | `ReputationVerdict` has no `SAFE` case. `NO_DETECTION`, `UNKNOWN`, `ERROR` contribute +0, never a reduction. |
| 3 | **Agent ≠ action executor** | `SafeAction` has 7 members; every one declines or defers. Enforced by absence — **zero** `startActivity` / `ACTION_VIEW` / `WebView` / `Runtime.exec` anywhere in the codebase, unconditionally. No exception exists for Phase 16 or anything else; if a share/report action is ever added, this table gets a new row, not a carve-out. |

**Phase 16 plan for Principle 1 (not yet implemented — see §16):** the planned report-generation LLM would sit strictly *after* the RiskEngine finalizes a verdict, receiving only the completed Evidence/Verdict object, never raw pipeline internals, with display-only narrative output. As designed it cannot change a score, a band, or a safe action — but as of this writing, no Report Narrator code exists, so nothing yet enforces this. Treat it as a design requirement for whoever builds it, not a shipped guarantee.

**Corollaries, unchanged from v4.0:**
- URL analysis never opens, resolves, expands, redirects to, or fetches a URL.
- Camera never persists or uploads an image, never auto-opens a URL, never launches a payment app.
- Voice never records calls, intercepts calls, or retains raw audio.
- Threat intel is transmitted hostname-only.

---

## 3. Architecture

```
:app            Compose UI host, Hilt wiring, MainActivity, integration tests
:feature:*      home · textcheck · urlcheck · lensscan · qrscan · voicecheck · result
                — emailshare and phonecheck are PLANNED, Phase 16; neither module exists yet
:domain         pure Kotlin/JVM — models + use cases + contracts. Depends on no other
                TrustLens module (it does use kotlinx-coroutines-core and javax.inject).
:investigation  pure JVM — deterministic extractors (indicators, URL, QR)
                — a phone-pattern extractor is PLANNED, Phase 16; does not exist yet
:riskengine     pure JVM — RiskEngine + RiskWeights.  →  :domain ONLY
                — RiskWeightsPhone is PLANNED, Phase 16, as a separate additive file; does not exist yet
:ai             ONNX classifier + TF-IDF vectoriser + ML Kit OCR
                — a Report Narrator is PLANNED, Phase 16; zero code exists yet
:threatintel    VirusTotal + URLhaus behind ReputationSource. The only networking module today.
                A Report Narrator cloud call, if that path is chosen, would join this — not yet built.
:core           shared UI/theme
```

**Hard rule, verified:** `:riskengine` currently depends on `:domain` only — confirmed by a full audit of its build file and imports. Any Phase 16 module must preserve this; none of the Phase 16 modules exist yet to check.

---

## 4. Input surfaces

| Surface | Input | Processing | Evidence produced | Privacy behaviour | Status |
|---|---|---|---|---|---|
| Text / SMS | Pasted message | ML + rules + threat intel | OTP, credential, payment, urgency, account threat, impersonation, UPI | Message never transmitted | Frozen (v4.0) |
| URL | Typed link | Structural only + threat intel | Punycode, IP literal, embedded creds, exec path, deep subdomain, shortener, port, org mismatch | Hostname only | Frozen (v4.0) |
| Camera / OCR | Single photo | ML Kit OCR → same text pipeline | Same as Text | Never stored, never uploaded | Frozen (v4.0) |
| QR | Camera frames | Decode → route URL / UPI / TEXT | Payload type + downstream evidence | Decode-only, no auto-open | Frozen (v4.0) |
| Voice | Microphone | On-device ASR → same text pipeline | Same as Text | No recording, no upload | Frozen (v4.0) |
| **Email** (PLANNED — no code yet) | Would be shared via Android Share Intent from any mail app | Would parse headers (from-address, subject) + body → same text pipeline | Would produce same evidence as Text, plus sender-domain check | Designed for: email content never leaves device | **NOT BUILT — no `:feature:emailshare` module, no `ACTION_SEND` intent-filter in the manifest** |
| **Phone number** (PLANNED — no code yet) | Would accept a typed or shared number | Would run local pattern heuristics (premium-rate prefix, spoofed/invalid country code, known-format scam patterns) | Would produce new `PHONE_*` indicator kinds | Designed for: number never leaves device | **NOT BUILT — no module, no weight file, no indicator kinds exist** |

**Design invariant for Phase 16, once built:** new input paths should add *input surfaces*, never new *detection logic* outside their own bounded weight additions. Email is designed to reuse the identical Text scoring path with zero changes to existing weights; Phone is designed to reuse the same deterministic engine with its own isolated, additive weight file. Neither exists yet — this is the design contract for whoever builds them, not a shipped fact.

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

**Phase 16 addendum, PLANNED — `RiskWeightsPhone` does not exist yet, no `PHONE_*` indicator kinds exist yet:**

```
PREMIUM_RATE_PREFIX 10   SPOOFED_COUNTRY_CODE 12   KNOWN_SCAM_FORMAT 15
```

Design intent, once built: these would only fire on the optional phone-number surface and could not contribute to a Text, URL, Email, QR, Camera, or Voice score, with a "beta" label in the UI. None of this is implemented yet.

**Confidence, safe actions:** unchanged from v4.0.

---

## 6. On-device / cloud AI

**Production classifier (Phase 1–15, verified):** TF-IDF vectoriser in Kotlin + Logistic Regression exported to ONNX. A direct protobuf parse confirms the graph has exactly one node, `op_type='LinearClassifier'`, executed by `OnnxScamClassifier.kt` using default session options — **CPU, not NPU**. Verified identical in both the source asset and the model extracted from the release APK.

**Report Narrator — PLANNED, Phase 16, zero code exists.** Design intent: generate a plain-language summary from the already-finalized Evidence/Verdict object, with the implementation path (on-device small model vs. cloud API call) decided once iQOO 15 free RAM is confirmed — do not assume POCO X6 RAM constraints carry over. If a cloud path is chosen, the UI must explicitly disclose it ("AI-generated summary — sends scan details to a cloud service") rather than contradict the existing privacy story. Mandatory design requirement: if it fails, times out, or is flagged off at the cutoff, the app must show the existing deterministic explanation string — never a blank state. None of this is built yet, so none of it is enforced yet.

---

## 7. Threat intel

Verified: `OkHttpTransport.kt` is the sole HTTP client in the codebase; the only endpoints in main source are `urlhaus-api.abuse.ch/v1/host/` and `virustotal.com/api/v3/domains`, both host-scoped, with a test asserting the transmitted URL contains neither path nor query token. `:threatintel` is the only module making network calls today. A Report Narrator cloud call, if that path is ever chosen, would join this — not yet built.

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
| Email → verdict | N/A — not built | Cannot measure until §4's planned surface exists |
| Phone heuristic → verdict | N/A — not built | Cannot measure until §4's planned surface exists |

**All POCO X6 numbers above are provisional.** They validate that the pipeline works, not what it will measure on the actual event hardware. Re-run the full table on the iQOO 15 as the first task on-site, before any new feature work.

---

## 9. Verification status

**Core, as measured by a fresh forced re-run (`./gradlew test --rerun-tasks`):** 468 tests, 0 failures, 0 errors, **4 skipped** (the opt-in `LiveProviderVerificationTest`). Per module: app 134 · investigation 132 · domain 92 · threatintel 52 · riskengine 48 · ai 10.

**Freeze integrity — the real risk, see §16:** this 468-test baseline exists only in the current uncommitted working tree. The repository's single commit predates it (452 `@Test` functions at HEAD). Until the tree is committed and tagged, "frozen" describes a policy, not a protected state — nothing stops an accidental `git checkout` or `rm -rf` from silently reverting the last day of work. **Commit today**, per the warning at the top of this document.

**One test's assertion changed, uncommitted:** `UrlhausSourceTest.kt` — the case that offline-only listings count as `CONFIRMED` was replaced with offline-only listings being `SUSPICIOUS` rather than `CONFIRMED`. This looks like a genuine false-positive fix rather than an accommodation, and predates the freeze — but with no commit history, that timing can't be proven. Net working-tree diff since the repo's one commit: +24 tests across two files, −1 test, 0 other weakenings found.

**Phase 16 requirement, once work starts:** every new feature (Email, Phone heuristic, Report Narrator) ships with its own test suite before merge. None of the three may touch, relax, or delete an existing test. `CalibrationTest`-style band/ordering assertions (never exact scores) apply to any new weight table too.

**Device validation:** the six-surface validation in v4.0 §9 stands (device-only; not reproducible from the JVM test suite, which wires a mock classifier, not the real ONNX model). Email and Phone heuristic will need their own device validation pass once built; Report Narrator will need a fallback-path validation.

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
| Email content (planned, not built) | Designed for: never — would process through the same on-device pipeline as Text |
| Phone number (planned, not built) | Designed for: never — heuristics would run entirely on-device |
| Hostname | Yes — to VirusTotal and URLhaus, unchanged |
| Scan evidence, only if cloud Report Narrator is chosen (NEW, conditional) | Yes, if that path is used — must be disclosed on-screen, not buried |

---

## 11. Known limitations

Carried over from v4.0, all still true: extractable API keys (rotate post-event), unmeasured latencies pending iQOO 15, MIUI-incompatible instrumented tests, narrow `DO_NOT_PAY` heuristic on bare UPI IDs, narrow payment-completion guard, minor tone mismatch in "no external source" messaging, host-vs-URL naming nuance on `CONFIRMED`, URLhaus's 100-entry cap, debug-keystore signing, and the classifier's UK-English training data (still the single largest real-world gap — unaffected by Phase 16, still recommended for post-event work unless time allows earlier).

**From the codebase audit (§16):**
11. The entire "frozen" baseline is uncommitted working-tree state — see the warning at the top of this document and §9. This is the single biggest risk to the whole plan, independent of anything else in this list.
12. The demo-morning refresh script (`_toolchain/phase15/refresh-control.sh`) referenced by §13 does not exist in the repo. The URL exact-match step currently has no working refresh path — build this script or update §13 before the cutoff.
13. The locked OCR demo asset (`ocr_scam.png`) is not in the repo. The Camera → 62/100 HIGH beat depends on a file that isn't version-controlled — add it or the beat can't be reproduced on a fresh checkout.
14. §13's demo scores (77 SMS, 62 OCR, 26 Voice) are device-only measurements — the JVM test suite wires a mock classifier, never the real ONNX model, so these can't be re-verified from the repo alone. They should be re-run on real hardware before the event, not assumed unchanged.

**Once Phase 16 work begins (currently N/A — nothing is built yet):**
15. Email and Phone-number surfaces, once built, will be new and will carry less device-validation history than the six core surfaces — treat any live demo of them as higher-risk than the frozen core.
16. If the cloud path is chosen for the Report Narrator, it will be a genuine, disclosed exception to the "nothing but hostname leaves the device" claim — don't let the pitch imply otherwise.
17. The phone-number heuristic, once built, will be pattern-based only, not a reputation database — it will miss anything outside prefix/format-level red flags. Label it "beta" for this reason.

---

## 12. Feature status (supersedes v4.0 §12)

| Feature | v4.0 status | v5.2 status (code-verified) |
|---|---|---|
| Email surface | Not built | **Still not built. Planned: Android Share Intent, reusing the Text pipeline, not Gmail OAuth. No module, no manifest intent-filter exist yet.** |
| Phone-number check | Not built | **Still not built. Planned: optional/beta, local pattern heuristics only, not a third-party reputation API. No module or weight file exist yet.** |
| Report-generation LLM | Not built | **Still not built. Planned: post-score narrative only, isolated from scoring, mandatory deterministic fallback. Zero code exists yet.** |
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

**Two gaps to close before this is actually reproducible (found in §16's audit):**
- `_toolchain/phase15/refresh-control.sh`, needed for the morning-of URL refresh, does not exist in the repo. Recreate it or replace this step with a manual refresh procedure.
- `ocr_scam.png`, the locked Camera input, is not in the repo. Add it to version control or the OCR beat can't be run from a fresh checkout.

Both are pre-event action items, not optional polish — the demo as specified currently can't be run end-to-end from what's committed.

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

## 16. Codebase audit summary (2026-09-05)

A full audit — fresh test re-run, whole-repo greps, byte-level ONNX parse of both the source asset and the shipped APK's model, and a git diff against the repository's single commit — checked v5.1 against the actual code. Full results folded into the sections above; headline findings:

**Confirmed, unconditionally:** the `:riskengine → :domain` dependency rule, the frozen weight table's exact values, the single-operator ONNX graph (CPU, not NPU), zero `startActivity`/`ACTION_VIEW`/`WebView`/`Runtime.exec` anywhere in the codebase, `:threatintel` as the only networking module with host-scoped-only endpoints, the 7-member `SafeAction` enum, and the 468/0/4-skipped test result.

**Corrected in this version:** the Principle 3 "carve-out" that cited a non-existent section, the injection-test count (8 → 9), the missing "4 skipped" qualifier, a stale "v5.0 status" column header, and imprecise "depends on nothing" domain-module phrasing.

**The one finding that matters most:** none of it — not the 468 tests, not `RiskWeights.kt`, not the freeze itself — is protected by version control. The repository has exactly one commit, and everything this document calls "frozen" is uncommitted working-tree state on a single machine. **Commit and tag it before anything else on your list.**

**Not yet built, contrary to how v5.1 sometimes read:** the Email surface, the phone-number heuristic, and the Report Narrator all have zero code — no modules, no manifest entries, no weight files. Sections 4, 5, 6, and 12 above have been reworded to say "planned" rather than describing them as already working.

---

*This document is the final, codebase-verified pre-event plan and pitch-language reference. v5.1, v5.0, and v4.0 remain on record as the planning history; v3.2 remains the original intent. All are kept; none replaces another. Only this version (v5.2) should be treated as current.*
