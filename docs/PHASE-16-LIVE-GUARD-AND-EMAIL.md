# Phase 16 — Live Guard and Check Email

**Branch:** `phase16-liveguard-email` · **Base:** `abccb49` (`v4.0-freeze`)
**Status:** built, JVM-tested, and device-validated on a POCO X6 5G — see §3b for what was
confirmed on hardware and §3c and §5 for what was not.

This supersedes `TrustLens_PRD_v5.3_FINAL.md` §4 and §12 for the Email row: Email is no longer
"NOT BUILT". Live Guard is new and appears in neither document. The v4.0 freeze tag is
untouched and still points at `abccb49`; everything here is additive work on a branch.

---

## 1. What was added, and what deliberately was not

| Feature | Status |
|---|---|
| **Live Guard** — continuous listening during a call, on speakerphone | **Built** |
| **Check Email** — share a mail in, analysed by the text pipeline | **Built** |
| **Check selected text** — highlight text in any app, analysed by the text pipeline | **Built — see §3c** |
| Gmail API / OAuth / mailbox access | **Not built, and deliberately not — see §3c** |
| Call audio interception | **Not built — impossible, see §2** |
| `CallScreeningService` / caller-ID screening | Not built — still forbidden by Principle 3 |
| Phone-number heuristic | Not built — unchanged from v5.3 |
| Report Narrator / any LLM | Not built — unchanged from v5.3 |

**No new detection logic, and no scoring change of any kind.** Both surfaces are *input paths*
onto the frozen pipeline:

```
Live Guard    -> transcript -> AnalyzeTranscriptUseCase -> AnalyzeTextUseCase -> RiskEngine
Check Email   -> subject+body -> AnalyzeEmailUseCase   -> AnalyzeTextUseCase -> RiskEngine
```

Verified untouched: `:riskengine` has **zero** changed files, `RiskWeights.kt` is byte-identical,
and `SafeAction` still has exactly **7** members.

---

## 2. Live Guard — what it is, stated precisely

> **Live Guard listens through the microphone. It is not attached to the call.**

This is the single most important sentence in this document, and it is also the first thing the
screen tells the user, before the microphone is ever switched on.

**Why it cannot be a true call interceptor.** Android's `VOICE_CALL`, `VOICE_DOWNLINK` and
`VOICE_UPLINK` audio sources require `CAPTURE_AUDIO_OUTPUT`, which is `signature|privileged` —
available only to preinstalled system apps and the carrier app. A sideloaded APK cannot obtain
it under any configuration. This is an OS restriction, not a policy choice, and no amount of
engineering changes it.

**What Live Guard does instead.** The user puts the call on **speakerphone** and starts Live
Guard. The app then hears the far end exactly as a person standing nearby would, transcribes it
on-device, and re-scores the conversation as it goes. The system microphone indicator is lit the
entire time.

### Behaviour

| Concern | How it behaves |
|---|---|
| Continuous listening | `SpeechRecognizer` ends a session at every pause, so `LiveTranscriber` restarts it. Restarts are bounded and backed off; five consecutive hard failures stop the loop rather than spin. |
| What gets analysed | **Completed phrases only.** Interim results are displayed so the user can see the microphone working, and are never scored — a half-heard sentence would produce a verdict about words nobody said. |
| Cost control | Analysis is debounced by 1.2 s and an unchanged transcript is never analysed twice, so three phrases in quick succession cost one analysis rather than three. |
| The peak | Both the live score *and* the highest score reached are shown. A scammer says the dangerous sentence once; a score that quietly recovered while they changed the subject would be the wrong thing to leave on screen. |
| Audio never arrives | Reported as **"No audio is reaching TrustLens"**, never as a clean result. See below. |
| Microphone release | Released on Stop, on leaving the screen (`DisposableEffect`), and on any terminal failure. |
| Audio retention | None. `onBufferReceived` is a no-op, as in the single-shot path. No buffer, file, cache or upload exists anywhere in the feature. |

### The silence problem, and why it is handled explicitly

On many devices the telephony stack takes exclusive use of the microphone during a call, and a
third-party app is handed **silence rather than an error**. Silence that rendered as "nothing
suspicious was said" would be the most dangerous failure this feature could have.

So audio level is tracked via `onRmsChanged`. Three consecutive sessions ending with no audio at
all raise a visible warning telling the user to turn on speakerphone, and stating plainly that
**until audio arrives, nothing is being checked**.

