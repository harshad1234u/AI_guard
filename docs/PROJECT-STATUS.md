# TrustLens — Project Status & Build Log

**Event:** iQOO Hackathon 2026 — Chennai City Battle, 12–13 Sept 2026 (30-hour phone-first build)
**Last updated:** 2026-09-05, end of Phase 14C
**State:** Phases 1–14C complete. Device regression, latency measurement, the URLhaus
historical-host fix and physical-input validation are all done on real hardware — see §11.

---

## 1. What TrustLens is

An Android, phone-first, on-device scam-analysis app. Five input surfaces — pasted text, URL,
camera OCR, QR, voice — all converge on **one** analysis pipeline and **one** deterministic risk
engine. The app explains *why* something is risky. It never acts on the user's behalf.

### Non-negotiable security principles

These are load-bearing. Every phase was reviewed against them; do not relax any of them.

| # | Principle | How it is enforced |
|---|---|---|
| 1 | **Untrusted content is DATA, never instructions.** SMS, OCR text, QR payloads, URLs, voice transcripts and threat-intel responses must never become agent instructions. | No LLM in the scoring path at all. Everything is deterministic extraction + a fixed weight table. Prompt-injection tests assert that injected text scores as evidence and changes no action. |
| 2 | **UNKNOWN ≠ SAFE.** If threat intel cannot determine maliciousness, it stays UNKNOWN. | `ReputationVerdict` has **no `SAFE` case**. `NO_DETECTION`, `UNKNOWN` and `ERROR` all contribute **+0**, never a reduction. UI copy: *"Nothing here confirms this content is safe — it means nobody was asked."* |
| 3 | **Agent ≠ action executor.** May extract, investigate, query, reason, score, explain. May **not** open URLs, pay, send OTP, send money, send messages, install apps, or run commands. | `SafeAction` has 7 members and every one of them *declines or defers*. Enforced by absence, not by a runtime check. A test asserts no member is named PAY / SEND / OPEN_PAYMENT / LAUNCH_PAYMENT. |

Corollaries that were also enforced:

- URL analysis **never** opens a URL, resolves DNS, follows redirects, makes HTTP requests to
  the target, expands short URLs, or contacts the domain. Analysis is purely structural.
- Camera **never** auto-opens a detected URL or launches a payment app. Images are never
  uploaded and never persisted.
- Voice **never** records calls, intercepts calls, monitors the mic continuously, retains raw
  audio, uploads audio, or listens in the background.
- Threat-intel is queried with **hostnames only** — never paths, query strings, full messages,
  images, or audio.

### Hardware honesty constraint (repeated every single phase)

> **iQOO 15 NPU / Snapdragon NPU / QNN acceleration / iQOO thermal / iQOO latency: PENDING
> TARGET-HARDWARE VALIDATION.** Never claim NPU acceleration until it is measured on an iQOO 15.

Required wording for demo docs and pitch:

> "The current classifier runs through ONNX Runtime on CPU. iQOO 15 Snapdragon NPU/QNN
> validation is still pending target-hardware access."

### Accuracy wording (mandatory, verbatim)

> **98.65% held-out accuracy on the UCI SMS Spam Collection.**

Never describe this as "TrustLens scam-detection accuracy". It is a public English SMS spam
corpus, not Indian banking scams.

The figure lives in `tools/model-metrics.json`. **The app's UI does not display it** — the
result screen states the model version, the execution backend, and the training-corpus caveat
("trained on a public English SMS spam corpus, not on Indian banking scams"), but makes no
numerical accuracy claim at all. That is the safer position and is deliberate: if the number is
quoted anywhere — pitch, slide, README — it must carry the full sentence above. Corrected in
Phase 13, which found this paragraph previously asserted the UI showed it.

---

## 2. Architecture

