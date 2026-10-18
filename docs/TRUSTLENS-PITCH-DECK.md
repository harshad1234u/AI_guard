# TrustLens

**See the risk before you click.**

An AI-powered mobile security guardian that detects and explains digital threats — before you interact with them.

iQOO Hackathon · Track: `[Track / Theme]`
Team: `[Your Team Name]` — `[Member · Member · Member]`

---

## 1. Title — The Hook

**TrustLens — See the risk before you click.**

Demo scenario shown on the title slide:

> **SMS:** "URGENT: Your bank account will be suspended today. Verify immediately: hdfc-verify-kyc.info/secure"

TrustLens response:

- **CRITICAL RISK** — Don't open this link
  - Domain impersonates HDFC Bank
  - Urgent account-suspension language
  - Requests identity verification
- `Block` · `Report`

**Blocked before the click.**

---

## 2. The Problem

### The dangerous moment is *before* the click.

```
Message arrives → Curiosity / trust → Click → Data theft · malware · financial loss
```

Most security tools operate at the last step. TrustLens works at the first.

Realistic examples:

- Fake bank alert — "your account will be suspended"
- Fake delivery notice — "pay ₹2 to reschedule"
- Phishing link — brand-lookalike domain
- QR code scam — payment QR swapped
- Fake job offer — "registration fee" upfront
- Impersonation — "this is your bank calling"

> The problem isn't "people need antivirus." Most users genuinely cannot tell legitimate content from a scam in the moment — they need **an intelligent decision layer**, right when they're about to act on something suspicious.

---

## 3. Why Existing Solutions Aren't Enough

### Detection exists. Context-aware prevention is still the opportunity.

| Capability | Traditional security | TrustLens |
|---|---|---|
| Detect suspicious links | Yes | Yes |
| Analyze message context | Often limited | Yes |
| Explain why it's risky | Often limited | Yes |
| QR / image understanding | Often limited | Yes |
| Real-time mobile interaction | Varies by product | Core design |
| On-device AI | Limited / varies | Core architecture |
| Privacy-first processing | Varies by product | Core principle |
| Personalized risk explanation | Often limited | Yes |

TrustLens combines multimodal understanding, risk reasoning, and explainability in one mobile-first security experience — not a single point feature.

---

## 4. The Solution

### Meet TrustLens — your on-device security guardian.

```
Capture → Understand → Reason → Explain → Protect
```

Seven input surfaces, one pipeline:

| Surface | What it does |
|---|---|
| **Message** | Suspicious SMS, chat or pasted text |
| **Link** | Investigate a URL without opening it |
| **QR code** | Inspected safely — never opened, never paid |
| **Camera** | Reads suspicious text through on-device OCR |
| **Voice** | Say back what you heard, get it checked |
| **Email** | Shared in from any mail app |
| **Live Guard** | Listens on speakerphone, re-scores a call as it happens |

Example output:

- **HIGH RISK** — Stop and verify independently
  - Domain resembles a known service but doesn't match it
  - Message uses urgent account-verification language
  - Requests a one-time password
- `Block` · `Investigate` · `Report`

---

## 5. The AI Brain

### AI doesn't just detect. It explains.

```
INPUT                RULE            ML CLASSIFIER      RISK ENGINE        EXPLAINABILITY     PROTECTION
text · url · qr   →  EXTRACTION   →  ONNX · CPU today →  deterministic  →  score → stated  →  warn · block ·
image · voice        on-device       capped at 30%       fusion, 0–100     reasons             report
                      fully                               score
                      explainable
```

Pure Kotlin risk engine — no IO, no randomness: identical input always yields an identical score.

Component notes:

- **Deterministic rule extraction** — URLs, UPI IDs, OTP/credential/payment requests, urgency, impersonated organisations. On-device, fully explainable, and covers patterns (e.g. Indian banking/UPI scams) a generic spam model never saw.
- **On-device ML classifier** — TF-IDF features + logistic regression, run through ONNX Runtime. Contributes at most 30% of the final score; never decides alone.
- **Risk engine** — pure, deterministic fusion of observations + external evidence into a 0–100 score across 5 bands.
- **Explainability layer** — every point on the score ties back to a stated reason.

> It never just says ~~"SCAM DETECTED."~~ It says **"High risk — because…"**

---

## 6. Why iQOO?

### Built for the smartphone. Optimized for iQOO.

| # | Benefit | Why |
|---|---|---|
| 01 | **Lower latency** | On-device inference means the warning appears before a round trip to any server could even complete. |
| 02 | **Privacy** | Message bodies, OTPs and account numbers are never transmitted — only a link's hostname, and only for an optional lookup. |
| 03 | **Offline resilience** | Rule extraction, the on-device classifier and OCR all run with zero connectivity. Cloud is enrichment, not a dependency. |
| 04 | **Real-time interaction** | Live Guard listens through a call on speakerphone and re-scores continuously — protection during the moment, not after it. |

Device capabilities in use: **Camera** (OCR & QR) · **Microphone** (Voice Check & Live Guard) · **Share sheet** (Email & text selection) · **On-device NPU** (target integration).

> The stronger the device's local AI capability, the faster and more private TrustLens becomes.

