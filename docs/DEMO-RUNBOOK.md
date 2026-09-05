# TrustLens — Demo Runbook (Phase 15 freeze)

Companion to `TrustLens_PRD_v5.3_FINAL.md` §13. This file closes the two reproducibility
gaps that v5.3 §11 (items 12 and 13) recorded as open blockers.

Everything the locked demo needs is now inside the repository. A fresh clone can run the
demo without reaching for files on one developer's machine.

---

## 1. What was missing, and where it is now

| v5.3 blocker | Status | Location in repo |
|---|---|---|
| §11.12 — `_toolchain/phase15/refresh-control.sh` absent | **Resolved** | `tools/demo/refresh-control.sh` |
| §11.13 — `ocr_scam.png` absent | **Resolved** | `tools/demo/assets/ocr_scam.png` |

Both existed only in a sibling `_toolchain/` directory outside the repository, so they were
absent from version control exactly as v5.3 stated. They have been imported, and the
refresh script rewritten to resolve paths relative to the repository root instead of a
hardcoded absolute path.

The five locked QR inputs and the Phase 14B device-run log were imported alongside them, so
the QR beat is reproducible on the same terms.

```
tools/demo/
  refresh-control.sh          demo-morning URLhaus refresh (query-only)
  phase14b-device-run.log     device evidence for the QR and OCR beats
  assets/
    ocr_scam.png              locked Camera/OCR input  -> 62/100 HIGH
    qr1_text.png              plain text               -> 12/100 LOW
    qr2_url.png               benign URL               ->  0/100 LOW
    qr3_upi_bare.png          bare UPI id              -> 10/100 LOW
    qr4_upi_request.png       UPI payment request      -> 35/100 GUARDED
    qr5_repeat.png            repeat of qr2 (debounce) ->  0/100 LOW
```

Total added: 228 KB. No secret, key, or training dataset is included.

---

## 2. Demo-morning procedure

Run this **on the morning of the demo**, before going on stage.

```bash
./tools/demo/refresh-control.sh github.com
```

It performs a hostname-only POST to the URLhaus host endpoint — the same request the app
makes. It never opens, fetches, resolves, or launches anything.

The URLhaus auth key is read from `secrets.properties` at run time. That file is gitignored
by design; recreate it locally if missing:

```
VIRUSTOTAL_API_KEY=<key>
URLHAUS_AUTH_KEY=<key>
```

**Interpreting the result:**

| Exit code | Meaning | What to do |
|---|---|---|
| `0` | An online listing exists | Use the printed URL for the exact-match beat |
| `1` | No online listing this morning | **Drop the exact-match beat.** Present the host-level case alone |
| `2` | Script/config error | Fix `secrets.properties` or network, then re-run |

> URLhaus listings rotate. A `CONFIRMED` exact-match result is **never** guaranteed and must
> never be scripted as a certainty. If the script exits 1, the honest move is to drop the
> step — not to assert a verdict you have not just verified.

*Last verified: 2026-09-06 — `github.com` returned `url_count=7939`, 19 online entries, so
the beat was available on that date. This says nothing about 12–13 September. Re-run it.*

---

## 3. Locked demo sequence

Target: **4 min 30 s**.

| # | Beat | Expected | Source of the number |
|---|---|---|---|
| 1 | Home | — | — |
| 2 | **Text hero** | 77/100 HIGH, reasons sum exactly, `DO_NOT_SHARE_OTP` | v4.0 device validation |
| 3 | **URL control** | `github.com` → LOW 18/100, host-level | Live URLhaus, host-level |
| 4 | **URL shared-host** | not `CONFIRMED` — host listed, this URL is not | Live URLhaus |
| 5 | **URL exact-match** | `CONFIRMED` — *only if step 2 above returned exit 0* | Live URLhaus |
| 6 | **Camera** | `ocr_scam.png` → 62/100 HIGH | `phase14b-device-run.log` |
| 7 | **QR** | qr1–qr5 as tabled in §1 | `phase14b-device-run.log` |
| 8 | **Voice** | 26/100 GUARDED, OTP +22, mic released after 5.9 s | v4.0 device validation |

**Cut-for-time order:** drop **Voice** first, then **QR**, then **Camera**.
**Never drop:** the Text hero or the URL pair (steps 3 and 4).

Steps 3 and 4 are the strongest part of the pitch — they demonstrate the shared-host
distinction that separates TrustLens from a naive blocklist lookup. Step 5 is a bonus.

---

## 4. Scores that cannot be re-verified from this repository

The JVM test suite wires a **mock** classifier, never the real ONNX model. The 468-test
suite therefore cannot reproduce any on-device score.

- **62/100 (OCR)** — independently corroborated by `phase14b-device-run.log`, where the
  stated reasons (+18 ML, +10 urgency, +22 OTP, +12 impersonation) sum exactly to 62.
- **12 / 0 / 10 / 35 / 0 (QR)** — corroborated by the same log.
- **77/100 (Text)** and **26/100 (Voice)** — documented in `TrustLens_PRD_v4.0_AS-BUILT.md`
  §9 device validation. No run log for these was found in the repository during the Phase 15
  audit. Re-run both on real hardware before the event rather than assuming them.

All of the above were measured on a **POCO X6 5G**, an interim stand-in. Re-measure on the
iQOO 15 on arrival, before any other work.

---

## 5. Standing rules during the demo

- The app never opens a link, pays, calls, or installs. If a judge asks it to, that is the
  answer — it is enforced by the absence of any such code path, not by a setting.
- "On-device AI." Not "NPU-powered." The classifier runs on the ONNX Runtime **CPU**
  execution provider; NPU is unvalidated.
- "Sends only the hostname to threat intel." Not "fully offline."
- If asked something outside `TrustLens_PRD_v5.3_FINAL.md`, the honest answer is a
  limitation from §11 — never an improvised claim.
