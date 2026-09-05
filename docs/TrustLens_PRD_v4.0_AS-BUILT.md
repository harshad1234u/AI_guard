# TrustLens — Product Requirements Document v4.0 (AS-BUILT)

> **See the risk before you click.**

**Version:** 4.0 — As-Built / Engineering Frozen
**Date:** 2026-09-05
**Supersedes:** `TrustLens_iQOO_Hackathon_PRD_v3.2 (1).md` (planning document, 2026-09-04) — **that file is unchanged and remains the historical record of intent**
**Target Event:** iQOO Hackathon 2026 — Chennai City Battle, 12–13 September 2026
**Build state:** Phases 1–15 complete · **468 tests, 0 failures** · engineering **FROZEN**
**Validation device:** POCO X6 5G (`23122PCD1I`, Snapdragon 7s Gen 2 / SM7435), Android 16 / API 36, arm64-v8a
**Release artefact:** `app-release.apk`, 64,155,230 bytes, versionName 0.1.0

---

## 0. How this document differs from v3.2

v3.2 described what TrustLens *would* be. **v4.0 describes only what TrustLens verifiably is**, with every number traced to code, a test, or a measurement taken on real hardware. Nothing here is aspirational.

Where v3.2 planned a capability that was not built, it is listed in §12 as descoped, with the reason. Nothing was silently dropped.

**Reading rule:** if a claim is not in this document, do not make it on stage.

---

## 1. Product statement

TrustLens is an Android, phone-first, on-device scam-analysis app. Five input surfaces — pasted text, URL, camera OCR, QR, voice — converge on **one** analysis pipeline and **one** deterministic risk engine.

> **Most scam detectors stop at detection. TrustLens investigates the threat before the user acts.**

It explains *why* something is risky, in arithmetic the user can audit. **It never acts on the user's behalf.**

---

## 2. Non-negotiable security principles (as enforced in code)

| # | Principle | Enforcement mechanism |
|---|---|---|
| 1 | **Untrusted content is DATA, never instructions** | No LLM in the scoring path at all. Deterministic extraction + fixed weight table. The shipped ONNX graph contains exactly one operator: `LinearClassifier`. 8 prompt-injection tests assert injected text scores as evidence and changes no action. |
| 2 | **UNKNOWN ≠ SAFE** | `ReputationVerdict` has **no `SAFE` case**. `NO_DETECTION`, `UNKNOWN`, `ERROR` contribute **+0**, never a reduction. Proven numerically: baseline 43 → +NO_DETECTION 43 → +UNKNOWN 43. |
| 3 | **Agent ≠ action executor** | `SafeAction` has 7 members; every one declines or defers. Enforced by *absence*, not a runtime check. Zero `startActivity` / `ACTION_VIEW` / `WebView` / `Runtime.exec` in the codebase. Verified on device: 0 browser or payment-app launches across all surfaces. |

**Corollaries, all verified:**
- URL analysis never opens, resolves, expands, redirects to, or fetches a URL. Analysis is purely structural.
- Camera never persists or uploads an image, never auto-opens a detected URL, never launches a payment app.
- Voice never records calls, intercepts calls, listens in the background, or retains raw audio.
- Threat intel is transmitted **hostname-only** — never paths, query strings, messages, images or audio.

---

## 3. Architecture (as built)

```
:app            Compose UI host, Hilt wiring, MainActivity, integration tests
:feature:*      home · textcheck · urlcheck · lensscan · qrscan · voicecheck · result
:domain         pure Kotlin/JVM — models + use cases + contracts. Depends on nothing.
:investigation  pure JVM — deterministic extractors (indicators, URL, QR)
:riskengine     pure JVM — RiskEngine + RiskWeights.  →  :domain ONLY
:ai             ONNX classifier + TF-IDF vectoriser + ML Kit OCR
:threatintel    VirusTotal + URLhaus behind ReputationSource. Only networking module.
:core           shared UI/theme
```

**Hard rule:** `:riskengine` depends on `:domain` only. It cannot reach `:ai`, `:threatintel` or Android — the architecture rule is enforced by the dependency graph, not by convention.