```
:app          Compose UI host, Hilt wiring, MainActivity, integration tests
:feature:*    home / textcheck / urlcheck / lensscan / qrscan / voicecheck / result
:domain       pure Kotlin/JVM — models + use cases. Depends on nothing.
:investigation  pure JVM — deterministic extractors (indicators, URL, QR)
:riskengine   pure JVM — RiskEngine + RiskWeights.  ->  :domain ONLY
:ai           ONNX classifier + TF-IDF vectoriser + ML Kit OCR
:threatintel  VirusTotal + URLhaus behind ReputationSource
:core         shared UI/theme
```

Hard rule: **`:riskengine` depends on `:domain` only.** It must never see `:threatintel`,
`:ai`, or Android. Threat intel reaches it as already-parsed `ExternalEvidence`.

### Build stack

| Thing | Version / note |
|---|---|
| AGP | 9.3.1 — **built-in Kotlin**: do NOT apply `org.jetbrains.kotlin.android` to Android modules; `kotlin { jvmToolchain() }` fails |
| Kotlin | 2.3.21 · KSP 2.3.10 · Hilt 2.60.1 |
| SDK | compileSdk 37, targetSdk 36, minSdk 26 |
| CameraX | 1.6.2 · ML Kit Text 16.0.1 · ML Kit Barcode 17.3.0 (both bundled) |
| ONNX Runtime | 1.29.0, **CPU execution provider only** |
| Net | OkHttp 5.5.0 · kotlinx-serialization-json 1.11.0 (JsonElement parsing only, no compiler plugin) |
| ABI | `abiFilters += "arm64-v8a"` — APK 203.7 MB → **65 MB**. Add `x86_64` back to run on an emulator. |

`org.jetbrains.kotlin.plugin.compose` is still required on Compose modules.

---

## 3. Phase-by-phase log

Every phase was gated: a before-implementation report, implementation, an after-implementation
report, and explicit user approval before the next began.

| Phase | What shipped |
|---|---|
| 1–5 | Modules, domain models, deterministic extractors, risk engine, text/URL UI |
| **6** | Camera / OCR — CameraX `Preview` + `ImageCapture`, ML Kit Text Recognition. Images never persisted, never uploaded. |
| **7** | QR Scanner — ML Kit Barcode, `ImageAnalysis` + `STRATEGY_KEEP_ONLY_LATEST`, deterministic payload classification (URL / UPI / TEXT), dedup + debounce. Decode-only: no auto-open, no auto-pay, no "Pay Now" button. |
| **8** | Voice Check — on-device `SpeechRecognizer`, runtime backend detection, disclosure before mic use. Transcript is routed through the *same* `AnalyzeTextUseCase`. VOICE is an input source, **not** a scam signal. |
| **9** | Real ML classifier — TF-IDF + Logistic Regression, trained on UCI SMS Spam, exported to ONNX, CPU inference. Replaced the mock. |
| **10** | Threat Intelligence — VirusTotal + URLhaus behind `ReputationSource`. Evidence, not instructions. |
| **10-live** | Live provider verification with real keys (opt-in test). |
| **11** | Risk Engine Calibration — fixed `SUSPICIOUS_URL` dedup underweighting at its cause. |
| **11A** | Text-path UPI destination scoring. |
| **11B** | Safe-action context guard for `DO_NOT_PAY`. |
| **12** | Demo / release hardening. Full test run, release APK, first Git commit (`f067704`). |
| **13** | Final security & hackathon readiness audit — **PASS**, no BLOCKER or HIGH findings. Read-only; no code changed. |
| **14** | Device regression + first latency measurement on POCO X6. **PASS.** Found one HIGH defect (URLhaus historical hosts). |
| **14A** | URLhaus historical-vs-active verdict calibration. **PASS.** `www.google.com` 40→18, CONFIRMED→SUSPICIOUS. |
| **14B** | Physical-input validation — real camera OCR, controlled QR payloads, real spoken audio. **PASS.** |
| **14C** | Shared-host threat-intel calibration — URL-level vs host-level URLhaus evidence. **PASS.** |

