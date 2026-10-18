# TrustLens — Security & Privacy Guarantees

Guarantees verified at commit `8b610a7`. Each row states **what is guaranteed**, **how it is
enforced**, and **how to check it yourself**. Nothing here is aspirational; anything not yet
true lives in [`TRUSTLENS-LIMITATIONS.md`](TRUSTLENS-LIMITATIONS.md) instead.

Verification greps below cover tracked **and** untracked sources
(`git grep --untracked`), so they include every file in the working tree.

---

## 1. The app performs no outward action

| Guarantee | Enforcement | Verify |
|---|---|---|
| Suspicious links are never opened automatically | No `startActivity` call exists anywhere | `git grep -n --untracked "startActivity" -- '*.kt'` → matches in comments only |
| No link can be opened *at all*, even manually | No `ACTION_VIEW`, no `UriHandler`, no `openUri`, no `LinkAnnotation`, no `ClickableText`, no custom-tabs dependency | `git grep -n --untracked "ACTION_VIEW\|UriHandler\|openUri\|LinkAnnotation" -- '*.kt'` → comments only |
| No WebView | The class is never imported or referenced | `git grep -ni --untracked "webview"` → comments only |
| No shell or process execution | No `Runtime.getRuntime`, no `ProcessBuilder` | `git grep -n --untracked "Runtime.getRuntime\|ProcessBuilder" -- '*.kt'` → **zero matches** |
| No automatic payment | No payment SDK, no UPI intent, no payment action in the codebase | UPI handles are extracted as *evidence only* |
| No automatic external action of any kind | The app starts nothing and returns nothing | `ProcessTextActivity` deliberately never calls `setResult`, so it cannot even write back into the app the text came from |

**`SafeAction` is decline or defer only.** Exactly seven members, and every one is a refusal or
a deferral:

```
DO_NOT_OPEN_LINK · DO_NOT_PAY · DO_NOT_SHARE_OTP · DO_NOT_SHARE_CREDENTIALS
VERIFY_VIA_OFFICIAL_CHANNEL · REPORT_OR_BLOCK · PROCEED_CAREFULLY
```

There is no case that opens, pays, sends or installs. This is enforced by the *absence* of such
a case rather than by a runtime check, and a test asserts the enum contains no `OPEN_URL` or
`VISIT_LINK`. "Report or block the sender" is advice rendered to the user — the app performs no
reporting or blocking itself.

---

## 2. Minimum data egress

**What leaves the device: hostnames. Nothing else.**

| Rule | Where |
|---|---|
| Only registrable hosts are sent for lookup | `AnalyzeTextUseCase.lookupSubjects()` |
| Paths and query strings are dropped | Same — a phishing path routinely carries a per-victim token; forwarding it would leak the token *and* the fact this user received that message |
| The message body is never transmitted | Names, amounts, OTPs, account numbers stay local |
| Email: only the domain after `@` | `AnalyzeEmailUseCase.domainOf()` — the local part never leaves |
| Hosts are deduplicated | A message repeating a link five times costs one lookup per provider |

**Exactly what each provider receives:**

| Provider | Request | Payload |
|---|---|---|
| URLhaus | `POST https://urlhaus-api.abuse.ch/v1/host/` | one form field: `host` |
| VirusTotal | `GET https://www.virustotal.com/api/v3/domains/{domain}` | the domain in the path |

**No OTP, payment or account text reaches a threat-intel provider.** Those live only in the
message body, which is never part of a lookup. URLhaus's adapter also accepts an `observedUrls`
list — that is compared **locally** and is never transmitted; it exists so a response that
enumerates offending URLs can distinguish "this link is listed" from "something on this host is
listed" without sending the link.

**Runs fully offline.** Rule extraction, URL structure analysis, the on-device classifier, OCR
and QR decoding need no network. Threat intelligence is enrichment, not a dependency.

---

## 3. Unknown is never treated as safe

Absence of evidence is never scored as evidence of absence. In `RiskWeights.forEvidence`:

```
NO_DETECTION -> 0     UNKNOWN -> 0     ERROR -> 0
```