> Whether the microphone is actually available during a live call is **OEM-dependent and has
> not yet been tested on the POCO X6.** See §5. Live Guard works regardless when the phone hears
> a scammer speaking in the room, or audio played from another device.

---

## 3. Check Email — what it is

A message shared to TrustLens from any mail app, analysed by the identical text pipeline.

**No mailbox access, no OAuth, no account.** The app has no Gmail integration and no read
permission of any kind. It sees exactly the one message the user chose to share, via a standard
`ACTION_SEND` / `text/plain` intent filter. Receiving a share is *input* — this activity still
starts no activity of its own, so Principle 3 is untouched.

### The sender-domain check — the one thing this surface adds

It adds no weight to do it. The sender's **host** is handed to `AnalyzeTextUseCase` as an extra
lookup subject, so it is graded by the same providers, on the same scale, as any host found in a
link. `UNKNOWN` still is not `SAFE`.

This needed a small, strictly additive change. `RuleBasedIndicatorExtractor.URL_BARE_DOMAIN`
carries a `(?<![@\w.\-/])` lookbehind, so the host inside `alerts@fake-sbi.com` is deliberately
never extracted as a link — correctly, since it is not one. For an email that host is the most
useful thing to check, so `AnalyzeTextUseCase.invoke` gained an
`extraLookupSubjects: List<String> = emptyList()` parameter. **It defaults to empty, so every
existing surface behaves exactly as before** — pinned by a test.

### What leaves the device

| Data | Transmitted? |
|---|---|
| Subject | **Never** |
| Body | **Never** |
| Sender address (local part, before the `@`) | **Never** — it identifies a person |
| Sender **domain** | Yes — hostname only, to the same two reputation providers |

Three tests assert this directly, including that no OTP, account number or subject text ever
appears in a lookup subject.

### Sender recovery is best-effort, and fails safe

`ACTION_SEND` carries no sender field. `DeterministicEmailParser` recovers one from a forwarded
`From:` header when the mail app included one, scanning at most the first 40 lines so it cannot
pick a sender out of an older quoted reply or the body prose. Failing to find one is the safe
direction: it removes one *additional* check and can never make a dangerous email look clean,
because the body is scored either way. The field is editable so the user can supply it.

---

## 3a. Multilingual Live Guard — Hindi, Hinglish and Indian English

> **The caller is understood, not translated.** TrustLens never renders Hindi speech into
> English for the user to read. It reads the original words and decides what is being asked
> for.

### The gap this closes, measured

Before this work, run over the real components:

| Input | Deterministic flags | ML p(scam) | Score |
|---|---|---|---|
| English bank + threat + OTP script | otp, urgency, threat | 0.505 | **57 / 100** |
| The identical script in Hindi | **none — every flag false** | **0.101** | **3 / 100, LOW** |
| The identical script in Hinglish | **none** | 0.138 | **4 / 100, LOW** |

Two independent causes, and neither was a tuning problem:

1. **The asking verb.** `RuleBasedIndicatorExtractor` matches "OTP" in any script, but only
   counts it as a *request* when a verb from `share|send|tell|give|…` sits within 60
   characters. "बताइए" and "bataiye" are not in that list, so the ask never registered and
   the +22 never fired.
2. **The classifier is mute in Hindi, not weak.** Its vocabulary is 12,007 English features
   from the UCI SMS corpus and contains no Devanagari — not even the token `otp`. Every Hindi
   input produced a feature vector of exactly zero and therefore an output *bit-identical to
   the empty string*: p = 0.10117331 for a scam script and for "आज मौसम बहुत अच्छा है" alike.

### What was added

| Change | Where |
|---|---|
| `MultilingualIndicatorExtractor` — a **decorator** over the frozen extractor | `:investigation` |
| Wired for every surface | `TrustLensModule.provideIndicatorExtractor` |
| Language detection + mid-session language switching | `LiveTranscriber` |
| On-device language inventory and Hindi pack download | `RecognitionLanguages` |
| Zero-coverage input reported as unavailable rather than scored | `OnnxScamClassifier` |