### Phase 9 — the ONNX export trap (important, do not regress)

skl2onnx's `TfidfVectorizer` export is **not faithful**: the ONNX `Tokenizer` n-grams every
token, while sklearn filters with `\b\w\w+\b` *first*. 281/1115 messages diverged, max Δ 0.082,
1 label flip.

**Fix:** the TF-IDF vectoriser was reimplemented in Kotlin (`TfidfVectoriser.kt`) and **only the
Logistic Regression** is exported to ONNX. Parity is now 1.203e-07 with 0 disagreements, pinned
by `TfidfVectoriserParityTest` against golden vectors in `parity-vectors.tsv`.

Second trap: `UNICODE_CHARACTER_CLASS` is **not supported on Android** (it throws at
`TfidfVectoriser.<clinit>`) even though JVM unit tests pass. The regex is now
`Regex("[\\p{L}\\p{N}_]+")` plus a `MIN_TOKEN_LENGTH = 2` filter. **This class of bug is only
findable by running on a device.**

### Phase 10 — what was learned

- `lookupSubjects()` originally emitted both the registrable domain and the raw domain, so each
  provider was queried **twice**. Now one entry per distinct host.
- URLhaus is queried at `POST /v1/host/`, deliberately **not** `/url/`, to avoid transmitting
  per-victim path tokens to a third party.
- A provider-confirmed malicious domain scoring 35 (GUARDED) produced no `DO_NOT_OPEN_LINK`.
  Fixed in `actionsFor` via `externallyFlaggedDestination` — **no weights were changed.**

### Phase 11 — calibration

Root cause was in the *analyzer*, not the weights: four distinct structural URL properties were
being merged into a single `SUSPICIOUS_URL` observation worth 10. `DeterministicUrlAnalyzer` now
emits one observation per finding, and four new `ObservationKind`s exist
(`PUNYCODE_DOMAIN`, `EMBEDDED_CREDENTIALS`, `IP_ADDRESS_URL`, `EXECUTABLE_DOWNLOAD_PATH`,
plus `DEEP_SUBDOMAIN`, `NON_STANDARD_PORT`, `URL_SHORTENER`, `DOMAIN_ORGANISATION_MISMATCH`).

Also added: **diminishing corroboration** for independent providers
(`EVIDENCE_CONFIRMED_SCHEDULE = [40, 25, 10]`, `EVIDENCE_SUSPICIOUS_SCHEDULE = [18, 10, 5]`) so
two providers agreeing is stronger than one but not double. Confidence was reworked around
*source agreement*, not score: degraded → never HIGH; disagreement → MEDIUM; ≥2 agreeing
answered sources → HIGH.

### Phase 11A — text-path UPI evidence

The stated gap was one bug; the false-positive tests exposed two more.

1. `UPI_PAYMENT_REQUEST` was only ever emitted by the QR path. Now derived in the engine from
   `indicators.paymentRequest && indicators.upiIds.isNotEmpty()` — both halves required.
2. **`paymentRequest` had no negation guard at all.** *"Do not send money to fraudster@ybl"* set
   it true — TrustLens was scoring its own advice as a scam signal. Now uses the same `isNegated`
   mechanism as `otpRequest` / `credentialRequest`.
3. `"Send Rs. 5000 to merchant@example"` was not recognised: `PAYMENT_PHRASE` required the literal
   word *money*. Extended with `(?:send|transfer|remit|deposit)\s+(?:rs\.?|inr|₹)\s*[\d,]+`.

Plus a narrow completion guard (`PAYMENT_COMPLETED`, `COMPLETION_WINDOW = 30`) so a receipt is not
read as a fresh demand.

**`UPI_PAYMENT_REQUEST` weight left at 10, unchanged.** Scenario E moved 32 → 42
(GUARDED → MEDIUM) purely from new evidence, not from a raised number.

