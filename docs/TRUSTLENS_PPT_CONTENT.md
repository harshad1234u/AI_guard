# TrustLens — PPT Content Document

**Purpose:** Direct source material for the iQOO Hackathon slide deck. Every claim below is
traced to actual code, tests, or existing project documentation at commit `8b610a7` (branch
`phase16-liveguard-email`). Where a number is a demonstration example rather than a measured
production result, it is labelled as such. Nothing here should be edited to sound stronger
without re-checking the source doc it came from — the project's own convention (see
`docs/TRUSTLENS-LIMITATIONS.md` and `docs/TRUSTLENS-JUDGE-QA.md`) is to say the honest thing,
and that discipline is a genuine differentiator worth keeping in the room.

**Three claims to never make, per the project's own Judge Q&A:** that it runs on the NPU; that
TrustLens is "98.65% accurate at detecting scams" (that number is a component benchmark on a
public English spam corpus, not scam-detection accuracy); that any figure was validated on
hardware that has not actually run it (no device has run the current commit; iQOO 15 has zero
measurements).

---

# Slide 1 — Title

## Main Message
Introduce TrustLens, the team, and the one-line thesis before any detail.

## Slide Content
- **TrustLens**
- "See the risk before you click."
- Team: **Ghost Ops**
- iQOO Hackathon
- *An on-device scam investigator that explains a threat before you act on it — not just a red flag.*

## Visual Suggestion
Dark, calm background (not alarmist red). A phone silhouette with a single suspicious SMS bubble
and a small magnifying-glass icon over it — investigation, not siren.

## Speaker Notes
"TrustLens is an on-device scam investigator for Android. It doesn't just flag a message as
dangerous — it investigates it, gathers evidence, and tells you exactly why, before you click,
pay, or share anything. We're Ghost Ops, and this is TrustLens."

---

# Slide 2 — The Problem

## Main Message
The moment that matters is the ten seconds before someone clicks, pays, or reads out an OTP — and that moment is where almost nothing protects them today.

## Slide Content
- Scams now arrive through SMS, email, WhatsApp, QR codes, and phone calls — not just one channel
- Modern scams **impersonate** — a bank, a delivery company, the police, KYC compliance
- They exploit **urgency and fear**: "your account will be blocked today"
- A well-made fake is often indistinguishable from the real thing in the few seconds a person gives it
- A generic "be careful of scams" warning does not tell anyone *what to do right now*

## Visual Suggestion
A simple flow diagram: **Message arrives → curiosity/trust → click / pay / read OTP → loss.**
Below it, a real-looking (but synthetic) SMS: *"URGENT: Your SBI account will be blocked today.
Complete KYC at [link] and share your OTP, or pay Rs. 4999."*

## Speaker Notes
"Picture this: an SMS says your SBI account will be blocked today unless you complete KYC and
share an OTP. It looks urgent, it looks official, and it gives you seconds to decide. By the time
someone reports it, the OTP is already shared and the money is gone. Most tools act after that
point. The dangerous moment is before the click — and that's where almost nothing exists today."

---

# Slide 3 — Why We Chose This Problem

## Main Message
This is a real, present, mobile-first problem where prevention — not cleanup — is the only thing that actually helps.

## Slide Content
- Bank/KYC/UPI/OTP scams are a daily reality for Indian smartphone users, from students to senior citizens
- The phone is both **where the scam arrives** and **where the sensitive data lives** — SMS, calls, UPI apps, camera
- Prevention has to happen **before** the click/pay/share — after that, recovery is hard or impossible
- AI is well suited here: scam wording follows patterns, and reasoning over context (a link, a request, an identity claim) is what a security system needs to do
- For a mobile-hardware hackathon: this is a problem that lives entirely on-device — camera, mic, share sheet, local AI — making it a natural fit for showcasing device intelligence

## Visual Suggestion
Icon row: phone with SMS bubble, UPI icon, camera, microphone — all converging into one shield icon.

## Speaker Notes
"We picked this because it's not hypothetical — it's the scam message sitting in someone's inbox
right now. It's inherently a phone problem: the message, the QR code, the call, all arrive and
live on the device. And it's a problem where AI genuinely helps, because scam wording and
structure follow patterns a model and a rule engine can both learn to recognise — as long as the
final decision stays explainable to the person holding the phone."

---

# Slide 4 — Existing Approach / The Gap

## Main Message
Most scam detectors stop at a label; a label alone doesn't help someone make a decision under pressure.

## Slide Content
- Conventional approach: classify a message or link as **"Scam" / "Not Scam"**
- That answers *what*, but not:
  - **Why** is this dangerous?
  - **What evidence** actually supports the warning?
  - **What exactly should I do right now?**
- A bare red flag, with no reasoning, is easy to dismiss under urgency — which is exactly the state a scam puts someone in

## Visual Suggestion
Side-by-side: a plain red "⚠ SCAM" badge with nothing else, next to a TrustLens-style card showing
a score, a reasons list, and a recommended action. Contrast the emptiness of one against the other.

## Speaker Notes
"Most scam detectors are binary classifiers wearing a UI — scam or not scam. But a scared user
staring at a KYC threat doesn't need a label, they need to know why it's dangerous and what to do
in the next ten seconds. That gap between detection and decision-support is exactly what
TrustLens is built to close."

---

# Slide 5 — Our Solution: TrustLens

## Main Message
TrustLens is a phone-first system that investigates a suspicious input and hands the user an explained verdict, not just a label.

## Slide Content
- **TrustLens is a phone-first AI security system that detects, investigates, explains, and recommends safe action — before the user acts.**
- **DETECT → INVESTIGATE → EXPLAIN → PROTECT**
- Runs on-device: SMS, links, QR codes, camera/OCR, voice, and email all converge on one investigation pipeline
- Every risk point is traced back to a stated reason — not a black-box score