**No new `ObservationKind`, and no new weight.** A Hindi OTP request produces the same
`OTP_REQUEST` at the same `+22` an English one does. Verified: `:riskengine` has **zero**
changed files against `v4.0-freeze`, `RiskWeights.kt` is **byte-identical**, and `SafeAction`
still has exactly **7** members. `MultilingualFlowIntegrationTest` asserts that the same
demand in Hindi and in English reaches the same score and the same observations.

The decorator only ever **adds** flags — every boolean is OR-ed, every list unioned — so it
cannot make the extractor it wraps worse. `MultilingualParityTest` pins English behaviour
against the bare frozen extractor, including the demo hero message.

### The three deliberate English changes

Detecting these only in Hindi would have been arbitrary, so they apply in both:

1. **The OTP euphemism** — "the six digit number" as well as "छह अंकों का नंबर". This is the
   wording a caller uses once they learn that saying "OTP" ends the call.
2. **Police / cyber-crime impersonation** — the frozen organisation list has no entry for it
   in any language, and it is the entire premise of the "digital arrest" scam.
3. **Customs department impersonation**, likewise.

As in the frozen extractor, a named body is only an impersonation signal when a coercive ask
sits beside it, so an ordinary sentence mentioning the police still scores nothing.

### Speech recognition

Live Guard now asks the recogniser for `hi-IN` and `en-IN` together, with
`EXTRA_ENABLE_LANGUAGE_DETECTION` and `EXTRA_ENABLE_LANGUAGE_SWITCH` at `BALANCED`
sensitivity — `BALANCED` rather than `QUICK_RESPONSE` because switching on a not-confident
detection would corrupt a transcript that is about to be scored. `EXTRA_PREFER_OFFLINE` is
requested so the on-device claim is the thing the app actually asked for. The detected
language is shown as *"Recogniser reports: Hindi"* — a report, not a claim of comprehension.

**Every one of these extras is documented by Android as "depending on the recognizer
implementation, this value may have no effect."** None of them is load-bearing: if HyperOS
ignores them, behaviour is exactly what it was before they were set. **Whether they take
effect on the POCO X6 has not been measured** — no device was attached when this was written.

The failure that matters is quiet: a recogniser with no Hindi model does not refuse Hindi
speech, it transcribes it as English-sounding nonsense, which then scores as harmless. So
`RecognitionLanguages` asks the platform (`checkRecognitionSupport`, API 33+) which languages
are actually **installed** on-device, and the screen says so before listening starts, with an
offer to fetch the Hindi pack. A missing model is stated, never discovered as a low score
during a real call.

### Privacy

Unchanged. No audio is retained, `onBufferReceived` is still a no-op, and no transcript
leaves the device. The OTP rules match the *request phrasing* and never the digits, so no
one-time code can reach an `IndicatorMatch` and from there the screen — asserted by tests in
both `:investigation` and `:app`.

---

## 3b. Device validation — POCO X6 5G

**Everything in this section was observed on hardware.** Anything not measured says so.

| | |
|---|---|
| Device | POCO X6 5G — `23122PCD1I`, `garnet`, serial `ecb3818` |
| OS | Android 16 (SDK 36) · HyperOS `OS3.0.301.0.WNRINXM` (V816) |
| System locale | `en-IN` |
| Recogniser | `com.google.android.as` — Android System Intelligence, on-device |
| Build | `:app:assembleRelease` BUILD SUCCESSFUL in 3m 22s · APK 64,204,482 bytes (61.23 MB) |
| Install / cold launch | Success in 7.8 s · `am start -W` TotalTime **2211 ms** (first launch post-install) |
| Regression | `./gradlew test --rerun-tasks` → **531 / 527 passed / 0 failed / 0 errors / 4 skipped** |

### Existing surfaces — no regression

The frozen hero SMS scored **100/100 CRITICAL, confidence high**, with reasons summing
correctly before the 0..100 clamp (21+10+22+15+10+12+12 = 102 → 100). **Threat intel was live:**
URLhaus and VirusTotal were both queried and both answered `NO_DETECTION` for
`sbi-kyc.example.com`, each rendered as *"this is not a confirmation of safety (+0)"*.

Check URL on `https://sbi.secure.login.example.com:8443/kyc/update.apk` scored **25/100** =
deep subdomain (8) + installable file (12) + non-standard port (5), structure parsed offline.

### Multilingual detection — the headline result

Same semantic content, three languages, **identical deterministic terms**:

