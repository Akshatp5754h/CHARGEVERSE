# CHARGEVERSE

A premium, 100% free Android charging-customization app for the Moto G54 5G —
native Kotlin + Jetpack Compose, deep-AMOLED design, neon blue/purple accents.

**Package:** `com.akshat.chargeverse` · **minSdk 29** · **targetSdk 36** · **compileSdk 37**

## Features

- **Charging experience** — full-screen charging interface with a native pulsing
  animation (media playback layered in Sprint 4), launched by system
  power-connected broadcasts via a `specialUse` foreground service.
- **Battery dashboard** — genuine Android BatteryManager data only: percentage,
  state, temperature, voltage, current, charging power, charge counter,
  technology, cycle count (when the device reports it). Missing measurements
  are shown as *“Not reported”* — never fabricated.
- **Animation library & Studio** — import video/GIF/WebP/PNG/JPEG into private
  storage, build animation profiles with scale/position/speed/text/battery
  overlays, activate profiles.
- **Sound studio** — import audio, assign sounds to connect / disconnect /
  full / low events, preview, volume, mute, repeat.
- **Automation** — rules engine: charger connect/disconnect/full/low and
  percentage triggers → apply profile / play sound / mute. Survives reboots.
- **Optional AI studio** — optional integration with a **local** ComfyUI server
  (`http://127.0.0.1:8188` by default) for text-to-image generation. The app is
  fully functional without any AI.

## Zero-cost guarantees

No paid APIs, no ads, no subscriptions, no accounts, no mandatory internet,
no analytics, no root. All processing happens on-device (or on your own
localhost ComfyUI if you opt in).

## Build

```powershell
. .\build-env.ps1      # sets GRADLE_USER_HOME (G:), JAVA_HOME (Studio JBR), ANDROID_HOME
.\gradlew assembleDebug
.\gradlew testDebugUnitTest
.\gradlew lintDebug
.\gradlew assembleRelease
```

The Gradle toolchain lives in `G:\AI_STORAGE\.gradle` (this laptop's C: drive
is critically low; never build into the default `C:\Users\...\.gradle`).

See `DEVELOPMENT.md` for the full engineering log, architecture and sprint
status, and `delivery/` for produced APKs and checksums.