### Phase 11B — safe-action context guard

`DO_NOT_PAY` fired on the mere presence of a UPI ID, so TrustLens answered its own warning
*"do not send money to X"* with *"Do not pay"*. One condition in `RiskEngine.actionsFor`:

```kotlin
ObservationKind.PAYMENT_REQUEST in kinds ||
ObservationKind.UPI_PAYMENT_REQUEST in kinds ||
(indicators.upiIds.isNotEmpty() && level >= RiskLevel.MEDIUM)   // was: upiIds.isNotEmpty()
```

The band-gated fallback stays deliberately, mirroring how `DO_NOT_OPEN_LINK` treats links.
**No weight, threshold or band was touched.**

---

## 4. Current risk model

**Bands:** LOW 0–19 · GUARDED 20–39 · MEDIUM 40–60 · HIGH 61–80 · CRITICAL 81–100

**Weights** (`RiskWeights.kt`):

```
ML_SIGNAL_MAX 30   OTP_REQUEST 22   CREDENTIAL_REQUEST 18   PAYMENT_REQUEST 15
ORGANISATION_IMPERSONATION 12   DOMAIN_ORGANISATION_MISMATCH 12
URGENCY 10   ACCOUNT_THREAT 10   PUNYCODE_DOMAIN 14   EMBEDDED_CREDENTIALS 14
IP_ADDRESS_URL 12   EXECUTABLE_DOWNLOAD_PATH 12   SUSPICIOUS_URL 10
DEEP_SUBDOMAIN 8   URL_SHORTENER 6   NON_STANDARD_PORT 5
UPI_PAYMENT_REQUEST 10   VOICE_SCAM_LANGUAGE 8
External: CONFIRMED [40,25,10] · SUSPICIOUS [18,10,5] · CONFLICTING 10
          NO_DETECTION / UNKNOWN / ERROR = 0, always
```

**Safe actions (all 7 decline or defer):** `DO_NOT_OPEN_LINK`, `DO_NOT_PAY`, `DO_NOT_SHARE_OTP`,
`DO_NOT_SHARE_CREDENTIALS`, `VERIFY_VIA_OFFICIAL_CHANNEL`, `REPORT_OR_BLOCK`, and a
proceed-carefully default.

---

## 5. Tests — 445 total, 0 failures, 4 skipped (opt-in live provider)

```
investigation.DeterministicUrlAnalyzerTest          56
investigation.RuleBasedIndicatorExtractorTest       48
investigation.DeterministicQrPayloadClassifierTest  28
riskengine.CalibrationTest                          26
riskengine.RiskEngineTest                           22
QrFlowIntegrationTest                               22
threatintel.VirusTotalSourceTest                    21
domain.AnalyzeQrPayloadUseCaseTest                  20
ThreatIntelFlowIntegrationTest                      20
UpiEvidenceIntegrationTest                          20
VoiceFlowIntegrationTest                            19
domain.AnalyzeScannedTextUseCaseTest                18
domain.AnalyzeTextUseCaseTest                       16
domain.AnalyzeTranscriptUseCaseTest                 16
UrlFlowIntegrationTest                              15
threatintel.UrlhausSourceTest                       15
domain.InvestigateIndicatorsUseCaseTest             14
HeroFlowIntegrationTest                             13
ScanFlowIntegrationTest                             13
ai.TfidfVectoriserParityTest                        10
domain.ScanDebouncerTest                             8
threatintel.LiveProviderVerificationTest             4  (skipped; -Dtrustlens.live=true)
CalibrationMatrixReportTest                          1  (reporting harness, asserts nothing)
```

Standing rules that were followed and must keep being followed:

- **Never modify a test merely to accommodate unexpected model behaviour.**
- **Never weaken or delete a security test because a weight changed.**
- `CalibrationTest` asserts **bands and orderings only**, never exact scores, to avoid overfitting.
- `CalibrationMatrixReportTest` prints the measured matrix but asserts nothing, so it can never be
  the reason a weight "has" to be a particular number.