| | ML | `OTP_REQUEST` | `ACCOUNT_THREAT` | Total |
|---|---|---|---|---|
| Hindi — *"मैं बैंक से बोल रहा हूँ। आपका खाता बंद हो जाएगा। ओटीपी बताइए।"* | did not run | **+22** | **+10** | **32/100 GUARDED** |
| Hinglish — *"Bank se bol raha hoon, account block ho jayega, OTP bataiye."* | +4 | **+22** | **+10** | **36/100 GUARDED** |
| English — *"I am calling from the bank. Your account will be blocked. Tell me the OTP."* | +7 | **+22** | **+10** | **39/100 GUARDED** |

The only variation is the ML term, which is a genuine measurement of English-vocabulary
coverage. **There is no Hindi bonus, no Hinglish bonus and no language multiplier.** Evidence
for the Hindi case quotes the original script: `Mentions an OTP: "ओटीपी"`. Hindi re-run gave
32/100 again — deterministic.

### The zero-vector guard, on screen

For the Devanagari input the app displayed, verbatim:

> *"The on-device model did not run: The on-device model has no vocabulary coverage for this
> text — it was scored by the deterministic rules alone. This assessment comes from
> deterministic analysis only."*

No `0.101`, no fabricated `+3`, no ML observation. The premise holds in production.

### Speech recognition — what the platform actually did