## Visual Suggestion
Four-stage horizontal pipeline (Detect → Investigate → Explain → Protect), each stage with a
one-line label. Keep it clean — this is the slide judges will remember the shape of.

## Speaker Notes
"TrustLens doesn't stop at detection. It detects a possible signal, investigates the actual
content — what is it asking for, what does the link look like — explains the risk in plain
language, and recommends a safe action. All of that happens before the user has clicked, paid,
or shared anything."

---

# Slide 6 — How TrustLens Works

## Main Message
One pipeline turns any suspicious input into an evidence-backed, explained risk verdict.

## Slide Content
```
User Input (message, link, QR, photo, voice, email)
        ↓
ML Detection — on-device classifier, an initial signal (capped, not final)
        ↓
Security Indicator Extraction — what is it asking for? what does the link look like?
        ↓
Threat Intelligence (optional) — has this host been reported before?
        ↓
Evidence — every observation and every external report, individually attributable
        ↓
Risk Engine — deterministic scoring, 0–100
        ↓
Risk Score + Explanation + Safe Action
```

## Visual Suggestion
Vertical pipeline diagram matching the stages above, each box labelled with the real module name
in small text underneath (e.g. "AI module — OnnxScamClassifier", "Investigation module —
RuleBasedIndicatorExtractor", "RiskEngine").

## Speaker Notes
"Whatever comes in — a pasted message, a photographed poster, a scanned QR, a shared email — it
all funnels through the same pipeline. First, an on-device model gives an initial read. Then a
rule-based investigator extracts what the message is actually asking for and inspects the link's
structure. If it's online and configured, an optional lookup checks whether the host has a bad
reputation elsewhere. All of that becomes evidence. A deterministic risk engine turns that
evidence into a score, an explanation, and a recommended action."

## Technical Depth
This is implemented as a single convergence point, `AnalyzeTextUseCase` (in `:domain`):
extractor → URL analyser → classifier (skipped for a bare link or zero-vocabulary text) →
optional threat-intel lookup → `RiskEngine.assess()`. The same use case backs every input
surface, so a highlighted line of text and a pasted paragraph reach an identical verdict for
identical content.

---

# Slide 7 — Input Types

## Main Message
Eight ways to bring a suspicious item to TrustLens, all converging on the same investigation.

## Slide Content
| Surface | What it does | Status |
|---|---|---|
| **Message / SMS / chat** | Paste or type suspicious text | Implemented |
| **Link (URL)** | Investigated by structure — never opened | Implemented |
| **QR code** | Decoded and investigated — never opened, never paid | Implemented |
| **Camera / screenshot (OCR)** | Reads text off a photo or screen, on-device | Implemented |
| **Voice** | Say back what a caller said; gets checked | Implemented |
| **Email** | Shared in from any mail app — no inbox access | Implemented |
| **Live Guard** | Listens on speakerphone, re-scores a call as it develops | Implemented |
| **Selected text** | Check one highlighted line from inside another app | Implemented |

## Visual Suggestion
An 8-icon grid (SMS, link, QR, camera, mic, mail, phone-call waveform, text-selection cursor) all
pointing inward to a single funnel icon.

## Speaker Notes
"All eight of these are actually built and wired end-to-end — not mockups. Whether it's a pasted
message, a photographed scam poster, a scanned QR, or a call on speakerphone, it reaches the same
investigation pipeline and the same kind of explained answer."

## Technical Depth
Each surface is its own feature module (`:feature:textcheck`, `:urlcheck`, `:qrscan`,
`:lensscan`, `:voicecheck`, `:emailshare`) plus manifest entry points for Email
(`ACTION_SEND`) and selected text (`ACTION_PROCESS_TEXT`). All eight are software-verified
(JVM/unit tests); Live Guard, Email, selected text, and the multilingual layer have **not yet been
re-validated on a physical device on this exact commit** — see Slide 19 for the honest status.

---

# Slide 8 — The ML Layer

## Main Message
An on-device model gives a fast first read on wording — but it's one signal among several, never the final word.

## Slide Content — general audience
- A small AI model reads the *wording* of a message and estimates how "scam-like" it sounds
- It runs entirely on the phone — no internet needed, no message ever leaves the device for this step
- It contributes to the final decision, but it is capped — it can never decide alone
- If it isn't confident it can assess something (e.g. a bare link with no text), it says so instead of guessing

## Visual Suggestion
Simple box: "Message text →  [ On-device model ]  → scam-likelihood score (capped contribution)".
Show the cap visually — e.g. a gauge that only fills to 30% of the dial for this signal.

## Speaker Notes
"This model looks at how a message is worded — the phrasing, the pressure, the structure — and
gives an initial signal purely from the wording, on the device, with no network call. But it's
only one voice in the room. It can contribute at most 30 of the final 100 points, and the
remaining points come from what the message is actually asking for and what the link looks like."

## Technical Depth
- Pipeline: **TF-IDF vectoriser** (unigram + bigram, ~12,007 vocabulary terms) → **logistic
  regression** (`C=4.0`, `liblinear`, class-weight balanced) → exported to **ONNX**.
- Model version string in code: `uci-sms-tfidf-logreg-v1`. Roughly 120 KB.
- **Trained on the UCI SMS Spam Collection** — 5,574 real, labelled UK-English SMS from ~2011,
  spam vs. ham. It has never seen a UPI handle, an Indian bank name, or a KYC scam — a domain
  mismatch the project is explicit about.
- **Held-out benchmark:** 98.65% accuracy, precision 0.959, recall 0.940, F1 0.949, ROC-AUC 0.992
  on 1,115 test messages. **This is a component-level benchmark on a public English spam corpus —
  not TrustLens's scam-detection accuracy, and the project's own documentation insists it never be
  quoted as one.**
- **Inference:** runs on-device via **ONNX Runtime**, execution provider = **CPU** (default
  session options, no NPU/NNAPI/QNN configuration exists in the codebase today). The classifier
  reports the backend that actually executed it.
- **TF-IDF is reimplemented in Kotlin**, not run through ONNX — only the logistic-regression graph
  is ONNX. A parity test holds the maximum probability delta against the original Python model at
  1.2 × 10⁻⁷ with zero label disagreements across all 1,115 test messages.
- **Output:** either a scam-probability signal, or an explicit "unavailable" result for two known
  cases (a bare link with no surrounding text; text with zero vocabulary coverage, e.g. Hindi) —
  deliberately never reported as a false "0.0".
- **Contribution cap:** capped at 30 of 100 points in the risk engine — it is one signal, not the
  verdict.

---

# Slide 9 — AI Investigation Agent

## Main Message
Where the model gives an initial read on wording, the investigation layer digs into what the message is actually asking for.

## Slide Content
- Analogy: **"The ML model is the first security checkpoint; the investigation layer is the digital investigator."**
- Extracts concrete evidence from the message and any link it contains:
  - URLs and domains
  - Email addresses
  - **UPI IDs**
  - **Phone numbers**
  - **Payment amounts**
  - Organisation names (e.g. a claimed bank)
  - Requests for **OTP / credentials / payment**
  - **Urgency** and **account-threat** language
  - Whether an organisation is being **impersonated**
- Also inspects any link's *structure* — without opening it: brand/domain mismatch, look-alike or free-registration domains, disguised characters, embedded logins, shortened links

## Visual Suggestion
A message getting "scanned" by a magnifying glass, with small tags popping out for each thing
found: "UPI ID found", "OTP request", "urgency language", "SBI impersonation".

## Speaker Notes
"Think of the model as the first checkpoint — it gives a quick read on the wording. The
investigation layer is the actual detective work: it pulls out what the message is asking for —
a UPI ID, a phone number, an OTP — and it inspects the link itself, character by character,
without ever opening it. That's the evidence the risk engine reasons over."

## Technical Depth
Real, verified classes: `RuleBasedIndicatorExtractor` (regex/string extraction of URLs, domains,
emails, UPI IDs, phone numbers, amounts, organisation names, plus urgency/account-threat/OTP/
credential/payment-request flags and derived "impersonated organisation"), wrapped by
`MultilingualIndicatorExtractor` (a decorator that only *adds* Hindi/Hinglish/Indian-English
patterns — English scoring is provably unchanged, pinned by a parity test), plus
`DeterministicUrlAnalyzer` (structural link analysis: punycode, IP-literal hosts, embedded
credentials, executable download paths, deep subdomains, non-standard ports, shorteners,
brand/domain mismatch, free-registration TLDs), `DeterministicQrPayloadClassifier`, and
`DeterministicEmailParser`. **This is deterministic, rule-based extraction — there is no LLM, no
autonomous agent, and no prompt anywhere in this pipeline.** Message content is always treated as
data, never as an instruction to any model.

---

# Slide 10 — Risk Engine

## Main Message
A deterministic engine combines every piece of evidence into one explainable score — no black box.

## Slide Content
- Combines: ML signal (capped) + extracted indicators + link structure + optional threat-intel corroboration
- Produces a score from **0–100**, mapped to five bands:

| Band | Score | Action |
|---|---|---|
| LOW | 0–19 | Proceed carefully |
| GUARDED | 20–39 | Verify before acting |
| MEDIUM | 40–60 | Do not act until verified |
| HIGH | 61–80 | Stop and verify independently |
| CRITICAL | 81–100 | Do not open, pay, or share sensitive information |

- Example (a verified test case, not a live measurement): a bank-impersonation KYC SMS scores **81/100 — CRITICAL** from deterministic reasons alone, *before* the ML signal is even added
- Every point on the score ties back to one stated reason — nothing is unexplained

## Visual Suggestion
A stacked bar showing how points accumulate: urgency, OTP request, payment request, account
threat, org impersonation, link/domain mismatch — each a labelled segment building up to the
total.

## Speaker Notes
"The risk engine is pure math over evidence — no randomness, no hidden weighting. The same input
always produces the same score. Every single point is traceable to a plain-language reason, so a
user — or a judge — can see exactly why a message was scored the way it was."

## Technical Depth
`RiskEngine` (in `:riskengine`) is pure Kotlin with no IO, no coroutines, and no randomness —
deterministic by construction. Score = `observationPoints + evidencePoints`, clamped to [0, 100].
Every weight lives in one file, `RiskWeights.kt`. External threat-intel evidence uses
**diminishing corroboration** — the first source to report a host counts most, each further
agreeing source counts less. `UNKNOWN` / `NO_DETECTION` / `ERROR` verdicts always contribute
exactly zero and never lower a score. `SafeAction` has exactly seven possible outputs, and every
one is a "don't" or "verify" — the engine has no capability to open, pay, send, or install
anything.

---

# Slide 11 — Case Study: A Fake Bank KYC SMS

## Main Message
Walking one real, reproducible example end-to-end shows the full TrustLens workflow in under a minute.

## Slide Content
**1. Message received** *(synthetic demo input, from the project's own demo pack)*:
> "URGENT: Your SBI account will be blocked today. Complete KYC at
> `https://sbi-kyc.example.com/verify` and share your OTP, or pay Rs. 4999."

**2. ML detection** — the on-device model reads the wording and contributes an initial signal (capped at 30 points)

**3. Indicators extracted** — urgency language · OTP request · payment request · account-suspension threat · claimed identity "SBI"

**4. Link investigated** — the message claims **SBI**, but the link points to `example.com` — a brand/domain mismatch

**5. Evidence assembled:**
| Reason | Points |
|---|---|
| Creates urgency / time pressure | +10 |
| Requests an OTP or PIN | +22 |
| Requests a payment | +15 |
| Threatens account suspension | +10 |
| Claims to be SBI | +12 |
| Message claims SBI, link goes elsewhere | +12 |
| **Deterministic total** | **81** |

**6. Risk score:** **81/100 — CRITICAL** (from deterministic evidence alone; ML adds on top, capped at 100) — *this exact breakdown is asserted in the project's automated test suite*

**7. Explanation:** shown as the reason list above, in plain language

**8. Recommended action:** Do not open the link · Do not pay · Do not share the OTP · Verify through SBI's official app or helpline

## Visual Suggestion
A left-to-right filmstrip of 4 phone-screen mockups: (1) the SMS as received, (2) a "scanning"
state, (3) the reasons list building up, (4) the final CRITICAL card with recommended actions.

## Speaker Notes
"This is a synthetic example, but the scoring behind it is real and test-verified, not
illustrative. The message threatens account suspension, asks for an OTP, asks for a payment, and
claims to be SBI while linking somewhere else entirely. Every one of those is independently
detected and weighted. The total — 81, CRITICAL — comes purely from what the message demands,
before the AI model even adds its signal. That's the point: even if the model got it wrong, the
deterministic evidence alone already caught this."

## Technical Depth
Source: `docs/demo-assets/A-scam-sms.txt`. The deterministic reason-sum (81 → CRITICAL band) is
exact and asserted in the calibration/integration tests; the same scenario measured 100/100
CRITICAL in the project's JVM calibration harness once the (mocked, for JVM testing) ML signal is
added on top. This scenario has not yet been re-run on a physical handset on the current commit
(see Slide 19) — the deterministic logic itself is what's verified here, not a live on-device
run.

---

# Slide 12 — User Experience

## Main Message
The user sees a risk level, a score, the reasons why, and what to do — nothing more, nothing cryptic.

## Slide Content
- **Risk level** — LOW / GUARDED / MEDIUM / HIGH / CRITICAL, in plain words and colour
- **Risk score** — 0–100, for anyone who wants the number
- **Why** — a short list of plain-language reasons, not jargon
- **Recommended action** — a clear next step: verify, don't pay, don't share the OTP, don't open the link
- No security jargon, no unexplained percentages, no dead-end "scam detected" banner

## Visual Suggestion
A single result-screen mockup: a coloured risk band at top, the score, a bulleted "Why" list
below it, and one or two action buttons/labels at the bottom (e.g. "Verify via official app",
"Don't share OTP").

## Speaker Notes
"This is what the person actually sees — not a model output, not a confidence interval. A risk
level in plain words, the reasons behind it in plain language, and a concrete next step. Someone
who has never heard of TF-IDF or ONNX can still act correctly, because the explanation is written
for them, not for us."

---

# Slide 13 — Privacy / On-Device Architecture

## Main Message
Nearly everything runs entirely on the phone; the only thing that can leave the device is a bare hostname, and only when a lookup is configured and online.

## Slide Content
- **Fully offline-capable core**: rule extraction, link-structure analysis, the on-device model, OCR, and QR decoding all run with **zero network access**
- **The message body never leaves the device** — no OTP, no account number, no name, no amount is ever transmitted
- **Optional external check**: if configured and online, only a link's **hostname** (never the full path or query string) — or an email's **sender domain** (never the local part or body) — may be sent to a reputation lookup
- If that lookup is unavailable, offline, or unconfigured, it's reported as **unavailable** on screen — never silently treated as "safe"
- **No outward actions anywhere in the app**: it never opens a link, launches a browser/WebView, executes a process, or takes any automatic action

## Visual Suggestion
A phone outline with almost everything happening inside it (rules, model, OCR, QR, camera, mic),
and one thin dashed line leaving the phone labelled "hostname only, optional, if online" pointing
to a small cloud icon.

## Speaker Notes
"We do not claim nothing is ever transmitted — that would be dishonest for the URL-reputation
feature. What we can say precisely: the message body, OTPs, account numbers, and names never
leave the device. The only thing that can ever leave, and only if you've configured it and you're
online, is a bare hostname — the attacker's infrastructure, not your data."

## Technical Depth
The only networking module in the entire codebase is `:threatintel` (OkHttp, real VirusTotal and
URLhaus adapters). Every other module — `:domain`, `:investigation`, `:riskengine`, `:ai` — has no
network dependency at all. `AnalyzeTextUseCase.lookupSubjects()` reduces any lookup to a
deduplicated set of hostnames before it ever reaches the network layer; paths and query strings
are dropped deliberately, since a phishing URL's path often carries a per-victim token. The threat
intel lookup is optional at build time (works with "unconfigured" reporting if no API key is
present) and at runtime (offline/timeout/rate-limit all map to zero contribution, never a negative
one).

---

# Slide 14 — iQOO Device / Hardware Innovation

## Main Message
TrustLens is built around a phone's native capabilities — camera, mic, share sheet, and on-device compute — with NPU acceleration as a clearly-labelled next step, not a current claim.

## Slide Content
**Implemented / validated today:**
- On-device model inference (ONNX Runtime, CPU)
- Camera-based OCR (ML Kit text recognition)
- QR code scanning and decoding
- Voice input via the device's speech recogniser
- Live, continuous re-scoring of a call on speakerphone ("Live Guard")
- Share-sheet and text-selection integration (Email, "check this line" from any app)

**Current development plan (not yet built or measured):**
- NPU / QNN execution provider, targeting the iQOO 15's on-device AI acceleration
- Real-device validation on iQOO 15 hardware (currently unavailable to the team)
- Direct in-call audio (currently speakerphone-only — an Android platform restriction, not a design choice)

## Visual Suggestion
Two clearly separated columns under a phone illustration — "Today" (camera, mic, on-device CPU
model, share sheet — all lit up) vs. "Next" (an NPU chip icon, greyed out / outlined, labelled
"iQOO 15 target").

## Speaker Notes
"Everything on the left is real and running today: the camera doing OCR, the mic powering Voice
Check and Live Guard, the model running entirely on-device. What's not built yet, and we want to
be upfront about this, is NPU acceleration — that's squarely the next step, and it's exactly the
kind of thing an iQOO device is built to showcase."

## Technical Depth
Grepping the codebase confirms there is no NNAPI, QNN, or execution-provider configuration
anywhere — `OnnxScamClassifier` hardcodes `InferenceBackend.CPU` and the ONNX session is created
with default (unconfigured) session options. The `InferenceBackend` enum already includes NPU and
GPU values so the result screen can report a backend honestly once one is selected — but no
backend-selection policy exists yet. This is a deliberate, documented gap, not an oversight.

---

# Slide 15 — Technical Architecture

## Main Message
A clean, modular architecture separates concerns: UI, deterministic investigation, ML, and a pure risk engine each live in their own module.

## Slide Content
```
:app              Compose UI, Hilt wiring, entry points (SEND / PROCESS_TEXT intents)
:feature:*        home · textcheck · urlcheck · lensscan · qrscan ·
                  voicecheck · emailshare · result   (one screen + ViewModel each)
:core             design system — tokens, theme, shared components
:domain           pure Kotlin — models, AnalyzeTextUseCase, AnalyzeEmailUseCase
:investigation    pure JVM — RuleBasedIndicatorExtractor, MultilingualIndicatorExtractor,
                  DeterministicUrlAnalyzer, DeterministicQrPayloadClassifier,
                  DeterministicEmailParser
:ai               OnnxScamClassifier, TfidfVectoriser, MockScamClassifier,
                  MlKitTextRecogniser (OCR)
:riskengine       pure JVM — RiskEngine, RiskWeights → depends on :domain only
:threatintel      OkHttpTransport — VirusTotal + URLhaus adapters (hostname-only)
```

## Visual Suggestion
A layered box diagram: UI layer on top, domain/use-cases in the middle, and
investigation/ai/riskengine/threatintel as four parallel boxes feeding into the domain layer from
below. Arrows only point one way — no circular dependencies.

## Speaker Notes
"This is a real multi-module Gradle project, not a monolith. The risk engine and the investigation
layer are pure Kotlin — no Android dependency at all — so they're fast to test and impossible to
accidentally couple to UI or network code. The AI module is isolated behind a small interface, so
swapping in a stronger model later doesn't touch anything else."

## Technical Depth
`:riskengine` imports only `com.trustlens.domain.*` — verified by reading its source — no
dependency on `:ai` or `:threatintel`. This means the scoring logic can be reasoned about, tested,
and audited entirely independently of the model or the network layer. `MockScamClassifier` exists
specifically so `:riskengine` and `:investigation` tests never require ONNX Runtime or a real
model to run.

---

# Slide 16 — Why This Architecture

## Main Message
Each layer does the one thing it's best at — fast pattern recognition, rigorous investigation, and predictable, explainable scoring — instead of asking one model to do everything.

## Slide Content
- **ML** → fast, generalising detection of scam-like *wording* — but it's one signal, on one training corpus
- **Investigation layer** → rigorous, rule-based extraction of what a message actually *asks for*, and link structure — fully explainable, covers patterns the model never saw
- **Deterministic risk engine** → consistency and auditability: same input, same score, every weight visible in one file
- **On-device processing** → privacy (message never leaves the phone) and responsiveness (no network round-trip required)
- **Threat intelligence** → adds real-world evidence a local model or ruleset simply cannot know
- **Why not a cloud LLM as the sole decision-maker?** A verdict a user can't interrogate is a verdict they can't act on. An LLM in the scoring path would mean the final score is opaque, non-reproducible across model versions, and dependent on the message content being trusted as an instruction rather than treated as data.

## Visual Suggestion
Four labelled pillars (ML / Investigation / Risk Engine / On-device) each holding up one word:
Speed / Evidence / Consistency / Privacy.

## Speaker Notes
"We deliberately didn't build a single model that decides everything. Wording alone is a weak
signal on its own — it's trained on one corpus and doesn't generalise perfectly. What generalises
is a rigorous set of deterministic checks: does this message ask for an OTP, does the link's
domain match the brand it claims. That's auditable and explainable in a way a model output isn't.
The model earns a seat at the table, but it never gets the final word."

## Technical Depth
This maps directly to the project's own stated design principle: message content is always
carried as an opaque value and never interpolated into any model's instruction context — there is
no prompt template, no LLM call, and no autonomous agent anywhere in the scoring path. The
classifier's maximum contribution is hard-capped in `RiskWeights` at 30 of 100 points.

---

# Slide 17 — Key Innovation / Differentiator

## Main Message
Most tools stop at a label; TrustLens investigates the threat and hands over evidence before the user has to decide anything.

## Slide Content
**Traditional approach:**
> DETECT → ALERT

**TrustLens:**
> DETECT → INVESTIGATE → COLLECT EVIDENCE → SCORE → EXPLAIN → RECOMMEND ACTION

> **"Most scam detectors stop at detection. TrustLens investigates the threat before the user acts."**

## Visual Suggestion
Two horizontal timelines stacked: a short 2-step traditional one on top, a longer 6-step
TrustLens one below it, both ending at the same "user decides" point — but only the bottom one
arrives with evidence in hand.

## Speaker Notes
"This is the single sentence that captures the whole project. A traditional detector tells you
something is wrong. TrustLens tells you what it found, why it matters, and what to do about it —
before you've acted."

---

# Slide 18 — Benefits

## Main Message
An explained, on-device verdict helps the widest range of users — especially the ones most exposed and least equipped to spot a scam alone.

## Slide Content
- **Everyday / first-time UPI users** — a second opinion before a payment
- **Elderly / non-technical users** — plain-language reasons, not jargon or a lone red flag
- **Mobile-first users** — works entirely on the phone where the scam actually arrives
- **Faster decisions** — a full explanation in seconds, not a research session
- **Explainability** — a reason a person can act on, not just a score to trust or distrust blindly
- **Privacy** — the message itself never leaves the device
- **Prevention over recovery** — intervenes before the click, payment, or OTP share — not after

## Visual Suggestion
A row of simple user personas (student, parent, senior citizen, small-business owner) each with a
small phone-with-shield icon.

## Speaker Notes
"This isn't built for security experts — it's built for the person who has ten seconds and a
convincing-looking KYC message. Plain-language reasons and a clear action matter most for exactly
the users who are usually targeted hardest: first-time UPI users and older relatives who don't
have a security background to fall back on."

---

# Slide 19 — Current Project Status

## Main Message
The software is thoroughly built and tested; the one honest gap is that the current build has not yet run on a physical device.

## Slide Content
**Implemented and software-verified (at commit `8b610a7`):**
- All 8 input surfaces (Message, Link, QR, Camera/OCR, Voice, Email, Live Guard, selected text) wired end-to-end
- On-device ML classifier exported to ONNX, running on-device via ONNX Runtime
- Deterministic investigation layer (indicators, URL structure, multilingual Hindi/Hinglish)
- Deterministic risk engine, 5-band scoring, 7 safe actions
- Optional threat-intelligence corroboration (VirusTotal + URLhaus)
- **606 automated tests: 602 passing, 0 failures, 0 errors, 4 skipped** (skipped = opt-in live-network tests)
- Release build compiles clean — APK ~61.3 MiB, `arm64-v8a`

**Device-validated (earlier build, interim hardware):**
- An earlier build (v4.0-era) was validated on a **POCO X6 5G** — used only as an interim stand-in for the target iQOO hardware, which the team does not yet have. Covered Camera/OCR, QR, Voice, and URL flows with real, measured on-device latencies.

**Not yet done:**
- **No physical device has run the current exact commit** — Live Guard, Check Email, selected text, and the multilingual layer are software-tested but not yet re-confirmed on real hardware on this build
- **No iQOO device measurement exists yet** — the team does not yet have access to the target hardware
- No end-to-end scam-detection accuracy figure exists (only a component-level ML benchmark — see Slide 8)

## Visual Suggestion
A simple three-column status board: "Built & tested" (green, long list) / "Validated on interim
hardware" (amber, POCO X6) / "Pending" (grey, iQOO 15 + current-commit device run).

## Speaker Notes
"We want to be completely upfront here, because it's the one honest gap in an otherwise
thoroughly tested project: 606 automated tests pass, the release build is clean, and every
feature works in software. But no phone has run this exact build yet, and we have not had access
to an iQOO device to measure on. Everything we've said about latency and device behaviour so far
comes from an earlier build on a different phone, used only as a stand-in."

---

# Slide 20 — Current Development Plan / Product Roadmap

## Main Message
The next phase of TrustLens is squarely about real-hardware validation and closing the one real data gap — not new, unrelated features.

## Slide Content
**Current Development Plan:**
1. **iQOO 15 real-device validation** — the team's next and most important step
2. **NPU / QNN execution provider**, validated and measured on iQOO hardware
3. **Build a labelled Indian scam corpus** (UPI, KYC, delivery, job-fraud, lottery scams) — the real bottleneck to a genuine accuracy figure
4. **Retrain the classifier** with Hindi (Devanagari) and Hinglish vocabulary, so multilingual detection gets a model signal, not rules alone
5. **Measure true end-to-end precision/recall** once that corpus exists
6. **Server-side proxy** for threat-intelligence provider credentials
7. **Production-grade release signing** and a full R8/device regression pass
8. **Full device validation matrix** across every input surface on target hardware

## Visual Suggestion
A short numbered roadmap track, not a "wishlist" cloud — present it as a sequenced execution plan
with the iQOO 15 validation step visually first/largest.

## Speaker Notes
"This isn't a list of features we haven't gotten to — it's a deliberate, sequenced plan. The
single most important next step is getting this exact build onto an iQOO 15 and measuring it for
real, including NPU acceleration. In parallel, the real long-term unlock is a labelled Indian scam
dataset — that's what turns 'capped ML signal' into a model that actually understands UPI and KYC
scams natively."

---

# Slide 21 — Hackathon Value

## Main Message
TrustLens demonstrates a real problem, a working on-device AI pipeline, and an explainable security product — all in one demoable build.

## Slide Content
- **Real-world problem** — bank/KYC/UPI scams affect people every day, not a hypothetical
- **Mobile-first** — camera, mic, share sheet, and on-device compute are the product, not add-ons
- **AI/ML + deterministic reasoning**, working together, not either/or
- **On-device intelligence** — the core pipeline runs fully offline
- **NPU opportunity** — a clear, credible next step that plays directly to iQOO's hardware strengths
- **Explainability** — every score traces to a stated reason, not a black box
- **Practical UX** — a plain-language verdict a non-technical user can act on
- **Honest engineering** — 606 tests, documented limitations, no overclaiming
- **Clear, demoable workflow** — a scam SMS in, an explained CRITICAL verdict out, in seconds
- **A real direction to scale** — from scam detector toward a broader on-device mobile security layer

## Visual Suggestion
A checklist-style slide — each point with a small checkmark icon, ending on the NPU-opportunity
point highlighted as "what an iQOO device unlocks next."

## Speaker Notes
"For this specific hackathon, we think TrustLens hits everything the brief is looking for: a real
problem, real on-device AI, a genuinely explainable product, and a clean, obvious next step —
NPU acceleration — that's exactly what an iQOO device is built to prove out."

---

# Slide 22 — Demo Flow

## Main Message
A short, reliable, five-minute demo shows the full pipeline on two reproducible examples.

## Slide Content
1. **User receives a suspicious message** — the SBI KYC scam SMS (Slide 11)
2. **TrustLens analyzes it** — paste it into Check Message
3. **ML produces an initial signal** — shown but visibly capped
4. **Indicators are extracted** — urgency, OTP request, payment request, impersonated organisation
5. **Investigation occurs** — the link's domain is checked against the claimed brand
6. **Risk score appears** — 81+/100, CRITICAL
7. **"Why" section explains the evidence** — the exact reason list, in plain language
8. **Safe action is recommended** — don't open, don't pay, don't share the OTP, verify independently

**Bonus, exactly-reproducible pair (no model involved, so it never varies):**
- `https://www.sbi.co.in` (the real bank) → **0/100, LOW**
- `sbi.bank.tk/verify` (impersonating it) → **22/100, GUARDED**

## Visual Suggestion
A numbered storyboard strip matching the 8 steps, with the two reproducible link scores called out
in a small "always works, no matter what" box in the corner — useful as a fallback if anything
else in the live demo misbehaves.

## Speaker Notes
"We'll walk through one scam SMS end-to-end, live. And as a safety net, we also have two link
examples that don't depend on the model at all and are guaranteed to reproduce exactly — the real
SBI domain scoring 0, and a look-alike phishing domain scoring 22 — so even if something about the
live environment misbehaves, we can still show the core reasoning working."

---

# Slide 23 — Competitive Differentiation

## Main Message
The difference isn't detection — most tools already do that. It's what happens after detection.

## Slide Content
| Capability | Traditional Scam Detector | TrustLens |
|---|---|---|
| Detection | Yes | Yes |
| Investigation of *what's being asked* | Usually no | Yes — OTP, payment, credentials, urgency |
| Evidence collection | Usually no | Yes — every observation is individually attributable |
| Explainability | Often a bare label | Every point traces to a stated reason |
| Risk scoring | Binary (scam / not scam) | Graded 0–100, five bands |
| Recommended action | Often none, or generic | Specific: don't pay, don't share OTP, verify independently |
| On-device capability | Varies | Core architecture — fully offline-capable |
| Mobile-first design | Varies | Built around camera, mic, share sheet from day one |

*(No named third-party products are claimed as comparisons here — this reflects the general shape of conventional binary scam classifiers, not a specific competitor.)*

## Visual Suggestion
A clean two-column comparison table, TrustLens column visually distinguished (e.g. highlighted
background), each row a single crisp phrase.

## Speaker Notes
"We're not claiming to be the first system that can tell a message is suspicious — plenty of
tools do that. What's different is everything after that first flag: the evidence, the
explanation, and the specific action. That's the part most scam detectors skip."

---

# Slide 24 — Vision

## Main Message
TrustLens should grow from a scam detector into a proactive personal mobile security agent.

## Slide Content
> TrustLens should evolve from a scam detector into a **proactive personal mobile security agent**
> that helps users identify and understand threats — before those threats cause financial or
> personal harm.

- Today: investigate what's shown to it — a pasted message, a scanned QR, a call on speakerphone
- Tomorrow: a broader, continuously-improving on-device layer of judgment — still explainable, still privacy-first, still never acting on the user's behalf without them deciding

## Visual Suggestion
A simple growth arrow from a small phone-with-shield icon to a larger, more integrated
phone-as-guardian illustration — restrained, not sci-fi.

## Speaker Notes
"We see TrustLens's endpoint not as 'one more scam-checking app,' but as a personal security
layer that lives on the phone and helps a person reason through a threat in the moment — always
explaining itself, never acting without them."

---

# Slide 25 — Final Slide

## Main Message
Close on the core thesis and the tagline, cleanly.

## Slide Content
> "Don't just detect the scam.
> Understand the threat before you act."

**TRUSTLENS**
*"See the risk before you click."*

Team: Ghost Ops — iQOO Hackathon

## Visual Suggestion
Minimal — dark background, the closing line, then the TrustLens wordmark and tagline, nothing
else on screen.

## Speaker Notes
"Don't just detect the scam — understand the threat before you act. That's TrustLens. Thank you."

---

# MASTER PROJECT SUMMARY

## One-line description
TrustLens is an on-device Android scam investigator that turns a suspicious message, link, QR
code, photo, voice input, or email into an explained, evidence-backed risk verdict — before the
user clicks, pays, or shares anything.

## 30-second elevator pitch
"TrustLens is an on-device scam investigator for Android. Instead of just flagging a message as
'scam' or 'not scam,' it extracts what the message is actually asking for — an OTP, a payment, a
suspicious link — investigates the link's structure, and produces a plain-language explanation
with a specific recommended action, all running mostly offline on the phone. Most scam detectors
stop at detection; TrustLens investigates the threat before the user acts."

## 60-second pitch
"Every day, people get messages like 'Your SBI account will be blocked today — complete KYC and
share your OTP.' By the time it's reported, the money's gone. TrustLens is built for that ten
seconds of decision. Paste the message, scan the QR, photograph the poster, or share the email in,
and it runs the same pipeline every time: an on-device AI model gives an initial read on the
wording, capped so it never decides alone; a deterministic investigation layer pulls out what's
actually being asked for and inspects any link's structure without opening it; an optional
lookup adds real-world reputation evidence; and a pure, auditable risk engine turns all of it into
a score from 0 to 100, a plain-language explanation, and a specific safe action — never a bare
red flag. It's mobile-first by design: camera, microphone, and share-sheet integration are core
to the product, not bolted on. And it's honest about where it stands — 606 automated tests
passing, a clean release build, and a clearly labelled next step: real validation on iQOO
hardware, including NPU acceleration, which hasn't happened yet."

## 3-minute explanation
"The problem TrustLens solves is the moment right before someone acts on a scam — clicks a link,
pays a fake fee, or reads out an OTP. Conventional scam detectors are binary classifiers: scam or
not scam. That doesn't tell a scared or hurried user *why* it's dangerous or *what to do*, and
under urgency, a bare red flag is easy to dismiss.

TrustLens instead runs every input — a pasted message, a scanned QR, a photographed poster, a
call on speakerphone, a shared email, even one highlighted line from inside another app — through
one investigation pipeline. First, a small on-device machine-learning model (TF-IDF features into
a logistic regression, exported to ONNX and run entirely on the phone via ONNX Runtime) reads the
wording and gives an initial signal. That model is real — it holds a 98.65% accuracy benchmark on
a public English SMS-spam dataset — but it's also honestly limited: that benchmark is a
component-level result on UK-English spam, not a measurement of TrustLens's real-world
scam-detection accuracy, and the model is capped at contributing at most 30 of the final 100
points precisely because it's one imperfect signal.

The heavier lifting is done by a deterministic investigation layer: rule-based extraction that
identifies UPI IDs, phone numbers, payment amounts, organisation names, and requests for an OTP,
credentials, or payment, plus structural analysis of any link — domain/brand mismatches,
look-alike or free-registration domains, disguised characters — all without ever opening the
link. If online and configured, an optional threat-intelligence lookup adds corroboration from
VirusTotal and URLhaus, using only a bare hostname, never a message body or the full URL path.

All of that evidence feeds a pure, deterministic risk engine — no randomness, no hidden weighting
— that produces a 0–100 score across five bands, from LOW to CRITICAL, with every point traceable
to a plain-language reason, and a specific recommended action drawn from a fixed set of seven,
all of which are 'don't' or 'verify' — the app never opens, pays, sends, or installs anything on
the user's behalf.

The project is thoroughly software-tested — 606 automated tests, 602 passing, a clean release
build — and an earlier build was already validated on a physical Android phone with real measured
latencies. What hasn't happened yet, and the team is upfront about it, is validation on the actual
target hardware: no iQOO device has been available yet, and NPU acceleration is a clearly labelled
next step rather than something already built. The roadmap after this hackathon is concrete: get
this exact build onto iQOO hardware, add NPU/QNN acceleration, and build a labelled Indian scam
corpus so the model can finally be retrained and measured on the exact kind of scams it needs to
catch."

## Core architecture
```
User Input → ML Detection (on-device, capped signal)
           → Security Indicator Extraction (deterministic, rule-based)
           → Threat Intelligence (optional, hostname-only)
           → Evidence
           → Risk Engine (deterministic, 0-100, 5 bands)
           → Risk Score + Explanation + Safe Action
```
Modules: `:app`, `:core`, `:domain`, `:investigation`, `:riskengine`, `:ai`, `:threatintel`, and
eight `:feature:*` screens. `:riskengine` and `:investigation` are pure JVM with no Android or
network dependency; `:threatintel` is the only networking module.

## Key innovation
Most scam detectors stop at detection. TrustLens investigates the threat, collects evidence,
scores it transparently, and recommends a specific safe action — all before the user has clicked,
paid, or shared anything. The ML model is deliberately capped and never decides alone; a
deterministic, auditable risk engine makes the final call.

## Current implementation status
All 8 input surfaces built and wired end-to-end; on-device ONNX-based classifier; deterministic
investigation and risk-scoring layers; optional VirusTotal/URLhaus corroboration. 606 automated
tests, 602 passing, 0 failures. An earlier build was device-validated on a POCO X6 (used only as
an interim stand-in for target hardware). **The current exact build has not yet run on any
physical device, and no iQOO hardware measurement exists yet** — the single honest gap in an
otherwise thoroughly tested project.

## Current development plan
iQOO 15 real-device validation (top priority) → NPU/QNN execution provider, measured on that
hardware → a labelled Indian scam corpus (UPI, KYC, delivery, job-fraud, lottery) → retrain the
classifier with Hindi/Hinglish vocabulary → measure genuine end-to-end precision/recall →
server-side proxy for provider credentials → production-grade signing and a full device
regression pass.

## Key demo scenario
A synthetic bank-impersonation KYC SMS ("Your SBI account will be blocked today... share your OTP,
or pay Rs. 4999") scores 81/100 CRITICAL from deterministic evidence alone — urgency, OTP request,
payment request, account-suspension threat, and a brand/domain mismatch, each independently
detected and explained — with a fallback pair of exactly-reproducible link scores (the real SBI
domain at 0/100 LOW, a look-alike phishing domain at 22/100 GUARDED) that don't depend on the ML
model at all.

## Top 5 reasons judges should care
1. **It solves a real, present problem** — bank/KYC/UPI/OTP scams that affect ordinary Indian
   smartphone users every day — not a hypothetical.
2. **It goes beyond detection** — an explained, evidence-backed verdict with a specific
   recommended action, not a bare "scam/not scam" label.
3. **It's genuinely mobile-first and largely on-device** — camera, microphone, and share-sheet
   integration are core to the product, and the core pipeline runs fully offline.
4. **The engineering is real and honestly reported** — 606 automated tests, a documented,
   deterministic risk engine, and a project culture that says "not yet validated" rather than
   overclaiming.
5. **There's a clear, credible next step that plays to iQOO's strengths** — NPU-accelerated
   on-device inference, validated on real target hardware, is the obvious and already-planned
   next phase.
