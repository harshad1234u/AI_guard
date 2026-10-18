# TrustLens — Judge Q&A

Answers reflect the implementation at commit `8b610a7`. Every claim here is traceable to code
or to a test. Where something is not validated, the answer says so.

**Three sentences that must never be said:** that it runs on the NPU; that TrustLens is
"98.65% accurate at detecting scams"; that any figure was validated on hardware that has not
actually run it.

---

### 1. What problem are you solving?

The dangerous moment is *before* the click. By the time a message is reported, the OTP is
already shared and the money is gone. Most people genuinely cannot tell a real bank alert from
a good forgery in the ten seconds they spend on it. TrustLens is a decision layer for that ten
seconds: paste it, point at it, or share it in, and get an explained verdict before acting.

### 2. What makes TrustLens different?

Most scam detectors stop at detection — a label, a score, a red banner. TrustLens
*investigates*: it extracts what the message actually asks for, analyses the link's structure
without opening it, optionally corroborates the host against external reputation, and then
scores all of it with a deterministic rubric where every point traces to a stated sentence.
The user sees the reasoning, not just the conclusion.

### 3. Why use ML?

Because wording generalises in ways rules do not. A classifier picks up the shape of
scam phrasing — the register, the pressure, the combinations — including phrasings nobody wrote
a rule for. It is a genuine signal, so we use it. It is also a *fallible* signal trained on one
corpus, so it is capped at 30 of 100 and never decides alone.

### 4. Why deterministic rules?

Three reasons. **Explainability:** "requests an OTP, +22" is a sentence a user can act on; a
logit is not. **Auditability:** every number lives in one file, `RiskWeights.kt`, and can be
reviewed in five minutes. **Reproducibility:** the risk engine is pure Kotlin with no IO and no
randomness, so the same input always yields the same score. A model cannot promise that across
versions.

### 5. Why threat intelligence?

Because some things simply cannot be known from a string. If a host is already in a malware
feed, that is real-world evidence no amount of local analysis produces. It is corroboration
layered on top of a verdict we already have.

### 6. Why not just VirusTotal?

Because a brand-new phishing domain has no reputation history. A campaign launched this
morning is invisible to every feed for hours or days — exactly the window that matters. Our
demo proves the point: `sbi.bank.tk/verify` reaches GUARDED at 22/100 with **zero** external
evidence, purely from structure. If TrustLens could only recognise what a provider had already
heard of, it could not protect anyone from a fresh campaign.

### 7. Does TrustLens open suspicious links?

No — and this is enforced by absence, not by a check. There is no `startActivity`, no
`ACTION_VIEW`, no `WebView`, no custom-tabs dependency, and no URI handler anywhere in the
codebase. A whole-repo grep returns matches only inside comments that assert the absence. URL
analysis is pure string and structure work: nothing is resolved, fetched, or rendered.

### 8. Does user message data leave the phone?

No. The message body — names, amounts, OTPs, account numbers — never leaves the device. The
only thing transmitted is a hostname, and only when a reputation lookup is configured and
online.

### 9. What data is sent externally?

Hostnames. Nothing else. `AnalyzeTextUseCase.lookupSubjects()` reduces everything found to a
deduplicated list of hosts. Paths and query strings are **dropped** — a phishing URL's path
routinely carries a per-victim token, and forwarding it would leak both the token and the fact
that this user received that message. For email, only the domain after the `@`; the local part
never leaves. Concretely: URLhaus receives a form with one field, `host`; VirusTotal receives
`GET /api/v3/domains/{domain}`.

### 10. Why is hostname lookup safe?

The hostname is the attacker's infrastructure, not the user's secret. We keep the full host
rather than collapsing to the registrable domain — attackers put the brand in the subdomain,
and providers track hosts as objects in their own right — but everything identifying the *user*
or the *message* is already gone by that point. There is nothing in a bare hostname that says
who asked about it or why.

### 11. What happens when threat intelligence is unavailable?

It is reported as unavailable, on screen. Offline, unconfigured, rate-limited, timed out and
"no report" all map to `NO_DETECTION`, `UNKNOWN` or `ERROR` — and all three contribute exactly
**zero**. They never subtract. A silent provider can never launder risky content into a lower
score. "We found nothing" is not "there is nothing."

### 12. Why does ML not make the final decision?

Because it is one narrow signal with a known domain mismatch, and because a verdict a user
cannot interrogate is a verdict they cannot act on. The classifier contributes at most 30 of
100, scaled by its probability. The remaining 70+ comes from what the message demands and what
the link is. The deterministic engine produces the final number.