| Question | Answer on this device |
|---|---|
| `checkRecognitionSupport()` works | **Yes** — returned real language lists |
| `hi-IN` installed on-device | **Observed both states.** Reported *pending download* at 16:36, *installed* by 16:47; the screen changed its disclosure accordingly, so both branches are validated |
| `en-IN` installed | Yes |
| `EXTRA_ENABLE_LANGUAGE_DETECTION` honoured | **Yes** — UI showed *"Recogniser reports: English (India)"* |
| `EXTRA_ENABLE_LANGUAGE_SWITCH` honoured | **Yes** — logcat: `ASR successfully switched locale to [en-in]` then `[hi-in]` within one session |
| Capture format (recogniser's own) | 16 kHz, mono, PCM 16-bit (`AudioRecordImpl … sr 16000 ch 1 fmt 1`) |

The javadoc warning that these extras "may have no effect" turned out not to apply here.
**That is a POCO X6 result and does not transfer to the iQOO 15.**

### Live Guard — microphone

Audio source: Windows TTS through PC loudspeakers into the phone's microphone. **Not a human
voice**, and far-field, which matters for every transcript quoted below.

English, transcript **uncorrected**:

> *"hello hello I am calling from your bank your account will be blocked today please tell me
> the OTP tell me the OTP now"* → **40/100 MEDIUM** (ML +8, OTP +22, threat +10)

Three English runs succeeded (40, 39, 29 / 100). `OTP_REQUEST +22` fired in all three.

**Peak retention, observed live:** the score reached 39, then benign speech diluted the ML
term and it fell to 27 — and the banner held *"Highest so far this call: 39/100 (GUARDED)"*.
On Stop the ENDED screen led with *"Highest risk during this call: 39/100"*. The fall is real
ML dilution; `OTP_REQUEST +22` never left the reasons.

**Interim results are shown and never scored** — a partial rendered as `"Bank"` with no
verdict on screen.

**Silence produces nothing.** 25 s of no speech: no transcript, no score, no verdict, no
safety claim. Just "Listening…".

**Microphone release** — appops `(running)` → `duration=` in all three paths: Stop, leaving
the screen, and process death.

Hinglish, transcript **uncorrected**:

> *"Bank AC call kar rahe ho aapka Bank AC call kar rahe ho aapka"* → 20/100 (ML only)

The recogniser produced romanised Hinglish, but the scam-critical words — "OTP", "block" —
did not survive at this signal-to-noise ratio. **The failure is transcription, not detection:**
the identical Hinglish sentence as *text* scored 36/100 with `OTP_REQUEST +22`. The acoustic
path was intermittent throughout (`agsa_transcription_NO_SPEECH_DETECTED`, handled correctly
by the bounded restart loop).

### Check Email

Shared via a real `ACTION_SEND` intent. `DeterministicEmailParser` **recovered the sender from
the `From:` header on device** and pre-filled the field.

Scam email → **89/100 CRITICAL**, reasons summing exactly (23+10+22+10+12+12 = 89).
Benign email → **5/100 LOW, "Proceed carefully"**. No inflation from the Email route.

**Privacy, verified against the on-screen lookup list.** For an email from
`alerts@fake-sbi.com` containing an OTP demand and a link, the *only* subjects sent to
providers were `fake-sbi.com` and `example.com`. Never transmitted: the `alerts@` local part,
the subject, the body, the OTP wording, the URL path.

### Measured latency

| Path | Result |
|---|---|
| Cold app launch | **2211 ms** (`am start -W`, first launch after install) |
| Email tap → first visible change | **≤ 932 / 917 / 918 ms** over three runs |
| Email tap → screen fully settled (incl. 4 live reputation lookups) | **≤ 3130 / 3130 / 3106 ms** over three runs |

Measured by `screencap` polling; the poll itself costs 698–788 ms, so these are **upper bounds
at ~750 ms resolution**, not point estimates.

**NOT MEASURED:** microphone start, first transcript, completed phrase → verdict, and peak
update. Two methods were tried and both failed honestly — `uiautomator` polling costs
2.5–3.4 s (coarser than the quantity), and `screenrecord` frame timing could not separate the
tap from the render because the encoder only emits frames on change. No number is given rather
than an estimated one. The same applies to speech → transcript latency.

### Not done on hardware

- **Cellular speakerphone (the deciding question) — NOT EXECUTED.** It needs a second phone, a
  person to answer, and a person to speak. Whether the microphone receives audio while a
  cellular call is active on this handset therefore **remains unanswered**, and is not claimed
  in either direction.
- **Hindi speech — NOT TESTED.** No Hindi TTS voice exists on the test PC, so Hindi audio could
  not be synthesised. Hindi *text* detection and Hindi *ASR model availability* are both
  validated; Hindi *transcription accuracy* is not.

---

## 3c. Check selected text — `ACTION_PROCESS_TEXT`

> **TrustLens still has zero mailbox permissions.** This is the alternative to Gmail access,
> not a step towards it.

### Why this instead of the Gmail API

Reading the user's inbox would need the `gmail.readonly` scope. That is a **restricted** scope:
publishing requires an annual CASA assessment by a Google-approved lab, and until then an app
is stuck in Testing with 100 users and refresh tokens that expire after **7 days**. It would
also cost the sentence that makes this product what it is — *"TrustLens cannot read your
mailbox"* — and put a "View your email messages" consent dialog in front of users of a
scam-detection app.

So the user highlights the text instead, and TrustLens receives those characters and nothing
else.

### The two entry points

```
ACTION_SEND          mail app -> Share -> TrustLens        -> Check Email
ACTION_PROCESS_TEXT  any app  -> select text -> TrustLens   -> Check Message
```

Both are **input only**. Neither grants TrustLens the ability to send, open or launch
anything, and `ACTION_SEND` is unchanged — the existing Email flow was not touched.

A share goes to Check Email because it is a whole *message* and a sender can be recovered from
a forwarded header. A selection goes to Check Message because it is a *fragment*: it has no
sender, and showing it inside a mail-shaped screen would imply TrustLens saw more than the
characters it was handed.

### No new scoring

`ProcessTextActivity` hosts the existing Check Message screen and nothing else. There is no
process-text detector, no selection weight, no separate engine:

```
selected text -> TextCheckViewModel -> AnalyzeTextUseCase -> RiskEngine
```

`:riskengine` still has **zero** changed files against `v4.0-freeze`, `RiskWeights.kt` and
`RiskEngine.kt` are byte-identical, and `SafeAction` still has exactly **7** members.
`ProcessTextFlowIntegrationTest` asserts that a selection and the same characters pasted in
produce identical score, level, observations, reasons and safe actions.

### Why a dedicated activity

The filter was first declared on `MainActivity`. On the POCO X6 it registered correctly —
`cmd package query-activities` listed `com.trustlens.MainActivity` among the device's nine
`PROCESS_TEXT` handlers — but it never appeared in any app's selection menu. Every other
handler on that device uses a dedicated, non-launcher activity:

```
ProcessTextGatewayActivity   TrampolineActivity      SelectedTextSearchActivity
SelectToSpeakPopupActivity   DeepLinkActivity        TapToTranslateActivity
ProcessTextSearch            TextProcessorActivity
```

So the filter moved to `ProcessTextActivity`, which is `excludeFromRecents` and carries its own
`taskAffinity` so checking a selection never disturbs whatever the user had open in TrustLens
itself. It **never calls `setResult`** — `ACTION_PROCESS_TEXT` allows a handler to write
modified text back into the source app, and a read-only analyser must not.

### Input handling

| Input | Behaviour |
|---|---|
| Missing, null, empty or whitespace-only | App opens normally. **No analysis, no verdict** — a verdict about nothing would read as "nothing wrong here" |
| Styled `CharSequence` | Flattened to plain text; styling never reaches the pipeline |
| Longer than 20,000 characters | Capped, and the screen **states how much was analysed** |

The existing Text pipeline imposes no length limit of its own, so the cap is new and exists
only because a selection can be a "select all" from a long web page. It sits far above any
real message — a scam SMS is a few hundred characters, a long phishing email a few thousand —
and **truncation is never silent**, because a link dropped off the end of a selection would
otherwise look like a link that was checked and found clean.

### Privacy

```
No Gmail API          No OAuth              No mailbox access
No inbox scanning     No account access     No IMAP, no app passwords
No contacts, SMS or storage permission
Selected text only    Hostname-only threat intelligence
```

The screen says so before the verdict: *"Analysing selected text only. TrustLens received just
the text you highlighted — it has no access to your mailbox, your messages, or anything else
in that app."* Threat intelligence still receives hostnames and nothing else; tests assert
that an OTP, an account number and a URL path in a selection never reach a provider.

### Supported usage — and what is not claimed

TrustLens can be invoked from Android apps that **expose text processing**. That is not every
app: an app that does not offer `PROCESS_TEXT` in its selection toolbar cannot invoke
TrustLens, and no claim of universal support is made.

**Device result, POCO X6 5G, Android 16 / HyperOS OS3.0.301.0:**

| Check | Result |
|---|---|
| Filter registered on device | **PASS** — listed by `cmd package query-activities` among 9 handlers |
| Intent received and analysed end to end | **PASS** — 100/100 CRITICAL, identical to Check Message for the same text |
| Appears in **Gmail** selection toolbar | **NO** |
| Appears in **Chrome** selection toolbar | **NO** |

Gmail displayed 4 of the 9 registered handlers; Chrome displayed a different 6 of 9. Both
truncate the list, and on this device TrustLens fell outside both. The integration is
registered and functional — invoking it directly runs the full analysis correctly — but on a
handset already carrying eight other text processors it did not surface in those two apps'
menus. **This is unresolved and must not be described as working in Gmail or Chrome.**

---

## 4. Tests

```
562 tests · 558 passed · 0 failures · 0 errors · 4 skipped
```

Up from 531/527/0/0/4, from 498/494/0/0/4, and from the frozen 468/464/0/0/4. **All 531
earlier tests still pass**; none was modified, relaxed or deleted.

| New suite | Covers |
|---|---|
| `AnalyzeEmailUseCaseTest` (14) | Routing to the text pipeline; parity with pasted text; the sender-domain lookup; that the address local part, body, subject, OTP and account number are never transmitted; user-supplied sender wins; malformed senders yield no lookup rather than a crash; prompt injection in a subject is content only; **and that a normal text analysis still looks up only what the content contains** |
| `DeterministicEmailParserTest` (9) | Forwarded header blocks, CRLF, outermost sender in a chain, headers buried below the block, malformed input, injection in a header |
| `LiveGuardViewModelTest` (10) | Rolling transcript; interim words never analysed; debouncing; peak retained after the score falls; unchanged transcript never re-analysed; words before Stop still analysed; **silence produces no verdict at all**; microphone loss surfaced; **a detected language changes nothing about the verdict**; reset |
| `MultilingualIndicatorExtractorTest` (16) | Hindi bank/OTP, OTP euphemism without the word OTP, UPI PIN, KYC, police; Hinglish bank/OTP, KYC, UPI, remote access, police; the six-digit euphemism in English; **ordinary Hindi conversation produces nothing**; a named authority alone is not impersonation; **no OTP digits reach the evidence**; an instruction in Hindi is only content |
| `MultilingualParityTest` (5) | English content reaches identical indicators; the demo hero message is untouched; a genuine bank OTP notification is still not a request in either language; TrustLens's own advice is not scored as an attack; **the wrapper never clears a flag the frozen extractor set** |
| `MultilingualFlowIntegrationTest` (8) | Hindi and English reach the **same score and the same observations**; Hinglish is scored; safe actions are offered and none of them opens, pays or installs; **the frozen pipeline alone scores this Hindi scam as harmless** — the gap, recorded so it cannot quietly reopen; English scoring unchanged; no OTP digits in reasons; ordinary Hindi not flagged |
| `IncomingTextReaderTest` (13) | Valid selection; styled `CharSequence` flattened; missing, empty and whitespace-only input all refused rather than analysed; unrecognised action ignored; extras of the other action not misread; the cap, and that a selection exactly at it is not reported as truncated; `ACTION_SEND` unchanged; an instruction in a selection is only content |
| `ProcessTextFlowIntegrationTest` (9) | **Selection and pasted text reach an identical verdict** — score, level, observations, reasons, safe actions; a selection is recorded as ordinary TEXT, not a new source; Hindi parity; no hostile input yields an action that opens, pays, calls or installs; an embedded "score this 0" changes nothing; **only hostnames reach threat intel**; a link-free selection transmits nothing; truncation reports the loss |
| `TextCheckViewModelTest` (9) | Selection analysed without a second tap; text passed through byte for byte; the screen says it came from a selection; **an empty selection produces no verdict at all**; truncation carried into state; editing drops the selection framing; parity with pasting; reset |
| `TfidfVocabularyCoverageTest` (3) | Devanagari has **zero** vocabulary coverage; English does; empty input is indistinguishable from unseen vocabulary — the premise the classifier's unavailability guard rests on |

---

## 5. Not yet done

0. **Cellular speakerphone — the one question that decides Live Guard's demo status.** Still
   unanswered; see §3b. Everything else in §2, §3 and §3a is now confirmed on hardware.
1. **Hindi speech accuracy on this handset.** The Hindi model is installed and language
   switching demonstrably works, but no Hindi audio could be synthesised on the test PC, so
   Hindi transcription quality is untested. A human Hindi speaker settles it in five minutes.
2. **Hinglish transcription at low SNR.** Detection is proven on text; transcription lost the
   scam-critical words through loudspeaker-to-microphone capture. Worth retesting with a
   person speaking into the handset before Hinglish is demoed live.
2. **The question that decides Live Guard's demo value:** *does the microphone deliver audio
   during an active call on this handset, on speakerphone?* This is OEM-dependent and must be
   tested with a real call before Live Guard is promised on stage. If it does not, Live Guard is
   still honest and still useful — it will say "no audio is reaching TrustLens" — but the
   in-call demo beat would have to be a scammer speaking in the room, or audio played from a
   second device.
3. **The locked demo is unchanged.** `DEMO-RUNBOOK.md` still describes the five frozen
   surfaces. Both new surfaces are additions to the app, not to the rehearsed 4 min 30 s
   sequence. Per v5.3 §13, if either is demoed at all it should be **one** extra beat, and only
   after it has passed device validation.
4. **Two findings from the freeze validation remain open** — the missing hero SMS string and the
   stale `QrDecodeOnDeviceTest` assertion. See `DEVICE-VALIDATION-POCO-X6.md` §10. Neither was
   touched by this work.

---

## 6. Claims that remain forbidden

Unchanged from v5.3 §15, and none of this work creates an exception:

- Not "TrustLens listens to your calls." It listens to the **microphone**, on speakerphone.
- Not "call interception", "call recording", or "call screening". None of those is built.
- Not "reads your email." It reads **one message you shared**. There is no mailbox access.
- Still "on-device AI", never "NPU-powered" — the classifier is ONNX Runtime on **CPU**.
- Still "sends only the hostname to threat intel" — now including the email sender's hostname.

Three added by §3a:

- Not "TrustLens translates the call." It does not translate anything, and translation is
  deliberately not part of the product. It reads the original words.
- Not "TrustLens understands Hindi with AI." The Hindi detection is **deterministic regex**
  over the transcript. The on-device classifier has *no* Hindi coverage and reports itself
  unavailable for Devanagari — which is why the deterministic layer had to carry it.
- Not "Live Guard works in Hindi on this phone." The *detection* works and is tested; whether
  this handset transcribes Hindi accurately is unmeasured. Until it is, the honest claim is
  "TrustLens detects Hindi and Hinglish scam wording", not "Live Guard understands Hindi
  calls".