### Pipeline

```
Input → ML signal (capped at 30/100) → Indicator extraction → Investigation
      → Threat intel (hostname only) → Evidence
      → Deterministic Risk Engine → Explanation → Safe action
```

**The 30-second pitch:** *ML gives an initial signal. The investigation layer collects evidence. The deterministic engine combines them into an explainable score.*

### Build stack

| Component | Version / note |
|---|---|
| AGP | 9.3.1 — built-in Kotlin; do **not** apply `org.jetbrains.kotlin.android` to Android modules |
| Kotlin / KSP / Hilt | 2.3.21 · 2.3.10 · 2.60.1 |
| SDK | compileSdk 37, targetSdk 36, minSdk 26 |
| CameraX / ML Kit | 1.6.2 · Text 16.0.1 · Barcode 17.3.0 (both bundled) |
| ONNX Runtime | 1.29.0 — **CPU execution provider only** |
| Net | OkHttp 5.5.0 · kotlinx-serialization-json 1.11.0 (JsonElement only) |
| ABI | `arm64-v8a` only — APK 203.7 MB → **64 MB** |

---

## 4. The five surfaces (as delivered)

| Surface | Input | Processing | Evidence produced | Privacy behaviour | Fallback |
|---|---|---|---|---|---|
| **Text / SMS** | Pasted message | ML + rules + threat intel | OTP, credential, payment, urgency, account threat, impersonation, UPI | Message never transmitted | — (is the fallback) |
| **URL** | Typed link | Structural only + threat intel | Punycode, IP literal, embedded creds, exec path, deep subdomain, shortener, non-standard port, org mismatch | Hostname only; never opened or resolved | Works fully offline (structural) |
| **Camera / OCR** | Single photo | ML Kit OCR → *same* text pipeline | Same as Text | **Never stored, never uploaded**; bitmap recycled after OCR; no external data dir ever created | Type the text manually |
| **QR** | Camera frames | Decode → route URL / UPI / TEXT | Payload type + downstream evidence | Decode-only; no auto-open, no auto-pay | Type the payload |
| **Voice** | Microphone | On-device ASR → *same* text pipeline | Same as Text | No recording, no upload; mic released on stop | "Type it instead" box on-screen |

**Design invariant:** camera, QR and voice add *input paths*, never *detection logic*. A spoken sentence, a photographed message and a pasted message reach an identical score — pinned by tests.

`VOICE_SCAM_LANGUAGE` exists as an `ObservationKind` and is deliberately **never emitted**: voice is an input source, not a risk signal.

---

## 5. Risk model (frozen — do not change without a blocker)

**Bands:** LOW 0–19 · GUARDED 20–39 · MEDIUM 40–60 · HIGH 61–80 · CRITICAL 81–100

**Weights** (`RiskWeights.kt` — the only file that may contribute points):

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

Additive, then clamped to 0..100. **The reasons shown on screen sum exactly to the score** — e.g. the hero SMS: 23+10+22+10+12 = 77.

**Confidence is independent of risk.** It reflects corroboration, not danger: degraded → never HIGH; sources disagreeing → MEDIUM; ≥2 agreeing answered sources → HIGH.

**Safe actions (7, all decline or defer):** `DO_NOT_OPEN_LINK`, `DO_NOT_PAY`, `DO_NOT_SHARE_OTP`, `DO_NOT_SHARE_CREDENTIALS`, `VERIFY_VIA_OFFICIAL_CHANNEL`, `REPORT_OR_BLOCK`, `PROCEED_CAREFULLY`.

---

## 6. On-device ML (as built)

**Production classifier:** TF-IDF vectoriser reimplemented in Kotlin + Logistic Regression exported to ONNX, executed by ONNX Runtime **on the CPU**.

**Mandatory accuracy wording:**

> **The current SMS classifier achieved 98.65% held-out accuracy on the UCI SMS Spam Collection.**

Never state this as "TrustLens detects scams with 98.65% accuracy."

