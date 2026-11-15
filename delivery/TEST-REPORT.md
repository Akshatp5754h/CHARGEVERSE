# CHARGEVERSE — QA & Delivery Report (Sprint 10)

Date: 2026-11-15 · Built on Windows 11 Pro (laptop) · Toolchain: Gradle 9.6.0, AGP 9.5.1 (built-in Kotlin), OpenJDK 25.0.4 (Studio JBR)

## 1. Artifacts

| File | Size | SHA-256 |
|---|---|---|
| `CHARGEVERSE-1.0.0-release.apk` | 4.88 MB | `F50119275885B48103E3CC0EF3879930C6507BB71F361BB7222F3F1E60BDD5F5` |
| `CHARGEVERSE-1.0.0-debug.apk` | 43.69 MB | `413E0F48BF20E6B590E61DEBF5DFD3624AE05EA625B9B1F80EDFD53BE26E56CB` |

Verified with `aapt dump badging`:
- package: `com.akshat.chargeverse` (release) / `com.akshat.chargeverse.debug` (debug)
- versionCode 1, versionName 1.0.0, minSdk 29, targetSdk 36, compileSdk 37
- native-code: arm64-v8a, armeabi-v7a, x86, x86_64
- Signed release: `apksigner verify --print-certs` → `CN=Chargeverse, OU=Personal, O=Akshat, C=IN` (self-generated local key; keystore + password kept **uncommitted** in `keystore.properties` / `keystore/`)

## 2. Build verification (real logs)

- `assembleDebug` → BUILD SUCCESSFUL
- `testDebugUnitTest` → BUILD SUCCESSFUL — **10 tests, 0 failures, 0 errors** (JUnit XML in `test-report/`)
  - `BatteryMathTest` (7 tests): charging-power unit math (mV×µA→mW), ETA computation incl. zero-remaining and invalid-input cases, charging-transition detection
  - `AutomationRuleTest` (3 tests): trigger semantics, thresholds, one-shot firing timestamps
  - Notably, the unit test suite caught and fixed a real unit-conversion bug in charging-power math (mV·µA is nanowatts; divisor must be 1e6, not 1e3)
- `lintDebug` → BUILD SUCCESSFUL, 0 errors, 2 benign warnings (documented below)
- `assembleRelease` → BUILD SUCCESSFUL (R8 minify + resource shrink, signed with local release key)
- Full console output: `build-logs/final-build-2026-11-15.log`

## 3. Lint warnings (accepted, documented)

1. `InlinedApi` — `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` is API 34+. The call is
   guarded at runtime (`SDK_INT >= 34`, plain `startForeground` otherwise).
2. `OldTargetApi` — targetSdk 36 (Android 16) is the current stable; compileSdk
   37 is installed locally. Bumping targetSdk to 37 without device testing is
   deliberately deferred.

## 4. Feature status

**Implemented + compile-verified (build passes, code reviewed):**
- Battery dashboard & detail screen — genuine BatteryManager readings; every
  unavailable measurement renders as "Not reported" (no fabrication)
- Charging detection — protected `POWER_CONNECTED/DISCONNECTED` broadcasts,
  `specialUse` foreground service, full-screen-intent notification
- Full-screen charging experience — native pulsing animation + profile media
  playback (video via Media3 ExoPlayer; GIF/WebP/stills via Coil) with
  profile transforms (scale/offset/speed/background/text/battery overlay)
- Animation library — SAF import (video/GIF/WebP/PNG/JPEG) copied to private
  storage, category assignment, deletion, Coil thumbnails
- Studio — profiles CRUD, activate, editor (scale/position/speed/text/overlays)
- Sound studio — 4 original synthesized built-in chimes seeded + assigned to
  roles, audio import, preview, volume, mute, repeat-loop while charging
- Automation — rules engine (connect/disconnect/full/low/percent triggers →
  apply profile / play sound / mute), WorkManager periodic sweep, boot re-arm
- Optional AI studio — local-ComfyUI-only client (ping, txt2img, download,
  auto-add to library, generation history); app fully functional without AI
- Settings — all persisted via DataStore; notification permission flow

**Tested on real hardware — install verified (2026-11-15):** the owner
sideloaded `CHARGERVERSE-1.0.0-release.apk` onto the Moto G54 5G from the
GitHub release (`v1.0.0`) and confirmed **"Installed successfully"**. Direct
`adb install` from the laptop was not possible during this session: the
laptop's USB/adb stack repeatedly wedged (device `ZD222K9PGS` flipped to
`offline`, then stopped enumerating), so automated on-device instrumentation
was skipped — this is an environment limitation, not an app failure.

Remaining hardware validation (owner, any time): plug in a charger to verify
the full-screen experience, sound playback, and automation rules; check the
battery screen shows real readings ("Not reported" for anything the device
does not expose); optionally point the AI studio at the local ComfyUI.

**Known limitations:**
- GIF animation frames render via Coil; animated WebP playback depends on
  device decoder support
- Battery "estimated time to full" appears only when the device exposes a
  readable charge counter and current (device-dependent by design)
- AI studio requires the owner's local ComfyUI server reachable; it is inert
  unless enabled in Settings

## 5. Security & privacy review

- No INTERNET permission usage except the optional, user-enabled local ComfyUI
  client (no telemetry, no analytics, no ads, no accounts)
- Runtime permissions: only `POST_NOTIFICATIONS` (Android 13+), requested
  contextually in Settings; SAF document picker for all media/audio imports
  (no broad storage permission)
- Imported media stays in app-private storage; no cloud upload path exists
- Release signing key generated locally, never committed (gitignored)
- FGS is `specialUse` with a clear subtype property string; service stops
  itself on unplug

## 6. Reproduce

```powershell
. .\build-env.ps1
.\gradlew assembleDebug testDebugUnitTest lintDebug assembleRelease
```
