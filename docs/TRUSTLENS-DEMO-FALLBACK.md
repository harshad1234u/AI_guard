# TrustLens — Demo Fallback Plan

What to do when something on stage does not work. Companion to
[`TRUSTLENS-DEMO-GUIDE.md`](TRUSTLENS-DEMO-GUIDE.md).

---

## The rule that overrides everything else

> **Never fabricate a live threat-intelligence result.**

Not a screenshot presented as live. Not "it usually says malicious." Not a number you did not
just see. If a provider does not answer, the honest sentence is already the stronger one:

> *"No provider has heard of this host. That is exactly what a fresh phishing domain looks
> like — and it changes nothing, because TrustLens never treats unknown as safe. This score is
> local."*

Any pre-recorded screenshot, video or log you display must be introduced with, verbatim:

> **"Recorded validation evidence — not a live lookup."**

---

## The good news: the core demo is offline by design

Beats 1–5 and 12 of the demo guide need **no network at all**. Deterministic extraction, URL
structure analysis, the on-device classifier and the risk engine all run locally. Threat
intelligence is enrichment layered on top.

**The two beats whose numbers are exactly reproducible — beat 4 (`0/LOW`) and beat 5
(`22/GUARDED`) — are entirely offline and involve no model.** If everything else fails, those
two beats plus beat 2 still prove the thesis.

---

## Failure playbook

### 1. No internet / venue Wi-Fi dead

**Impact:** none on the core. VirusTotal and URLhaus cannot be consulted.

**Do:** Put the phone in aeroplane mode *deliberately, in front of the judges*, and use it.

**Say:** "I have just taken this phone offline. Everything you are about to see runs on the
device. Threat intelligence would add corroboration when it is available — it is never a
prerequisite, because a brand-new phishing domain has no reputation history anyway."

This is a **stronger** demo than the online one. Consider doing it on purpose.

---

### 2. VirusTotal unavailable (rate limit, key rejected, timeout)

**Impact:** external evidence section reports the provider as unavailable.

**Do:** Continue. Point at the disclosure on screen.

**Say:** "The provider could not be consulted, and the app says so rather than quietly
counting it as clean. Unknown, error and no-detection all contribute exactly zero — they can
never *lower* a score."

---

### 3. URLhaus unavailable

Same as VirusTotal. Both are optional, independent, and weighted with diminishing
corroboration — the first source that reports a host carries the most weight, a second
independent one adds less.

**Do not** substitute a screenshot of a previous URLhaus hit as though it were live.

---

### 4. Live external evidence changed since rehearsal

A host that was listed last week may be delisted, or vice versa. **This is expected.**

**Do:** Read what is on the screen, not what you rehearsed.

**Say:** "Reputation data moves. That is precisely why the local layer carries the verdict and
the external layer only corroborates it."

**Never** say "it normally shows malicious."

---

### 5. Microphone / ASR fails (Voice Check, Live Guard)

**Symptoms:** no recogniser installed, language pack missing, permission denied, silence.

**Do:** Skip beats 9 and 10 — the guide already ranks Live Guard as the first thing to cut.
Feed the identical text through **Check Message** instead.

**Say:** "Voice and Live Guard transcribe on the device and then feed this exact pipeline. Let
me show you the same call as text — the analysis is byte-for-byte identical, because it is the
same use case."

That is a true statement, not a workaround: `AnalyzeTextUseCase` is the shared path.

**Do not** claim a Live Guard demo happened if it did not.

---

### 6. Camera unavailable (permission, hardware, lighting)

**Impact:** beats 6 and 7 (OCR, QR).

**Do:** Show `tools/demo/assets/ocr_scam.png` and the QR images on a laptop and either
(a) point the camera at the screen, or (b) skip to Check Message with equivalent text.

If you show the Phase 14B recorded results (`tools/demo/phase14b-device-run.log`), introduce
them as **"Recorded validation evidence — not a live lookup."**

**Say:** "OCR and QR decoding only change how characters reach the pipeline. The investigation
after that point is the same code."

---

### 7. The app crashes or a band is wrong

**Check first:** beat 4 must be `0/100 LOW` and beat 5 must be `22/100 GUARDED`. Those two are
exact and model-independent. If either is wrong, the build on the phone is not the audited one.

**Do:** Reinstall from the checksummed APK in [`RELEASE-CHECKLIST.md`](RELEASE-CHECKLIST.md).
If that is not possible, present from the demo guide and the recorded evidence, and say plainly
that you are showing recorded results.

---

### 8. No device at all

Fall back to: the pitch deck, this guide, `tools/demo/phase14b-device-run.log`, and the test
suite. `./gradlew test` runs 606 tests on any laptop with a JDK and needs no phone, no network
and no API key.

**Say:** "Six hundred and six tests, and the two headline numbers you would have seen on the
handset — zero for the real bank domain, twenty-two for the impersonation — are asserted right
here, in `UrlFlowIntegrationTest`."

That is a legitimate demonstration. It is not a device validation, and must not be described as one.

---

## Sentences that are always safe to say

- "This runs on the device."
- "Threat intelligence corroborates; it is never a prerequisite."
- "Unknown is not treated as safe."
- "The classifier contributes at most 30 of 100, and it never decides alone."
- "Nothing in this app opens a link, pays, or installs anything."
- "This number is reproducible — no model runs on this input."

## Sentences that are never safe to say

- "VirusTotal says this is malicious" — unless it is on the screen right now.
- "This was validated on the iQOO 15 / POCO X6" — unless that run actually happened.
- "It runs on the NPU."
- "TrustLens is 98.65% accurate at detecting scams."
- "It detects all Indian scams."
- Any latency figure you did not measure on that handset.