| Metric | Value |
|---|---|
| Accuracy | 98.65% |
| Precision (spam) | 95.89% |
| Recall (spam) | 93.96% |
| F1 (spam) | 94.92% |
| ROC-AUC | 99.17% |
| Test set | 1,115 held-out messages |
| ONNX ↔ sklearn parity | 1.203e-07, **0 label disagreements** |

**Mandatory dataset caveat:** these are benchmark results on a 2011 UK-English SMS corpus containing no UPI IDs, no KYC fraud and no OTP harvesting. They are **not** a claim about real-world scam detection. Indian-scam handling comes from the deterministic layer — which is precisely why the model is capped at 30 of 100.

**Mandatory hardware wording:**

> **The current classifier runs through ONNX Runtime on CPU. iQOO 15 Snapdragon NPU/QNN validation is still pending target-hardware access.**

**Two traps that must not regress:**
1. skl2onnx's `TfidfVectorizer` export is **not faithful** — ONNX n-grams every token while sklearn filters with `\b\w\w+\b` first. Hence the Kotlin vectoriser and LogReg-only ONNX. Re-confirmed in the Phase 15 model audit: converting a full pipeline diverged on 432/800 messages (max Δ 5.1e-02).
2. `UNICODE_CHARACTER_CLASS` is **unsupported on Android** and throws at class-init, though JVM tests pass. The regex is `[\p{L}\p{N}_]+` plus `MIN_TOKEN_LENGTH = 2`. **This class of bug is only findable on a device.**

Failure behaviour: any model failure yields `ClassificationResult.Unavailable`, the pipeline continues on rules alone, and the UI states the model did not run. A classifier that cannot run must never look like one that found nothing.

---

## 7. Threat intelligence (as built)

Two providers behind `ReputationSource`: **VirusTotal** (`GET /api/v3/domains/{domain}`) and **URLhaus** (`POST /v1/host/`).

**Privacy rule:** hostname only. URLhaus's `/v1/url/` endpoint is deliberately *not* used, because phishing paths carry per-victim tokens. Verified live on device: for `http://sbi-kyc.example.com/verify?token=abc123`, the transmitted subject was `sbi-kyc.example.com` — the path and token never left the phone.

### Six verdict states

| Verdict | Meaning | Points |
|---|---|---|
| `CONFIRMED` | Source positively reports the subject as malicious | 40 / 25 / 10 |
| `SUSPICIOUS` | Indicators of concern short of confirmation | 18 / 10 / 5 |
| `NO_DETECTION` | Source answered and holds no report. **Not a safety guarantee** | **0** |
| `UNKNOWN` | No usable answer — unsupported subject, no key configured | **0** |
| `ERROR` | Lookup failed — offline, rate-limited, timed out, malformed | **0** |
| `CONFLICTING` | Sources disagreed materially | 10 |

**There is no `SAFE` state, by design.**

### URLhaus evidence grading (Phases 14A + 14C)

URLhaus tracks *URLs*, never forgets one, and returns at most 100 of a possibly far larger set. Raw host-level listing therefore says little about a specific link. Evidence is graded from the provider's own per-entry `url_status`, with the analysed URL matched **locally on-device** (nothing extra transmitted):

| Situation | Verdict | Wording shown |
|---|---|---|
| This exact URL listed **and online** | `CONFIRMED` | "reports **this exact URL** as malicious and currently online" |
| This exact URL listed, offline | `SUSPICIOUS` | "this exact URL … not currently online — a past-abuse record" |
| No URL context (bare host queried) | host-level grading | as before |
| Host listed, this URL not among entries | `SUSPICIOUS` | "a report about **the host, not about this URL**" |

The last case is **never** `NO_DETECTION`: absence from a truncated slice is not evidence of absence, and the summary says so. **No allowlist, no hardcoded domain.**

Device-verified, same host, opposite verdicts:

```
https://example.com                      →  0/100 LOW   NO_DETECTION ×2, confidence HIGH
https://github.com                       → 18/100 LOW   SUSPICIOUS, host-level wording
https://github.com/<listed-online-path>  → 40/100 MED   CONFIRMED, "this exact URL"
```

---

## 8. Measured performance (POCO X6 5G — not an iQOO 15)

