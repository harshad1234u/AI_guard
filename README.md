# TrustLens

**See the risk before you click.**

An on-device scam investigator for Android. Paste a message, point the camera at a poster,
scan a QR, share an email, or put a call on speakerphone — TrustLens tells you *why* something
is risky, with every point of the score traced to a sentence, and it never opens, pays, or
installs anything.

> **Most scam detectors stop at detection. TrustLens investigates the threat before the user acts.**

iQOO Hackathon 2026 · Kotlin · Jetpack Compose · ONNX Runtime · fully offline-capable

---

## Overview

| | |
|---|---|
| Platform | Android 8.0+ (API 26), `arm64-v8a` |
| Language | Kotlin, Jetpack Compose, multi-module Gradle |
| ML runtime | ONNX Runtime, **CPU** |
| Network | Optional. The entire analysis path runs in aeroplane mode. |
| Tests | **606 total · 602 passing · 0 failures · 0 errors · 4 skipped** |
| Status | Verified software checkpoint. **Not yet validated on physical hardware** — see [Current Limitations](#current-limitations). |

## Problem

The dangerous moment is *before* the click.

```
Message arrives → curiosity or trust → click / pay / read out a code → loss
```

Most security tools act at the last step. By the time something is reported, the OTP is shared
and the money is gone. And most people genuinely cannot separate a real bank alert from a good
forgery in the ten seconds they give it.

## Solution

A decision layer for those ten seconds. Seven ways in, one investigation pipeline, one
explained verdict.

## Key Features

| Feature | What it does |
|---|---|
| **Check Message** | Suspicious SMS, chat, or pasted text |
| **Check Link** | Investigates a URL **without opening it** |
| **QR** | Decoded and investigated — never opened, never paid |
| **Camera / OCR** | Reads text off a photo or screen, on-device |
| **Voice Check** | Say back what the caller said |
| **Check Email** | Shared in from any mail app — no OAuth, no mailbox access |
| **Live Guard** | Listens on speakerphone and re-scores a call as it develops |
| **Selected text** | Check a highlighted line from inside Gmail, Chrome, or anywhere |
| **Hindi / Hinglish** | Deterministic multilingual extraction — no model, no translation service |

## Architecture

```
Mobile
  ↓
ML Detection          on-device classifier, capped at 30 of 100
  ↓
AI Investigation      deterministic extraction + URL structure analysis
  ↓
Threat Intelligence   optional, hostname-only, corroboration not prerequisite
  ↓
Evidence              observations + external reports, each attributable
  ↓
Risk Scoring          pure deterministic engine, 0–100
  ↓
Explanation           every point traced to a stated reason
  ↓
Safe Action           decline or defer only — seven possibilities, no others
```

Module layout:

```
:app             Compose host, Hilt wiring, entry points, integration tests
:feature:*       home · textcheck · urlcheck · lensscan · qrscan · voicecheck ·
                 emailshare · result
:core            design system — tokens, theme, shared components
:domain          pure Kotlin — models and use cases. Depends on nothing.
:investigation   pure JVM — deterministic extractors (indicators, URL, QR, email,
                 multilingual)
:riskengine      pure JVM — RiskEngine + RiskWeights → :domain only
:ai              ONNX classifier + TF-IDF vectoriser + ML Kit OCR
:threatintel     VirusTotal + URLhaus adapters, hostname-only
```

## AI/ML Pipeline

TF-IDF (unigram + bigram, ~12,000 terms) → logistic regression → ONNX. Model version
`uci-sms-tfidf-logreg-v1`, roughly 120 KB, no network.

- The vectoriser is reimplemented in Kotlin; a parity test holds the maximum probability delta
  against the Python original at **1.2e-07** with **zero** label disagreements over 1,115 messages.
- A protobuf parse confirms the graph is a single `LinearClassifier` node.
- Execution is **CPU**, with default session options. `OnnxScamClassifier` reports the backend
  that actually ran. **No NPU execution is claimed or implemented.**
- The classifier contributes **at most 30 of 100** and never decides alone.
- When it cannot assess something it says so. Two cases take that path: a link with no message
  around it, and text with no vocabulary coverage. Neither is ever reported as a score of 0.0.

## Investigation Pipeline

Rule-based extraction of URLs, domains, UPI IDs, phone numbers, amounts, and request types —
OTP, credentials, payment, urgency, account threat, impersonated organisation — plus offline
structural analysis of every link: brand-vs-registrable-domain mismatch, free-registration
TLDs, punycode, IP-literal hosts, embedded credentials, shorteners, deep subdomains,
non-standard ports, executable download paths.

**Nothing is resolved, fetched, or rendered.** URL analysis is string and structure work.

## Threat Intelligence

Optional corroboration from VirusTotal and URLhaus, weighted with diminishing returns — the
first source to report a host carries the most weight, each further independent source adds
less.

- **Hostnames only.** Paths and query strings are dropped; a phishing path routinely carries a
  per-victim token.
- **`UNKNOWN`, `NO_DETECTION` and `ERROR` contribute exactly zero, and never subtract.**
- Unavailability is reported on screen, never allowed to pass for a clean result.
- **Not a prerequisite:** `sbi.bank.tk/verify` reaches 22/100 GUARDED with zero external
  evidence — a brand-new phishing domain has no reputation history.

## Risk Engine

Pure Kotlin. No IO, no randomness — identical input always yields an identical score. Every
number that can affect a score lives in one file, `RiskWeights.kt`, additive then capped at 100.

| Band | Score | User action |
|---|---|---|
| LOW | 0–19 | Proceed carefully |
| GUARDED | 20–39 | Verify before acting |
| MEDIUM | 40–60 | Do not act until verified |
| HIGH | 61–80 | Stop and verify independently |
| CRITICAL | 81–100 | Do not open, pay, or share sensitive information |

## Privacy & Security

- Suspicious links are **never opened** — no `startActivity`, no `ACTION_VIEW`, no `WebView`,
  no URI handler anywhere in the codebase.
- No `Runtime.exec`, no `ProcessBuilder`.
- No automatic payment or external action. `SafeAction` has exactly seven members and every one
  is decline or defer.
- The message body never leaves the device. Only hostnames do, and only for an optional lookup.
- No Gmail OAuth, no mailbox access, no cloud LLM, no external AI API.
- Content is treated as data, never as instruction.

Full detail and reproduction commands: [`docs/TRUSTLENS-SECURITY.md`](docs/TRUSTLENS-SECURITY.md).

## Supported Input Surfaces

Message · Link · QR · Camera/OCR · Voice · Email · Live Guard · Selected text — all converging
on one `AnalyzeTextUseCase`, so a highlighted fragment and a pasted message reach an identical
verdict.

## Benchmark

> **98.65% held-out accuracy on the UCI SMS Spam Collection** — 5,574 real labelled SMS, 1,115
> in the test split. Precision 0.959 · recall 0.940 · F1 0.949 · ROC-AUC 0.992.

**Read that carefully.** It is a **component-level benchmark on a public English spam corpus**,
spam vs ham, measuring one input worth at most 30% of the final score. It is **not** TrustLens's
scam-detection accuracy and must never be quoted as one. The corpus is UK English from around
2011 — it has never seen a UPI handle, an Indian bank name, or a KYC scam.

TrustLens has **no end-to-end accuracy figure**, because we have not built a labelled Indian
scam corpus to measure one against.

| Claim type | Example |
|---|---|
| **Measured** | 606 tests · 602 pass · release build green · APK 61.30 MiB · ONNX parity 1.2e-07 |
| **Benchmarked** | 98.65% on UCI SMS Spam Collection (component-level) |
| **Device-validated** | QR and OCR beats, POCO X6, Phase 14B — **v4.0 era, predates the current commit** |
| **Not yet validated** | Everything on the current commit: Live Guard, Email, selected text, multilingual, P0/P1 |
| **Planned** | NPU/QNN execution provider · Indian scam corpus · end-to-end accuracy |

## Demo

- [`docs/TRUSTLENS-DEMO-GUIDE.md`](docs/TRUSTLENS-DEMO-GUIDE.md) — 4–5 minute judging demo
- [`docs/TRUSTLENS-DEMO-FALLBACK.md`](docs/TRUSTLENS-DEMO-FALLBACK.md) — what to do when something breaks
- [`docs/demo-assets/`](docs/demo-assets/) — copy-paste inputs, all synthetic

Two numbers in the demo are exactly reproducible, because no model runs on either input:

| Input | Score | Band |
|---|---|---|
| `https://www.sbi.co.in` (the real bank) | **0/100** | LOW |
| `sbi.bank.tk/verify` (impersonating it) | **22/100** | GUARDED |

Before the P1 guard that ordering was inverted — the genuine domain scored **p=0.8888** and
collected **+26**, the phish scored **p=0.1729** and collected **+5**.

## Screenshots

**None yet.** No UI screenshot exists in this repository, and capturing them requires a device
that is currently unavailable. The images under `tools/demo/assets/` are demo *inputs*, not
screenshots. Checklist: [`docs/SCREENSHOT-CHECKLIST.md`](docs/SCREENSHOT-CHECKLIST.md).

## Build Instructions

```bash
git clone <repo> && cd trustlens

./gradlew test                  # 606 tests — no device, no network, no API key needed
./gradlew :app:assembleRelease  # → app/build/outputs/apk/release/app-release.apk
```

Threat-intel lookups are optional. To enable them, create `secrets.properties` in the repo root
(gitignored, never committed):

```properties
VIRUSTOTAL_API_KEY=your_key
URLHAUS_AUTH_KEY=your_key
```

Without it the app runs normally and reports threat intelligence as unconfigured — which is not
the same as clean.

Install and pre-flight: [`docs/RELEASE-CHECKLIST.md`](docs/RELEASE-CHECKLIST.md).

## Project Structure

```
app/  core/  domain/  investigation/  riskengine/  ai/  threatintel/  feature/*/
docs/                 guides, security, limitations, Q&A, PRDs, demo assets
tools/demo/           QR + OCR demo inputs, device-run log, refresh script
```

## Current Limitations

The honest short list — full version in
[`docs/TRUSTLENS-LIMITATIONS.md`](docs/TRUSTLENS-LIMITATIONS.md):

1. **No device validation of the current commit.** The software checkpoint is verified; no
   handset has run this build.
2. **The classifier's training corpus does not match its deployment domain** — UK English SMS
   from 2011 against Indian scams in 2026. Mitigated by the 30-point cap and the deterministic
   layer, but it is a real gap.
3. **No end-to-end accuracy figure**, because there is no labelled Indian scam corpus yet.
4. **API keys are extractable from the release APK** — free-tier, no billing, rotate after the
   event. The only real fix is a server-side proxy.
5. **Debug-keystore signing** — fine for side-loading, unfit for distribution.
6. **Live Guard is speakerphone-only** — direct in-call audio is a platform boundary, not a
   fixable bug.
7. **Voice depends on the handset's recogniser** and its installed language packs.

## Future Roadmap

1. Labelled Indian scam corpus — UPI, KYC, delivery, job fraud, lottery. The real bottleneck.
2. Retrain with Devanagari and transliterated Hinglish in the vocabulary.
3. Measure end-to-end precision and recall on that corpus.
4. NPU / QNN execution provider, validated and measured on iQOO hardware.
5. Server-side proxy for provider credentials.
6. Proper release signing, and R8 with a full device regression behind it.

---

**TrustLens** — *See the risk before you click.*