---

## 7. On-Device NPU-First Architecture

### From cloud-dependent AI → edge intelligence.

```
┌─ ON-DEVICE ───────────────────────────────────────────────┐
│  INPUT NORMALIZATION                            ● IMPLEMENTED
│  privacy filtering · feature extraction                    │
│                     │                                       │
│  DETERMINISTIC EXTRACTION + ON-DEVICE OCR       ● IMPLEMENTED
│  rules engine · bundled ML Kit text recognition             │
│                     │                                       │
│  AI INFERENCE                                                │
│   CPU · ONNX Runtime ── ● shipped                            │
│   NPU execution provider ── ◌ target iQOO integration        │
│                     │                                       │
│  RISK ENGINE                                    ● IMPLEMENTED
│  score · category · confidence · evidence                   │
│                     │                                       │
│  EXPLAINABILITY + PROTECTION LAYER              ● IMPLEMENTED
│  why it's risky → warn / block / allow / report              │
└──────────────────────┬──────────────────────────────────────┘
                        ┊ hostname only, if online
                        ▼
              ┌───────────────────────┐
              │   OPTIONAL CLOUD       │
              │   threat intelligence  │
              │   degrades gracefully  │
              │   offline              │
              └───────────────────────┘
```

Target architecture. Today the classifier runs through **ONNX Runtime on CPU**, with default session options and no execution-provider configuration — `OnnxScamClassifier` reports `InferenceBackend.CPU`, which is what actually executes.

The `InferenceBackend` enum carries NPU and GPU values so the result screen can *report* a backend honestly once one exists, but **no backend-selection policy is implemented yet**. Adding a QNN/NNAPI execution provider and validating it on iQOO hardware is roadmap work, not shipped work.

> Required wording: *"The current classifier runs through ONNX Runtime on CPU. iQOO 15 Snapdragon NPU/QNN validation is still pending target-hardware access."*

---

## 8. User Experience / Demo

### Three seconds from suspicion to explanation.

`STEP 1` Message arrives → `STEP 2` TrustLens analyzes → `STEP 3` Risk explained → `STEP 4` Action recommended

**Why?**

- Urgency-based social engineering
- Suspicious / lookalike domain
- Account-verification & OTP request
- Brand impersonation indicators

**Recommended action:** Don't open this link. `Block` · `Go back` · `Report`

Fast, simple, and understandable to a non-technical user — no security jargon.

---

## 9. What Makes TrustLens Different?

### Prevention + reasoning + device intelligence.

| # | Pillar | Description |
|---|---|---|
| 01 | **Before the click** | Intervenes before damage occurs, not after. |
| 02 | **Multimodal** | Text, links, QR, camera, voice and email — one pipeline. |
| 03 | **Explainable** | Tells users why something is risky, not just that it is. |
| 04 | **On-device first** | Local AI for speed and privacy; cloud is optional. |

> TrustLens is not another blacklist. It's an intelligent decision layer between the user and digital risk.

---

## 10. Technical Feasibility

### Designed to be buildable, testable and scalable.

| Layer | Stack |
|---|---|
| Android | Kotlin · Jetpack Compose · multi-module Gradle |
| AI runtime | ONNX Runtime · NPU-ready backend policy |
| Computer vision | On-device OCR & QR decoding, bundled |
| Risk engine | Deterministic, pure Kotlin, unit-tested |
| Threat intel | Optional, hostname-only, degrades gracefully |
| Security | On-device-first, minimal data exposure |

**MVP — implemented today:** Message · Link · QR · Camera/OCR · Voice · Email · Live Guard

**Validated today:** **98.65% held-out accuracy on the UCI SMS Spam Collection** — 5,574 real labelled SMS, 1,115 in the test split (precision 0.959, recall 0.940, F1 0.949, ROC-AUC 0.992).

This is a component-level, public-benchmark result — spam vs. ham, not "scam detection in the wild." It contributes at most 30% of the final risk score; the deterministic rules layer carries the rest, including patterns this corpus never saw.

---

## 11. Impact

### A safer internet starts at the device.

Who this protects: Students · Parents · Senior citizens · First-time UPI users · Professionals · Small businesses

**Prevent** rather than ~~Recover~~

**Target KPIs** *(targets, not achieved figures — pending field validation; see Slide 10 for the one metric already measured)*:

- Detection precision & false-positive rate
- Warning latency (time to explained verdict)
- User decision accuracy after seeing a warning
- % of threats surfaced before interaction
- On-device inference time
- Data minimization — bytes leaving the device

---

## 12. Roadmap + Closing

### From app → AI safety layer for the smartphone.

| Phase | Focus |
|---|---|
| **Phase 1 — MVP** | Message, link, QR, camera, voice, email & Live Guard — shipped |
| **Phase 2 — iQOO optimization** | NPU execution provider, latency & battery tuning |
| **Phase 3 — Proactive protection** | Notification & shared-content analysis, real-time contextual warnings |
| **Phase 4 — Intelligent mobile security layer** | Continuous local threat intelligence, personalized protection, cross-app coverage within Android's permission model |

> Every click is a decision. TrustLens helps users make the safer one.

**TrustLens**
*See the risk before you click.*
