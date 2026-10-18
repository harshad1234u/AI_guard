# TrustLens — Judging Demo Guide

**Target length: 4–5 minutes.** Twelve beats are listed; **beats 1–7 are the locked core**
(~3:30). Beats 8–12 are optional and get cut in order of the "Cut first" column when the clock
is tight.

Companion files:
- Inputs: [`demo-assets/`](demo-assets/) — copy-paste ready, all synthetic
- If anything is offline or broken: [`TRUSTLENS-DEMO-FALLBACK.md`](TRUSTLENS-DEMO-FALLBACK.md)
- Install and pre-flight: [`RELEASE-CHECKLIST.md`](RELEASE-CHECKLIST.md)

---

## The one sentence the whole demo exists to prove

> **Most scam detectors stop at detection. TrustLens investigates the threat before the user acts.**

Say it at beat 1. Prove it at beat 5. Repeat it at beat 12. Everything in between is evidence.

---

## How to read the expectation columns

| Label | What it means on stage |
|---|---|
| **exact** | The classifier does not run for this input, so the number is reproducible to the point. Safe to say the number out loud. |
| **band** | The band is stable; the exact number depends on the on-device model. **Say the band, not the number.** |
| **recorded** | A number from an earlier device run. If you show it, say: *"Recorded validation evidence — not a live lookup."* |

> **Presenter rule:** only read a score aloud when the row says **exact**. For **band** rows,
> say "GUARDED", "CRITICAL" — never a number you have not personally seen on that handset.

---

## The demo

### Beat 1 — Home (0:00–0:20)

| | |
|---|---|
| **Input** | none — just the app open |
| **Say** | "Every scam ends the same way: someone clicks, pays, or reads out a code. Most tools tell you *that* something is suspicious. TrustLens tells you *why*, before you act. Seven ways in, one investigation pipeline." |
| **Show** | The surface tiles: Message, Link, QR, Camera, Voice, Email, Live Guard |
| **Expected** | Home renders, no analysis running |
| **Transition** | "Start with the one everybody gets." |

---

### Beat 2 — Check Message, scam SMS (0:20–1:00)

| | |
|---|---|
| **Input** | [`A-scam-sms.txt`](demo-assets/A-scam-sms.txt) |
| **Say** | "A bank alert. Urgent, a link, an OTP request, a payment demand." |
| **Expected band** | **CRITICAL** — *(band)* |
| **Key evidence** | Urgency +10 · OTP request +22 · Payment request +15 · Account threat +10 · Claims to be SBI +12 · Message claims SBI but the link goes to example.com +12 |
| **Safe action** | Do not open the link · Do not pay · Do not share your OTP · Verify through the official app or helpline |
| **The line that matters** | "Six separate reasons, each worth a stated number. Add the deterministic ones and you already have 81 — that is CRITICAL before the model contributes anything at all." |
| **Transition** | "Now the test that actually matters for a security tool." |

---

### Beat 3 — Check Message, benign SMS (1:00–1:20)

| | |
|---|---|
| **Input** | [`B-benign-sms.txt`](demo-assets/B-benign-sms.txt) |
| **Say** | "A tool that flags everything is a tool people turn off." |
| **Expected band** | **LOW**, zero observations — *(band)* |
| **Key evidence** | none — the reasons list is empty, deliberately |
| **Safe action** | none beyond Proceed carefully |
| **Transition** | "Same screen, a link on its own this time." |

---

### Beat 4 — Check Link, genuine domain (1:20–1:45)

| | |
|---|---|
| **Input** | [`C-genuine-url.txt`](demo-assets/C-genuine-url.txt) → `https://www.sbi.co.in` |
| **Say** | "The real State Bank of India domain." |
| **Expected** | **0/100, LOW** — *(exact — safe to say the number)* |
| **Key evidence** | none. Plus the disclosure: *"This is a link with no message around it. The classifier reads message wording, so it was not run — the link was analysed structurally instead."* |
| **The line that matters** | "Notice it tells you the model **did not run**, rather than implying it ran and found nothing." |
| **Transition** | "Same screen. One character different in intent." |

---

### Beat 5 — Check Link, phishing domain (1:45–2:30) — **the money beat**

| | |
|---|---|
| **Input** | [`D-phishing-url.txt`](demo-assets/D-phishing-url.txt) → `sbi.bank.tk/verify` |
| **Say** | "A link pretending to be the same bank." |
| **Expected** | **22/100, GUARDED** — *(exact — safe to say the number)* |
| **Key evidence** | Free-registration `.tk` address +10 · `sbi` appears as a label but the registrable domain is `bank.tk` +12 |
| **Safe action** | Do not open the link · Verify through the official app or helpline |
| **The line that matters** | "No model ran on either link. No threat-intelligence provider has ever heard of this host. TrustLens still ranks the impersonation above the real bank — from structure alone. And it *never opened the link* to find out." |
| **Fallback note** | If a provider *does* answer, the score rises and you say so. If it returns nothing, say: "No provider has heard of it — and that changes nothing, because unknown is never treated as safe." |
| **Transition** | "It reads the world too, not just what you paste." |

---

### Beat 6 — Camera / OCR (2:30–2:55)

| | |
|---|---|
| **Input** | `tools/demo/assets/ocr_scam.png` — display on a second screen and point the camera at it |
| **Say** | "Text you can't copy — a photo, a poster, someone else's screen." |
| **Expected band** | **HIGH** — *(recorded: 62/100 on POCO X6, Phase 14B)* |
| **Key evidence** | Same reason list as a typed message — OCR only changes how the characters arrive |
| **If you quote 62** | Say "recorded validation evidence, not a live lookup" |
| **Transition** | "Same for a QR code — and this one matters more than it looks." |