Live provider tests are opt-in via `-Dtrustlens.live=true` so ordinary runs never burn a rate limit.

---

## 6. Device validation (POCO X6 5G — *not* the Motorola Edge 50 Ultra; user accepted this)

Verified on device with the real ONNX classifier:

```
"URGENT: Your parcel will be returned. Transfer Rs. 5000 to merchant@example immediately."
  57/100 MEDIUM · UPI_PAYMENT_REQUEST +10 · Recommended: Do not pay

"Urgent: transfer Rs. 5000 to merchant@example now"
  59/100 MEDIUM · Recommended: Do not pay + Verify through the official app or helpline

"Do not send money to merchant@example."
  10/100 LOW · no payment evidence · Recommended: Proceed carefully   (negation guard holds)

"merchant@example"
   3/100 LOW · Recommended: Proceed carefully                          (11B guard holds)
```

### Device gotchas worth remembering

- MIUI/HyperOS **uninstalled the app** after `connectedAndroidTest`, and blocks test-APK installs
  with `INSTALL_FAILED_USER_RESTRICTED`. Instrumented tests may simply be unrunnable there.
- `uiautomator dump` returns empty when the screen is asleep — wake with `input keyevent 224` first.
- Dismiss the keyboard before swiping, or the swipe gesture-types garbage into the field.
- `adb` is not on PATH here: `$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`.
- PowerShell mangles `-Pandroid.testInstrumentationRunnerArguments...` — quote it.

---

## 7. Known limitations (disclose these; do not paper over them)

1. **API keys are extractable from the APK.** This is the Phase 10 SECURITY FAIL. It is inherent
   to `buildConfigField`; the only real fix is a server proxy, which is out of scope. Mitigations:
   free-tier keys, no billing attached, R8. **Rotate both keys after the hackathon** — rotation is
   a mitigation, not a repair.
2. `DO_NOT_PAY` still fires on a bare UPI ID **when the message is independently MEDIUM+**. This is
   deliberate and tested, but it is a heuristic.
3. The payment-completion guard is narrow: *"I'll pay him tomorrow"* is not recognised as a
   non-request.
4. **Latency has never been measured** — not QR scan latency, not ONNX inference latency.
5. Camera permission-**denial** UI path is unverified on device.
6. Scam-message OCR through the live camera is unverified.
7. Duplicate-lookup prevention is unit-tested but not observed on device.
8. "External verification: no external source could be consulted" shows even when nothing needed
   consulting. Accurate, slightly off in tone.

---

## 8. Explicitly out of scope (do not add)

Gmail OAuth · ScamDB India · phone-reputation APIs · UPI-reputation APIs · VLM / Gemma ·
NPU / QNN implementation · backend / server / proxy · call interception · call recording ·
background microphone · automatic payment, URL-opening or message-sending · new threat-intel
providers · cloud LLM reasoning · **any LLM anywhere in the scoring path**.

---

## 9. Secrets

- `secrets.properties` at repo root is **gitignored** (`.gitignore` line 25) and holds
  `VIRUSTOTAL_API_KEY` + `URLHAUS_AUTH_KEY`. It is read by `threatintel/build.gradle.kts` into
  `buildConfigField`s; when absent, the fields are empty and the build still succeeds.
- `*.onnx` is gitignored, with a deliberate un-ignore for `!ai/src/main/assets/scam-clf.onnx`
  (120 KB, a build input the app cannot run without).
- Keys must never be printed, logged, committed, or included in evidence objects, URLs, exception
  messages, screenshots or test output. `LiveProviderVerificationTest` asserts no credential
  appears in any evidence field.

---

## 10. Phase 12 & 13 — completed