### 13. How do you handle false positives?

Structurally, by requiring *specificity* rather than keyword presence. The brand-impersonation
rule matches whole hostname labels, never substrings — `sbicards.com` is a real SBI product and
`basbi.com` is somebody else, so a `contains("sbi")` rule would call both phishing. The
free-TLD rule covers exactly five TLDs that were given away with no verification; `.xyz`,
`.top` and `.online` are deliberately excluded because they carry substantial legitimate use.
Benign-input tests are treated as first-class: `github.com`, `example.com/about` and a small
`.in` blog must all produce **zero** observations, and ordinary Hindi conversation must score
LOW.

### 14. How do you handle false negatives?

By layering independent signals so no single miss is fatal, and by never letting silence read
as safety. The bands are calibrated conservatively — GUARDED starts at 20, which two structural
observations reach on their own. Known gaps are documented in
[`TRUSTLENS-LIMITATIONS.md`](TRUSTLENS-LIMITATIONS.md) rather than hidden. The honest position:
we do not claim recall we have not measured on Indian scam data, because we have not measured it.

### 15. Why does a bare URL not use ML?

Because there is no message wording for the classifier to read, so any probability it returned
would be an artefact of its priors rather than a measurement of the content. Measured on the
shipped model: `https://www.sbi.co.in` — the genuine bank — scored **p=0.8888** and contributed
**+26**, while `sbi.bank.tk/verify` scored **p=0.1729** and contributed **+5**. The model ranked
the real bank as five times more scam-like than the phish impersonating it, because a bare
hostname is far outside its SMS training distribution. So a link with no message around it is
now assessed the way the Check URL screen always did: structure, plus reputation, scored by the
engine. The guard uses the extractor's own URL spans — it cuts them out of the text and checks
whether a single letter or digit survives — so it can never disagree with what TrustLens
considers a link. Wrap any wording around the link and the model runs normally.

### 16. How does multilingual detection work?

A deterministic decorator, `MultilingualIndicatorExtractor`, wraps the English extractor and
adds Hindi, Hinglish and Indian-English patterns. It only ever *adds* flags, so English scoring
is unchanged — a parity test pins that against the bare extractor. No model, no translation
service, no network. The measurement that justifies it: a complete Hindi bank-impersonation
script produced every indicator false and scored 3/100 LOW through the frozen extractor. It
also handles the euphemism a caller switches to once they learn that saying "OTP" ends the
call — "the six-digit number that just arrived."

### 17. How does Live Guard work?

Speech is transcribed on the device via Android's `SpeechRecognizer`, with
`EXTRA_PREFER_OFFLINE` requested, and the running transcript is fed into the same
`AnalyzeTextUseCase` every other surface uses, re-scoring as the call develops. No audio is
recorded to disk and none is uploaded — the module contains no network or file-writing code at
all. It is not a call recorder: it listens on speakerphone, which is a deliberate limitation
(see Q28 and the limitations doc).

### 18. How does Email work without mailbox access?

Android's share sheet. The user picks one message in their mail app and shares it to
TrustLens; we receive the subject and body as intent extras and nothing else. There is also a
text-selection entry point (`ACTION_PROCESS_TEXT`) so a single suspicious line can be checked
from inside Gmail or Chrome without leaving that app. TrustLens is a read-only processor
there — it deliberately never calls `setResult`, so it cannot alter the user's mail or page.

### 19. Why no Gmail OAuth?

Because it is the wrong trade. Mailbox access would mean an account link, a token to store, a
scope covering *every* message, and a much larger blast radius if anything went wrong — in
exchange for convenience we get from the share sheet for free. Sharing one message is a clear,
revocable, per-message consent that a user actually understands. There is no OAuth code, no
Gmail API dependency and no account concept anywhere in the app.

### 20. What is the NPU roadmap?

Today the classifier runs through ONNX Runtime **on CPU**, with default session options and no
execution-provider configuration. `OnnxScamClassifier` hardcodes and reports
`InferenceBackend.CPU`, and the UI shows the backend that actually executed. The roadmap is to
add a QNN / NNAPI execution provider and validate it on iQOO hardware. **We claim no NPU
execution and have measured none.** The `InferenceBackend` enum has NPU and GPU values so the
result screen can report a backend honestly once one exists — the selection policy is not
written yet.

### 21. What is the current model?

