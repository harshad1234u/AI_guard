# TrustLens — Screenshot & Visual Asset Checklist

## Current state, stated plainly

**No UI screenshots exist in this repository.** Not one.

Every image currently tracked is a demo *input*, not a screenshot of the app:

| Existing asset | What it actually is |
|---|---|
| `tools/demo/assets/ocr_scam.png` | A scam message image, used as OCR **input** |
| `tools/demo/assets/qr1_text.png` | QR encoding plain text — **input** |
| `tools/demo/assets/qr2_url.png` | QR encoding a benign URL — **input** |
| `tools/demo/assets/qr3_upi_bare.png` | QR encoding a bare UPI id — **input** |
| `tools/demo/assets/qr4_upi_request.png` | QR encoding a UPI payment request — **input** |
| `tools/demo/assets/qr5_repeat.png` | Repeat of qr2, for debounce — **input** |

There is also `tools/demo/phase14b-device-run.log` — a **text** log from the Phase 14B device
run (v4.0 era, POCO X6). It is recorded evidence, not a screenshot, and it predates P0/P1.

> Do not describe the repository as having screenshots. It does not. Every row in the checklist
> below is outstanding, and every one requires a physical device.

---

## Capture checklist — all outstanding, all device-dependent

Capture in both themes. The design system defines light and dark palettes, and neither has been
photographed.

### Screens

| # | Screen | Light | Dark | Suggested input | Priority |
|---|---|:---:|:---:|---|---|
| 1 | Home | ☐ | ☐ | — | **P0** |
| 2 | Text result — CRITICAL | ☐ | ☐ | `demo-assets/A-scam-sms.txt` | **P0** |
| 3 | Text result — LOW | ☐ | ☐ | `demo-assets/B-benign-sms.txt` | P1 |
| 4 | URL result — LOW, genuine domain | ☐ | ☐ | `demo-assets/C-genuine-url.txt` | **P0** |
| 5 | URL result — GUARDED, phishing | ☐ | ☐ | `demo-assets/D-phishing-url.txt` | **P0** |
| 6 | Check Email | ☐ | ☐ | `demo-assets/E-scam-email.txt` | P1 |
| 7 | Camera / OCR capture | ☐ | ☐ | `tools/demo/assets/ocr_scam.png` | P1 |
| 8 | QR result | ☐ | ☐ | `tools/demo/assets/qr4_upi_request.png` | P1 |
| 9 | Voice Check | ☐ | ☐ | `demo-assets/I-voice-transcript.txt` | P2 |
| 10 | Live Guard — mid-call, score climbing | ☐ | ☐ | speakerphone | P2 |
| 11 | Process Text — selection toolbar + result | ☐ | ☐ | select text in Gmail/Chrome | P1 |
| 12 | Hindi / Hinglish result | ☐ | ☐ | `demo-assets/G-hindi-scam.txt` | P1 |

### Detail shots worth having

| # | Shot | Done | Why it earns space |
|---|---|:---:|---|
| 13 | Reason list with per-reason weights | ☐ | The explainability claim, made visible |
| 14 | "Classifier was not run" disclosure on a bare URL | ☐ | The P1 story in one image |
| 15 | Safe-action list | ☐ | Proves every action is decline/defer |
| 16 | Threat intel reported **unavailable** | ☐ | Proves unknown ≠ safe (use aeroplane mode) |
| 17 | Side-by-side: `sbi.co.in` 0/LOW vs `sbi.bank.tk` 22/GUARDED | ☐ | **The single best image in the deck** |

**P0 = needed for the README and the deck.** Rows 1, 2, 4, 5 and detail shot 17 are the minimum
viable set.

---

## Capture protocol, when a device is available

1. Install the checksummed release APK — see [`RELEASE-CHECKLIST.md`](RELEASE-CHECKLIST.md).
2. Record the device model, Android version and the commit hash on the build.
3. Aeroplane mode **on** for rows 4, 5 and 16, so no external evidence is present and the
   numbers are the exact reproducible ones.
4. Verify before capturing: row 4 must read `0/100 LOW`, row 5 must read `22/100 GUARDED`. If
   not, the installed build is not the audited one — stop.
5. `adb exec-out screencap -p > name.png`, or the device's own screenshot gesture.
6. Suggested location: `docs/screenshots/<light|dark>/<NN>-<name>.png`.
7. Note the capture date and commit in a caption. A screenshot from a different commit must be
   labelled as such.

## Rules for using these images

- Never label a mockup as a screenshot.
- Never present a screenshot from an earlier commit as current without saying so.
- Any image showing external threat-intel evidence must state whether the lookup was live at
  capture time.
- Recorded material introduced in a demo carries the line: *"Recorded validation evidence — not
  a live lookup."*
