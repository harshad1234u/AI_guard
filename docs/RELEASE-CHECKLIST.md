# TrustLens — Release & Installation Checklist

Build recorded at commit `8b610a7`, on the working tree with only developer tooling
uncommitted. No source file was modified to produce it.

---

## 1. Build

```bash
./gradlew :app:assembleRelease
```

**Result: BUILD SUCCESSFUL.** Lint-vital clean. 465 actionable tasks.

| Property | Value |
|---|---|
| APK path | `app/build/outputs/apk/release/app-release.apk` |
| Size | **64,274,234 bytes** (61.30 MiB) |
| **SHA-256** | `1d5adee2650d6c54798195e0007727410d77dcdd1e44a118fae286b3f6565e67` |
| ABI | **`arm64-v8a` only** |
| Application id | `com.trustlens` |
| Version | `0.1.0` (versionCode 1) |
| minSdk / targetSdk / compileSdk | 26 / 36 / 37 |
| Minification | **off** (`isMinifyEnabled = false`) |
| Signing | **demo config — public Android debug keystore** |

Verify the checksum before installing:

```bash
sha256sum app/build/outputs/apk/release/app-release.apk
```

### Why the build is configured this way

- **`arm64-v8a` only** — takes the APK from ~203 MB to ~61 MiB. Add `x86_64` back to
  `abiFilters` if you need to run on an emulator.
- **R8 off, deliberately** — it would shrink the APK, but an untested shrink of a build using
  Hilt, ONNX Runtime and two ML Kit models is not something to discover on stage. Turn it on
  only with time to run a full device regression afterwards.
- **Debug keystore** — a release APK must be signed to install, and this build exists to be
  side-loaded onto a validation handset. **Not for distribution.**

---

## 2. Install

```bash
adb devices                                              # confirm exactly one device
adb install -r app/build/outputs/apk/release/app-release.apk
```

If a previous build with a different signature is present:

```bash
adb uninstall com.trustlens
adb install app/build/outputs/apk/release/app-release.apk
```

> **MIUI / HyperOS note:** these have been observed uninstalling the app after
> `connectedAndroidTest` and blocking test-APK installs. Install the release APK directly and
> do not run instrumented tests on the demo handset before the demo.

---

## 3. Device installation checklist

| # | Step | Done |
|---|---|:---:|
| 1 | Device is `arm64-v8a`, Android 8.0 (API 26) or newer | ☐ |
| 2 | SHA-256 of the APK matches the value above | ☐ |
| 3 | Previous TrustLens build uninstalled if signatures differ | ☐ |
| 4 | App launches to Home | ☐ |
| 5 | Camera permission granted (Camera/QR beats) | ☐ |
| 6 | Microphone permission granted (Voice / Live Guard) | ☐ |
| 7 | A speech recogniser is installed; Hindi pack present if beat 11 is used | ☐ |
| 8 | Share sheet shows TrustLens from a mail app | ☐ |
| 9 | Text-selection toolbar shows TrustLens in Gmail or Chrome | ☐ |
| 10 | `secrets.properties` present at build time if provider lookups are wanted | ☐ |

---

## 4. Final pre-demo checks

Run these on the handset, five minutes before going on.

| # | Check | Expected | Why it matters |
|---|---|---|:--|
| 1 | Paste `https://www.sbi.co.in` into Check Link | **0/100 LOW**, no observations | Exact and model-independent. If wrong, the installed build is not the audited one. |
| 2 | Paste `sbi.bank.tk/verify` into Check Link | **22/100 GUARDED**, two observations | Same. These two are the only numbers safe to say aloud. |
| 3 | Paste `demo-assets/A-scam-sms.txt` | **CRITICAL** | The opening beat. |
| 4 | Paste `demo-assets/B-benign-sms.txt` | **LOW**, no observations | Proves it is not a panic button. |
| 5 | Aeroplane mode on, repeat checks 1–2 | Identical results | Proves the core is offline. |
| 6 | Battery | > 50% | — |
| 7 | Screen timeout | 5 minutes or never | The reason list is the demo. |
| 8 | Brightness | High | — |
| 9 | Notifications | Silenced / DND | — |
| 10 | `demo-assets/` open on a laptop | for copy-paste | — |

**If check 1 or 2 fails, stop and reinstall.** Those two are exactly reproducible because no
model runs on either input; a wrong number means the wrong build.

---

## 5. After the event

- [ ] **Rotate the VirusTotal API key** — extractable from the release APK.
- [ ] **Rotate the URLhaus auth key** — same.
- [ ] Replace the debug keystore with a real release key before any distribution.
- [ ] Capture the outstanding screenshots — see
      [`SCREENSHOT-CHECKLIST.md`](SCREENSHOT-CHECKLIST.md).

---

## 6. Distribution

**The APK is not distributed or pushed anywhere.** It is built locally and side-loaded onto a
validation handset. `*.apk` is gitignored, so it is never committed. The debug-keystore signing
makes it unsuitable for any store or public download in its current form.