| Operation | Median | Range | n |
|---|---|---|---|
| App cold start | **440 ms** | 415–564 | 7 |
| Text E2E, cold (incl. model load) | **405 ms** | 219–534 | 6 |
| Text E2E, warm | **< ~12 ms** (one display frame — a bound, not a measurement) | — | 13 attempts, never repainted |
| URL E2E, online (live providers) | **1023 ms** | 696–1153 | 8 |
| URL E2E, offline | **125 ms** | 81–284 | 4 |
| Threat-intel round trip (derived) | **≈ 900 ms** | — | — |
| **QR decode → verdict** | **NOT MEASURED** | — | — |
| **Camera OCR → verdict** | **NOT MEASURED** | — | — |
| **Voice → verdict** | **NOT MEASURED** | — | — |

Cold start + first analysis ≈ **0.85 s to first verdict**. **Model cold load dominates, not inference.**

The three unmeasured items are a **measurement-tooling limitation**, not a performance finding: `screenrecord` cannot capture the CameraX preview (hardware overlay — 5 frames in 10 s), `uiautomator` polling costs ~2.6 s per sample, and MIUI blocks instrumented benchmarks with `INSTALL_FAILED_USER_RESTRICTED`. **Report "not measured". Never estimate.**

---

## 9. Verification status

**Test suite:** 468 tests · **0 failures** · 0 errors · 4 skipped (opt-in live-provider tests).

Standing rules that must keep being followed:
- Never modify a test merely to accommodate unexpected model behaviour.
- Never weaken or delete a security test because a weight changed.
- `CalibrationTest` asserts **bands and orderings only**, never exact scores, to avoid overfitting.
- `CalibrationMatrixReportTest` prints the measured matrix but asserts nothing, so it can never be the reason a weight "has" to be a particular number.

**Physical device validation (Phase 14B) — all six surfaces on the release APK:**

| Surface | Result |
|---|---|
| Text | 77/100 HIGH, exact-sum reasons, `DO_NOT_SHARE_OTP` |
| URL | structural + live threat intel, all four verdict states exercised |
| Camera OCR | photographed scam sheet → **62/100 HIGH** → OTP + report actions |
| QR | plain text / URL / bare UPI (10/100 LOW, not fraud) / UPI request (35/100, `DO_NOT_PAY`) |
| QR dedup | code held in frame 24 s → decoded **once**, no re-analysis loop |
| Voice | real spoken audio → 26/100, OTP +22, mic released after 5.9 s |

**Security regression (device, 162,400 logcat lines):** 0 `ACTION_VIEW`, 0 browser/payment launches, 0 API keys, 0 files written, no external data dir, no network module outside `:threatintel`.

---

## 10. Privacy architecture

| Data | Leaves the device? |
|---|---|
| Message / SMS body | **Never** |
| OCR text | **Never** |
| Camera image | **Never** — in-memory only, bitmap recycled, no file written |
| Voice audio | **Never** — `SpeechRecognizer` returns text; `onBufferReceived` is a no-op |
| Voice transcript | **Never** |
| QR payload | **Never** |
| URL path / query | **Never** |
| **Hostname** | **Yes** — to VirusTotal and URLhaus, and nothing else |

There is no backend, no account, no analytics, no telemetry, no cloud inference.

---

## 11. Known limitations (disclose; do not paper over)

1. **API keys are extractable from the APK.** Inherent to `buildConfigField` — empirically confirmed: the VirusTotal key is recoverable from the release dex. Mitigations: free-tier keys, no billing, R8 available but off. **Rotate both keys after the event.** The only real fix is a server proxy, out of scope.
2. QR / camera-OCR / voice latency unmeasured (tooling, §8).
3. Instrumented tests unrunnable on MIUI; `connectedAndroidTest` **uninstalls the app** — never run it before a demo.
4. `DO_NOT_PAY` still fires on a bare UPI ID when the message is independently MEDIUM+. Deliberate, tested, still a heuristic.
5. Payment-completion guard is narrow: *"I'll pay him tomorrow"* is not recognised as a non-request.
6. "External verification: no external source could be consulted" appears even when nothing needed consulting (a message with no links). Accurate, slightly off in tone.
7. On a URL-level `CONFIRMED`, the reason line names the queried host while the evidence card names the exact URL. Both true; the evidence card is the precise one.
8. URLhaus returns ≤100 entries per host, so a non-match is not proof of absence — stated on screen.
9. Release APK is signed with the public debug keystore. Fine for side-loading; must be replaced before any distribution.
10. The classifier is trained on UK-English SMS spam, not Indian scams. This is the single largest real-world gap.

