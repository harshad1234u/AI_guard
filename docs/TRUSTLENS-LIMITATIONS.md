# TrustLens — Current Limitations

Honest inventory at commit `8b610a7`. Separated by what actually blocks the hackathon, what is
known and accepted, and what comes after.

**Nothing here is inflated into a blocker it is not.** The software checkpoint is verified: 606
tests, 602 passing, 0 failures, 0 errors, 4 skipped, release build green, frozen components
byte-identical to `v4.0-freeze`.

---

## Hackathon blockers

### 1. No physical device validation of the current build

No handset has run commit `8b610a7`. The POCO X6 used for earlier validation is unavailable.

**Not validated on this commit:** Live Guard, Check Email, the `ACTION_PROCESS_TEXT` selection
entry point, the multilingual layer, and the P0/P1 URL changes.

**What exists instead:** recorded Phase 14B evidence from the v4.0 era
(`tools/demo/phase14b-device-run.log`, POCO X6, Android 16 / API 36, arm64-v8a release APK)
covering the QR and OCR beats. That predates P0/P1 and must always be introduced as *"recorded
validation evidence — not a live lookup."*

**Why this is the one real blocker:** a demo is given on hardware. Everything else on this page
is a known trade-off; this is an unknown.

**Mitigation if no device arrives:** [`TRUSTLENS-DEMO-FALLBACK.md`](TRUSTLENS-DEMO-FALLBACK.md)
§8 — the two headline numbers (0/LOW for the real bank domain, 22/GUARDED for the
impersonation) are asserted in `UrlFlowIntegrationTest` and demonstrable on any laptop.

---

## Known non-blockers

These are understood, accepted, and in several cases deliberate. None prevents a demo.

### 2. The classifier's training corpus does not match its deployment domain

Trained on the **UCI SMS Spam Collection** — 5,574 UK English SMS from around 2011, spam vs
ham. It has never seen a UPI handle, an Indian bank name, or a KYC scam.

The benchmark, stated the only way it may be stated:

> **98.65% held-out accuracy on the UCI SMS Spam Collection** (1,115 test messages; precision
> 0.959, recall 0.940, F1 0.949, ROC-AUC 0.992).

This is a component-level result on a public English spam corpus. It is **not** TrustLens's
scam-detection accuracy and must never be quoted as one.

**Why it does not block:** the classifier is capped at 30 of 100 and never decides alone. The
deterministic layer carries the Indian-scam patterns, and the P1 guard removes its worst
failure mode.

### 3. TrustLens has no end-to-end accuracy figure

We have not built a labelled Indian scam corpus, so we cannot honestly quote end-to-end
precision or recall. We quote the component benchmark and say so. This is a measurement gap,
not a defect.

### 4. Mixed-content real-model probabilities not re-measured after P1

For text with wording around a link the classifier still runs, and those scores were last
measured on-device before P1. The JVM calibration harness wires a **mock** classifier, so its
numbers for ML-on inputs are not device-representative.

**Unaffected:** the two exact demo numbers. Both are ML-off inputs, so 0/LOW and 22/GUARDED
hold regardless of the model. The demo guide only asks the presenter to say a number aloud for
those two rows.

### 5. API keys are extractable from the release APK

Inherent to `buildConfigField`; empirically confirmed for the VirusTotal key. Free-tier keys,
no billing attached, R8 available but deliberately off (an untested shrink of a build using
Hilt, ONNX Runtime and two ML Kit models is not something to discover on stage).

**Action: rotate both keys after the event.** The only real fix is a server-side proxy.

### 6. Release APK is signed with the public Android debug keystore

Fine for side-loading at a hackathon. Must be replaced before any distribution.

### 7. Live provider availability is outside our control

VirusTotal and URLhaus can rate-limit, time out, or delist a host between rehearsal and demo.
By design this changes no local score — unknown contributes zero. The fallback guide covers the
wording. **Never present a cached provider verdict as live.**

### 8. ASR and device language availability

Voice Check and Live Guard depend on the handset's `SpeechRecognizer`: a recogniser must be
installed, and Hindi requires a language pack. `EXTRA_PREFER_OFFLINE` is requested but not
guaranteed. Live Guard is ranked first-to-cut in the demo guide for exactly this reason.

### 9. Live Guard is speakerphone-only

It listens through the microphone, not the call audio stream. Direct in-call audio capture is
available only to preinstalled system apps and the carrier app; a sideloaded APK cannot obtain
it. This is an Android platform boundary, not something to fix.

### 10. Physical QR and OCR validation pending on this build

Both worked in the Phase 14B device run and neither module changed in Phase 16, but neither has
been re-exercised with real optics on this commit.

### 11. Cellular-call audio validation pending

Live Guard has not been exercised against a real cellular call on this build — only the
speakerphone path, and not on this commit.

### 12. `.tk`/`.ml`/`.ga`/`.cf`/`.gq` added to the bare-domain TLD list

Needed so a pasted `sbi.bank.tk/verify` is recognised as a link at all. The regex requires a
literal dot, so ordinary prose `"500 ml"` cannot match — but `"500.ml"` would be read as a link.
Untested edge, low likelihood, cosmetic if hit.

### 13. `DeterministicEmailParserTest.kt` is stored by git as binary

It contains two literal NUL bytes inside a string literal testing malformed input. Intentional
test content; compiles and passes. Consequence: git will not show a textual diff for that file.

### 14. `TextCheckViewModel.analyse()` swallows analysis failures

`runCatching { … }.getOrNull()` means a failure yields no result rather than an error state —
it looks like nothing happened. Pre-existing pattern, not introduced by P0/P1.

### 15. Dev tooling left uncommitted

`skills-lock.json` and five `.claude/skills/` directories are uncommitted. Note that `.claude/`
*is* otherwise tracked in this repository, so this is an inconsistency to resolve deliberately —
not an accident.

---

## Post-hackathon improvements

In the order they should actually be done.

1. **Build a labelled Indian scam corpus** — UPI, KYC, delivery, job fraud, lottery. This is the
   real bottleneck. Everything below is easier once it exists.
2. **Retrain with Devanagari and transliterated Hinglish** in the vocabulary, so Hindi gets a
   model signal instead of rules alone.
3. **Measure end-to-end precision and recall** on that corpus, so TrustLens can finally quote a
   number that means what people assume it means.
4. **NPU / QNN execution provider**, then validate on iQOO hardware and measure latency. Note
   that no backend-selection policy exists yet — the `InferenceBackend` enum has NPU and GPU
   values so a backend can be *reported* honestly, but nothing selects one.
5. **Server-side proxy for provider credentials**, removing the extractable-keys issue.
6. **Proper release signing** and an R8 pass with a full device regression behind it.
7. **Full device validation matrix** — every surface, both themes, on target hardware.
8. Only then: a larger model architecture, and only if the data justifies it.