---

### Beat 7 — QR (2:55–3:30)

| | |
|---|---|
| **Input** | `tools/demo/assets/qr4_upi_request.png` (UPI payment request) |
| **Say** | "A payment QR. TrustLens decodes it and investigates it — it never opens it and never pays it." |
| **Expected band** | **GUARDED** — *(recorded: 35/100, Phase 14B)* |
| **Key evidence** | UPI payment request |
| **Safe action** | Do not pay · Verify through the official app or helpline |
| **The line that matters** | "There is no 'Pay' button anywhere in this app. Not disabled — absent. The enum of safe actions has seven entries and every one of them is decline or defer." |
| **Contrast** | `qr2_url.png` → **LOW** *(recorded: 0/100)* if you want the benign counterpart |

**End of locked core (~3:30). Everything below is optional.**

---

### Beat 8 — Check Email (optional, +0:30) — **Cut first: 5th**

| | |
|---|---|
| **Input** | [`E-scam-email.txt`](demo-assets/E-scam-email.txt), shared from a mail app or pasted |
| **Say** | "Shared in from any mail app. No Gmail login, no OAuth, no mailbox access — the user hands us one message." |
| **Expected band** | **HIGH or CRITICAL** — *(band)* |
| **Key evidence** | Urgency · account threat · OTP request · impersonation · domain mismatch |
| **The line that matters** | "The sender's domain gets checked exactly like any link. The subject, the body, and the name before the @ never leave the phone." |
| **Contrast** | [`F-benign-email.txt`](demo-assets/F-benign-email.txt) → **LOW** if time allows |

---

### Beat 9 — Voice Check (optional, +0:25) — **Cut first: 3rd**

| | |
|---|---|
| **Input** | Read [`I-voice-transcript.txt`](demo-assets/I-voice-transcript.txt) aloud |
| **Say** | "Say back what the caller said." |
| **Expected band** | **MEDIUM or above** — *(band)* |
| **Key evidence** | OTP request · urgency · account threat · impersonation |
| **Risk** | Needs a working on-device recogniser. See fallback. |

---

### Beat 10 — Live Guard (optional, +0:35) — **Cut first: 1st (highest risk)**

| | |
|---|---|
| **Input** | Speakerphone, or a colleague reading `I-voice-transcript.txt` |
| **Say** | "On speakerphone, it re-scores the call as it happens." |
| **Expected** | Score climbs as the demands accumulate — *(band; live ASR, no pinned number)* |
| **Risk** | Highest-variance beat in the demo: ASR availability, language packs, room noise. **Cut this first.** |

---

### Beat 11 — Hindi / Hinglish (optional, +0:30) — **Cut first: 4th**

| | |
|---|---|
| **Input** | [`G-hindi-scam.txt`](demo-assets/G-hindi-scam.txt) into Check Message |
| **Say** | "The same demand, in Hindi." |
| **Expected band** | **MEDIUM or above** — *(band)* |
| **Key evidence** | OTP request · urgency · account threat · impersonation — the same four, at the same weights |
| **The line that matters** | "The classifier contributes nothing here — its vocabulary has no Devanagari, and it says so rather than guessing. The deterministic layer carries it. Through the English-only extractor this exact script scored LOW with zero observations." |

---

### Beat 12 — Close on the result screen (0:20) — **never cut**

| | |
|---|---|
| **Show** | Scroll the CRITICAL result from beat 2: score, band, every reason with its weight, the evidence section, the safe actions |
| **Say** | "Every point on that number traces to a sentence. Nothing here opens a link, pays anything, or installs anything. **Most scam detectors stop at detection. TrustLens investigates the threat before the user acts.**" |

---

## Cut order when the clock is short

| Cut | Beat | Why it goes first |
|---|---|---|
| 1st | 10 — Live Guard | Live ASR is the highest-variance thing in the demo |
| 2nd | 9's contrast case | Nice-to-have |
| 3rd | 9 — Voice Check | Still ASR-dependent |
| 4th | 11 — Hindi | Strong story, but beats 2–5 already carry the thesis |
| 5th | 8 — Email | Strong story, same reason |

Never cut beats 1, 2, 4, 5, 12. Beats 4 and 5 together **are** the P1 story and they are the
only two rows in the whole demo whose numbers are exactly reproducible.

---

## Network-dependent evidence — what to say

Threat intelligence is **corroboration, not a prerequisite**. Every expectation above is
satisfied with the phone in aeroplane mode.

| Situation | What you say |
|---|---|
| Provider confirms the host | "Two independent sources agree — that is worth more than either alone, and the engine weights it that way." |
| Provider returns "no report" | "No provider has heard of it. That is normal for a fresh phishing domain, and it changes nothing: unknown is never treated as safe. The 22 was local." |
| Offline / no key / rate-limited | "Threat intelligence is unavailable, and the app says so on screen rather than quietly scoring it clean." |

**Never** claim a live provider verdict you have not just seen on that screen.

---

## Pre-flight, five minutes before

1. Aeroplane mode **off**, then confirm the app still works with it **on** — the core beats must not depend on the network.
2. Open each of beats 2, 4, 5 once and confirm the bands. If beat 4 is not `0/LOW` or beat 5 is not `22/GUARDED`, **stop and use the fallback guide** — something is wrong with the build.
3. Screen brightness up; the reason list is the demo.
4. Have `demo-assets/` open on a laptop for copy-paste.
5. Battery > 50%; screen timeout to 5 minutes.
6. If quoting recorded numbers, have `tools/demo/phase14b-device-run.log` open.