**Phase 12 — demo / release hardening.** Full `./gradlew test`, release APK built, secrets
verified absent from Git/source/APK-resources/logs, `secrets.properties` confirmed ignored,
`scam-clf.onnx` confirmed intentionally tracked. Repo committed: **`f067704` on `master`**,
working tree clean. (The repo is no longer uncommitted — the previous revision of this document
said "no commits yet", which was stale.)

**Phase 13 — final security & hackathon readiness audit: PASS.** Read-only; no source file was
modified. Verified this run:

- No LLM anywhere. The dependency catalogue has zero LLM/GenAI/TFLite/MediaPipe entries, and the
  shipped ONNX graph contains exactly one operator: `LinearClassifier`. The sklearn tokenizer has
  not leaked back in.
- No action capability. A grep for `startActivity` / `Intent(` / `ACTION_VIEW` / `Uri.parse` /
  `WebView` across all eight modules returns **one** hit: `RecognizerIntent` handed to
  `SpeechRecognizer.startListening`, never to `startActivity`. No `Runtime.exec`, no
  `ProcessBuilder`, no `Socket`, no SMS or payment API.
- UNKNOWN ≠ SAFE, proven numerically rather than by inspection. In the calibration matrix,
  baseline `B` = 43, `G + both NO_DETECTION` = 43, `H + both UNKNOWN` = 43. Exactly +0.
- Secrets: all 353 tracked files scanned byte-for-byte against both live key values — zero
  matches, and `secrets.properties` has never been in history.
- 445 tests, 0 failures, 0 errors, 4 skipped (opt-in live provider).

Audit findings: 0 BLOCKER, 0 HIGH, 1 MEDIUM (API keys extractable from the APK — known and
accepted, see §7.1), 4 LOW, 4 INFO.

---

## 11. Phases 14 / 14A / 14B — device validation (all PASS)

Device: **POCO X6 5G** (`23122PCD1I`, `garnet`), **Android 16 / API 36**, arm64-v8a, release APK
0.1.0. The POCO is **not** an iQOO 15; every number here is a POCO number.

> "The current classifier runs through ONNX Runtime on CPU. iQOO 15 Snapdragon NPU/QNN
> validation is still pending target-hardware access."

### Measured latency (Phase 14) — the project's first real numbers

| Operation | Median | Range | n |
|---|---|---|---|
| App cold start (`am start -W`) | 440 ms | 415–564 | 7 |
| Text E2E, cold (incl. model load) | 405 ms | 219–534 | 6 |
| Text E2E, warm | below one display frame (< ~12 ms) | — | 13 attempts, never repainted |
| URL E2E online (real providers) | 1023 ms | 696–1153 | 8 |
| URL E2E offline | 125 ms | 81–284 | 4 |
| Threat-intel round trip (derived) | ≈ 900 ms | — | — |

Cold start + first analysis ≈ **0.85 s to first verdict**. **Model cold load dominates, not
inference.** QR / camera-OCR / voice latency is **NOT MEASURED** — `screenrecord` cannot capture
the CameraX preview (hardware overlay: 5 frames in 10 s) and `uiautomator` polling costs ~2.6 s
per sample. That is a measurement-tooling limit, not a performance finding. Do not estimate it.

### Phase 14A — URLhaus historical-host calibration

URLhaus tracks URLs and never forgets one, so every large shared host accumulates permanent
entries. `describeListing` returned CONFIRMED for any `query_status:"ok"`, which called
`www.google.com` malicious (+40). Now graded on the provider's own per-entry `url_status`:
**any `online` entry → CONFIRMED; entries but all `offline` → SUSPICIOUS.** Historical abuse
still adds risk (+18) and is never worded as safe. **No weight, band, threshold, confidence rule
or SafeAction was touched.**

Live-verified: `example.com` no_results → NO_DETECTION · `www.google.com` 4 entries/0 online →
SUSPICIOUS · `github.com` 100/19 online → CONFIRMED · `drive.google.com` 100/9 online → CONFIRMED.