TF-IDF (unigram + bigram, ~12,000 vocabulary terms) feeding a logistic regression, exported to
ONNX. Model version `uci-sms-tfidf-logreg-v1`. The vectoriser is reimplemented in Kotlin; a
parity test holds the maximum probability delta against the Python original at 1.2e-07 with
zero label disagreements across 1,115 messages. A protobuf parse confirms the graph is a single
`LinearClassifier` node. It is roughly 120 KB and needs no network.

### 22. What are the model's limitations?

Domain mismatch, and we are direct about it. It was trained on UK English SMS from around 2011.
It has never seen a UPI handle, an Indian bank name, or a KYC scam. It has no Devanagari — for
Hindi the feature vector is all zeros, and it reports itself unavailable rather than returning
its prior. Its worst measured failure is the one P1 fixes: it scored the genuine
`www.sbi.co.in` at 0.8888. That is why it is capped at 30 of 100 and why the deterministic
layer carries the Indian-scam patterns.

### 23. What is the current benchmark?

> **98.65% held-out accuracy on the UCI SMS Spam Collection** — 5,574 real labelled SMS,
> 1,115 in the test split. Precision 0.959, recall 0.940, F1 0.949, ROC-AUC 0.992.

That is a **component-level result on a public English spam corpus**, spam-vs-ham — it is not
TrustLens's scam-detection accuracy, and it must never be quoted as one. It measures one input
worth at most 30% of the final score. TrustLens has no end-to-end accuracy figure because we
have not built a labelled Indian scam set to measure one against.

### 24. How would you improve the model after the hackathon?

In priority order: (1) build a labelled Indian scam corpus — UPI, KYC, delivery, job-fraud,
lottery — which is the actual bottleneck, not the architecture; (2) retrain with Devanagari and
transliterated Hinglish in the vocabulary so Hindi gets a model signal instead of only rules;
(3) measure end-to-end precision and recall on that corpus, so we can finally quote a number
that means what people assume it means; (4) only then look at a larger architecture, and only if
the data justifies it.

### 25. What happens if the scam URL is unknown to threat intelligence?

Exactly what the demo shows. `sbi.bank.tk/verify` reaches **22/100, GUARDED** with no external
evidence at all, from two structural observations: a free-registration `.tk` domain (+10) and a
brand label that does not match the registrable domain (+12). Unknown contributes zero — it
never reduces the local score.

### 26. Can the system automatically block, pay, or report?

No. The `SafeAction` enum has exactly seven members and every one is decline or defer: do not
open the link, do not pay, do not share your OTP or PIN, do not share account credentials,
verify through the official app or helpline, report or block the sender, proceed carefully.
There is no case that opens, pays, sends or installs anything. "Report or block the sender" is
advice shown to the user, not an action the app takes. This is enforced by the absence of such
a case, and a test asserts the enum contains no `OPEN_URL` or `VISIT_LINK`.

### 27. What happens if the AI is wrong?

Then at most 30 points are wrong, and the user can see exactly which sentence they came from
and disagree with it. That is the whole reason the score is a transparent rubric rather than a
model output. And because every recommended action is "don't" or "verify independently", the
cost of a false positive is a moment of caution — not a blocked payment or a deleted message.
When the model cannot assess something it says so, rather than returning a number that looks
like a finding.

### 28. Why is this suitable for a smartphone?

The phone is where the scam arrives — the SMS, the QR, the call, the shared mail. It is also
where the sensitive content lives, which is the argument for analysing locally rather than
uploading. The whole analysis path runs offline: rules, the 120 KB classifier, OCR and QR
decoding are all on-device, so the verdict appears without waiting for a round trip that might
never complete on a bad connection. Cloud is enrichment; the app is fully functional in
aeroplane mode.

### 29. What is your privacy architecture?

Four properties, all enforced in code. **Content is data, never instruction** — the message is
carried as an opaque value and never interpolated into any model's instruction context.
**Minimum egress** — hostnames only, paths and queries dropped, message body never transmitted.
**No silent safety** — unavailable evidence is reported as unavailable and weighted zero.
**No outward actions** — no link opening, no WebView, no process execution, no payment, no
mailbox. Full detail in [`TRUSTLENS-SECURITY.md`](TRUSTLENS-SECURITY.md).

### 30. What is your biggest current limitation?

Two, honestly. **First:** the classifier's training corpus does not match its deployment
domain — UK English SMS from 2011 against Indian scams in 2026. The deterministic layer
compensates, but that is a patch over a data gap, and closing it needs a labelled Indian corpus
we do not yet have. **Second:** the current build has not been validated on physical hardware.
The software checkpoint is verified — 606 tests, 602 passing, release build green — but no
handset has run this exact commit, and we are not claiming otherwise.