They contribute exactly zero and **never subtract**, so an offline, rate-limited, misconfigured
or silent provider can never launder risky content into a lower score. Providers must map "no
report", rate limiting, timeouts and offline conditions to one of those three — never to an
absence of evidence and never to safety.

When a lookup could not happen, `threatIntelUnavailable` is set and the result reports itself
as degraded on screen, rather than quietly passing for a clean result.

**Threat intelligence is corroboration, not a prerequisite.** `sbi.bank.tk/verify` reaches
22/100 GUARDED with zero external evidence — asserted by test, because a brand-new phishing
domain has no reputation history and a tool that needs one cannot protect anyone from a fresh
campaign.

---

## 4. The classifier is one signal, never the verdict

| Property | Value |
|---|---|
| Maximum ML contribution | **30 of 100** (`RiskWeights.ML_SIGNAL_MAX`), scaled by probability |
| Who produces the final score | `RiskEngine` — pure Kotlin, no IO, no randomness |
| Where every number lives | `RiskWeights.kt`, one file, additive then capped at 100 |
| Reproducibility | Identical input always yields an identical score |

**No result is ever fabricated.** `ClassificationResult` is a sealed type with an explicit
`Unavailable(reason)` case, and an unavailable classifier leaves `mlSignal` null — not 0.0. The
engine only emits an ML observation when a signal actually exists, so a model that did not run
contributes nothing and is *reported* as not having run. Two cases take that path deliberately:

- **A link with no message around it** (P1) — no wording to read, so the model is not asked.
- **Text with no vocabulary coverage** — an all-zero feature vector would return the model's
  intercept, identical for every such input, so it reports unavailable instead.

A classifier that could not assess something must never look like a classifier that assessed it
and found nothing.

---

## 5. Content is data, never instruction

`InvestigationContext.rawContent` is untrusted user-supplied data. It is carried as a value to
be analysed and displayed — never interpreted as an instruction, however imperative it sounds,
and never interpolated into any model's instruction context. The use case creates no findings
of its own: observations come from the structured extractor, external evidence from reputation
sources, and the classifier's output travels only in the typed `mlSignal` field.

There is no cloud LLM, no prompt, and no external AI API in the codebase.

---

## 6. Permissions

| Permission | Why |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Optional threat-intel lookups only |
| `CAMERA` (feature not required) | OCR and QR, both decoded on-device |
| `RECORD_AUDIO` (feature not required) | Voice Check and Live Guard, transcribed on-device |

Plus a `<queries>` entry so `SpeechRecognizer.isRecognitionAvailable` can see installed
recognition services. **No new permission was added in Phase 16.** There is no contacts, SMS,
storage, location, or accounts permission — and no mailbox or OAuth scope of any kind.

Both intent filters (`ACTION_SEND` for email, `ACTION_PROCESS_TEXT` for selected text) are
**input only**: content comes in, a verdict goes to the screen. Neither grants TrustLens any
ability to send, open or launch anything.

---

## 7. Known security gaps

Stated here rather than buried, and expanded in
[`TRUSTLENS-LIMITATIONS.md`](TRUSTLENS-LIMITATIONS.md):

1. **API keys are extractable from the release APK.** Inherent to `buildConfigField`;
   empirically confirmed for the VirusTotal key. Mitigations: free-tier keys, no billing, R8
   available but deliberately off. **Rotate both keys after the event.** The only real fix is a
   server-side proxy, which is out of scope.
2. **The release APK is signed with the public Android debug keystore.** Fine for side-loading
   at a hackathon; must be replaced before any distribution.
3. **The current commit has not been validated on physical hardware.**

---

## Reproducing this audit

```bash
git grep -n --untracked "ACTION_VIEW\|startActivity\|UriHandler\|openUri" -- '*.kt'
git grep -ni --untracked "webview" -- '*.kt' '*.xml'
git grep -n  --untracked "Runtime.getRuntime\|ProcessBuilder" -- '*.kt'
git grep -n  --untracked "oauth\|gmail\|mailbox" -- '*.kt'
git diff v4.0-freeze --stat -- riskengine/ threatintel/     # expect: empty
./gradlew test                                              # 606 tests, 602 pass, 4 skipped
```

The four skipped tests are `LiveProviderVerificationTest` — they make real network calls to
VirusTotal and URLhaus and are gated off by default.