**14A residue, closed by 14C:** hosts *currently* serving malware still read CONFIRMED even
when household names — `github.com` scored 40/100 MEDIUM. Resolved below.

### Phase 14C — URL-level vs host-level URLhaus evidence

The host response already **enumerates the offending URLs**, so the sharper question can be
answered **on this device**: is the link the user is holding one of them? The observed URL is
passed to the adapter as local context (`ReputationSource.lookup(subject, observedUrls)`,
defaulted so no other provider changes) and matched locally. **The request is still `host=` and
nothing else — the privacy rule is untouched, and a test pins it.**

Four cases, strongest evidence first:

| Situation | Verdict | Wording |
|---|---|---|
| This exact URL listed **and online** | CONFIRMED | "reports **this exact URL** as malicious and currently online" |
| This exact URL listed, offline | SUSPICIOUS | "**this exact URL** … not currently online" |
| No URL context (bare host asked about) | Phase 14A host grading | unchanged |
| Host listed, this URL not among entries | SUSPICIOUS | "a report about **the host, not about this URL**" |

The last case is **never** NO_DETECTION: URLhaus returns at most 100 of a possibly far larger
set (`url_count` is 7,939 for `github.com`), so absence from the returned slice is not evidence
of absence — the summary says so in as many words. No allowlist, no hardcoded domain; the
adapter has no idea which hosts are famous.

Device-verified on POCO X6, same host, opposite verdicts:

```
https://github.com                      18/100 LOW      SUSPICIOUS  "not among them"
https://github.com/<listed-online-path>  40/100 MEDIUM  CONFIRMED   "this exact URL"
https://example.com                       0/100 LOW     NO_DETECTION (unchanged)
```

Detection of genuinely malicious destinations is therefore **not** weakened — it is now more
precise than before 14A.

### Phase 14B — physical input, on real hardware

All six surfaces exercised against the release APK with real optics and a real microphone.

| Surface | Result | Evidence |
|---|---|---|
| Camera OCR | PASS | Photographed scam sheet → **62/100 HIGH**, ML+18 urgency+10 OTP+22 SBI+12, `DO_NOT_SHARE_OTP` + `REPORT_OR_BLOCK` |
| QR plain text | PASS | `Hello from TrustLens` → TEXT routing, 12/100 LOW |
| QR benign URL | PASS | `https://example.com` → URL routing, 0/100, both providers NO_DETECTION |
| QR bare UPI | PASS | `upi://pay?pa=testmerchant@example` → **10/100 LOW — not fraud** |
| QR UPI request | PASS | +urgency/amount/note → **35/100 GUARDED**, `DO_NOT_PAY` |
| QR dedup/debounce | PASS | Code held in frame 24 s after rescan → decoded **once**, no re-analysis loop |
| Voice | PASS | Real spoken audio → **26/100 GUARDED**, OTP+22, `DO_NOT_SHARE_OTP`, mic released after 5.9 s |

Every score was the exact sum of its stated reasons. **No browser launch, no payment-app launch,
no `ACTION_VIEW`, no image or audio persisted, no external data dir ever created, no key or
content in logcat** (152,563 lines swept).

Real OCR artefacts worth remembering: ML Kit read `will` as `wilI`, and captured the physical
keyboard behind the monitor (`ESC`, `F2`, `BACKSPACE`, …) as extra tokens. The deterministic
layer was unaffected — noise tokens carry no indicators.

### Still open

- QR/OCR/voice latency (measurement tooling, see above)
- Instrumented tests unrunnable on MIUI (`INSTALL_FAILED_USER_RESTRICTED`); `connectedAndroidTest`
  also **uninstalls the app** — reinstall before continuing device work
- A host with live abuse whose *specific* URL is beyond URLhaus's 100-entry response window
  is reported at host level (SUSPICIOUS), not URL level — the honest answer, but less precise
  than a `/v1/url/` lookup would give, which the hostname-only privacy rule rules out
