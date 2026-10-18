# TrustLens — Demo Input Pack

**Every file in this directory is a DEMO INPUT. All content is synthetic.**

These are the exact strings used in `../TRUSTLENS-DEMO-GUIDE.md`. They exist so a demo can be
reproduced from a fresh clone without anyone retyping a scam from memory on stage.

## Safety rules this pack follows

- No real person's name, phone number, address or email address.
- No real OTP. Where a six-digit number appears it is invented, and it is there to demonstrate
  that TrustLens **does not** echo it back into the evidence.
- No real credentials, card numbers, UPI handles belonging to anyone, or payment details.
- No live malicious link. Phishing hosts use `.tk` or `example.*` reserved/parked namespaces,
  which are safe to display and are never opened by the app in any case.
- No executable download link is used as a live input.

## What each expectation means

The guide labels every expected result with how it is known:

| Label | Meaning |
|---|---|
| **exact** | ML does not run for this input, so the deterministic score is reproducible to the point. |
| **band** | The risk band is stable; the exact number depends on the on-device model and is not pinned. |
| **recorded** | A number captured in an earlier device run. Recorded validation evidence — not a live lookup. |

## Local vs external evidence

TrustLens separates the two on screen and this pack keeps that separation:

- **Deterministic / local** — structure of the link, wording of the message. Runs fully offline.
  Every expectation in this pack is satisfied by this layer alone.
- **External corroboration** — VirusTotal and URLhaus, hostname-only. It can **raise** a score,
  never lower one, and it may legitimately return "no report" for any of these hosts.

**No file in this pack promises a live third-party verdict.** If a provider happens to answer
during the demo, that is a bonus beat, not the scripted one.

## Files

| File | Asset | Surface |
|---|---|---|
| `A-scam-sms.txt` | Scam SMS — bank impersonation | Check Message |
| `B-benign-sms.txt` | Benign SMS | Check Message |
| `C-genuine-url.txt` | Genuine bank domain | Check Link |
| `D-phishing-url.txt` | Brand-impersonating phishing link | Check Link |
| `E-scam-email.txt` | Scam email (subject + sender + body) | Check Email |
| `F-benign-email.txt` | Benign email | Check Email |
| `G-hindi-scam.txt` | Hindi scam call script | Voice / Check Message |
| `H-hinglish-scam.txt` | Hinglish scam | Voice / Check Message |
| `I-voice-transcript.txt` | Phrase to read aloud | Voice / Live Guard |

Image inputs for Camera/OCR and QR already live in `../../tools/demo/assets/`.