---

## 12. Descoped from v3.2 — what was planned and deliberately not built

| v3.2 feature | Status | Reason |
|---|---|---|
| Email surface | **Not built** | No email input surface exists; `InvestigationSource` has no `EMAIL`. A trained email classifier was audited (Phase 15) and recommended for post-event adoption. |
| Phone-number reputation | **Not built** | No provider in scope |
| Gmail OAuth | **Not built** | Out of scope; adds auth + backend |
| Cloud AI / cloud reasoning | **Not built** | Violates the no-LLM-in-scoring principle and the offline guarantee |
| VLM / Gemma 3 4B | **Not built** | Audited Phase 16 (read-only): **no model asset exists**, no LiteRT/MediaPipe dependency, and the demo device has ~2.44 GB available RAM against ~3–4 GB required. Recommendation: **do not integrate**. |
| NPU / QNN acceleration | **Not built** | Not validated on target hardware; a 50k-coefficient dot product is not an NPU workload |
| Backend / proxy | **Not built** | Out of scope; would fix the API-key limitation post-event |
| CallScreeningService / call interception | **Not built** | Explicitly forbidden by the security principles |
| ScamDB India, UPI-reputation APIs | **Not built** | No suitable provider in scope |

**None of these may be added without lifting the engineering freeze.**

---

## 13. Demo specification (locked, Phase 15)

**Sequence (4 min 30 s):** Home → Text hero → URL control → URL shared-host → URL exact-match → Camera → QR → Voice.

**Locked inputs:**
```
SMS   URGENT: Your SBI account will be blocked today. Share the OTP sent to your phone immediately.
URL   https://example.com                          → 0/100 LOW
URL   https://github.com                           → 18/100 LOW  (host-level)
URL   <refresh on demo morning>                    → 40/100 MED  (exact URL)
QR    https://example.com
QR    upi://pay?pa=testmerchant@example            → 10/100 LOW (not fraud)
QR    upi://pay?pa=testmerchant@example&am=4999&tn=URGENT…  → 35/100, DO_NOT_PAY
OCR   ocr_scam.png                                 → 62/100 HIGH
VOICE "Your account will be blocked today. Share the OTP immediately."  → 26/100
```

All UPI destinations are synthetic. **Never open the exact-match URL in a browser.**

**Demo-day dependency:** the exact-match URL depends on live third-party data that rotates. Run `_toolchain/phase15/refresh-control.sh` on the morning; if no online entries exist, drop that step and present the host-level case alone.

**Cut-for-time order:** drop Voice, then QR, then Camera. Never drop Text or the URL pair.

**Pre-demo checklist:** charged · unlocked · 10-min screen timeout · **Do Not Disturb ON** (a notification interrupted a QR scan during validation) · app launches · control refreshed · Wi-Fi confirmed.

---

## 14. Engineering freeze

As of Phase 15, TrustLens is **frozen**. Any further change requires explicit justification and should be treated as a blocker fix only.

Freeze criteria, all met: 468 tests / 0 failures · all six surfaces hardware-validated · latency measured · no BLOCKER or HIGH finding open · demo rehearsed end-to-end on the release APK.

**Post-event roadmap** (nothing before the event):
1. Rotate both API keys.
2. Server proxy to remove key extractability.
3. Indian scam training data — the largest real-world gap.
4. Email classifier adoption (already audited: converts faithfully at 3.9e-07, 1.9 MB assets, awaiting an input-surface decision).
5. NPU/QNN path once iQOO 15 hardware is available.
6. Vision-model layout understanding for Lens — after, and only after, the above.

---

*This document records what was built and verified. The v3.2 PRD records what was intended. Both are kept; neither replaces the other.*
