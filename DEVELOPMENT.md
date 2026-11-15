# CHARGEVERSE — Development Log & Task Board

## Toolchain (verified on this machine, 2026-11-15)

| Component | Version | Source |
|---|---|---|
| OS | Windows 11 Pro 64-bit | local |
| JDK | OpenJDK 25.0.4 (Studio JBR `D:\ANDROID_STUDIO\jbr`) | bundled |
| Gradle | 9.6.0 (dist cached, wrapper committed) | gradle-9.6.0-bin |
| AGP | 9.5.1 (built-in Kotlin — no `kotlin-android` plugin) | cached in modules-2 |
| Kotlin | 2.2.x embedded by AGP; compose-compiler plugin 2.2.10 | cached |
| KSP | 2.3.14 (standalone; required ≥2.3.1 for built-in Kotlin) | maven |
| SDK | `C:\Users\Akshat\AppData\Local\Android\Sdk` — platform android-37.0, build-tools 36.0.0 | local |
| Compose BOM | 2026.02.01 (ui 1.10.4, material3 1.4.0) | cached |
| Room 2.8.5 · Media3 1.11.1 · DataStore 1.3.0 · Work 2.12.0 · Coil 2.7.0 · Lottie 6.7.1 | | maven metadata verified |

**Environment discipline:** C: has ~1.8 GB free — `GRADLE_USER_HOME` is pinned
to `G:\AI_STORAGE\.gradle` via `build-env.ps1`; SDK stays on C: (already there).
**No device connected to adb** during Sprint 0/1 — installation on the Moto G54
is pending the owner connecting the phone (or ADB-over-WiFi).

## Architecture

Single `:app` module, clean MVVM with manual DI (`ServiceLocator`), organized
by feature packages:

```
com.akshat.chargeverse
├── ChargeverseApp        (Application: DI init, channels, battery feed)
├── MainActivity          (NavHost + 7-tab bottom bar)
├── core/ theme · icons · di · media
├── data/ db (Room) · prefs (DataStore)
├── battery/              (BatteryReader, BatteryRepository, BatteryMath)
├── charging/             (ChargingReceiver, ChargingService, ChargingActivity)
├── sound/                (SoundEngine)
├── automation/           (AutomationEngine, BootReceiver, BatteryCheckWorker)
├── ai/                   (ComfyClient — local ComfyUI only)
└── ui/ home · battery · animations · studio · sounds · automation · settings
```

## Sprint board

- [x] **S0** Environment inspection + project scaffold + wrapper + version catalog
- [x] **S1** Foundation: theme, navigation, dashboard, Room, DataStore, DI (build pending)
- [x] **S2** Battery monitoring: live readings, detail screen, no-fabrication policy
- [x] **S3** Charging detection: receiver, FGS, full-screen experience, boot re-arm
- [ ] **S4** Media playback: ExoPlayer integration into ChargingActivity
- [ ] **S5** Gallery + editor polish (category assignment, previews, thumbnails)
- [ ] **S6** Sound studio: built-in chimes, repeat loop, disconnect/full/low wiring
- [ ] **S7** AI integration: txt2img/IMG2VID flows, generation history UI
- [ ] **S8** Personalization: onboarding, lockscreen overlay option, polish
- [ ] **S9** Performance: battery-draw audit, APK size, lint fixes
- [ ] **S10** QA + release: unit tests, checksums, delivery folder

## Engineering decisions log

1. **AGP 9 built-in Kotlin** — `org.jetbrains.kotlin.android` is NOT applied
   (AGP 9 forbids it under the new DSL). Compose compiler comes from
   `org.jetbrains.kotlin.plugin.compose` 2.2.10, matching the embedded Kotlin.
2. **Room via KSP 2.3.14** — kapt is impossible under built-in Kotlin.
   Avoided `@Entity` + `@Parcelize` in one class (known KSP bug #3053).
3. **Custom icons** — material-icons-extended was deprecated/removed from
   recent BOMs; CHARGEVERSE ships its own vector set in `res/drawable`.
4. **Charging launch strategy** — `ACTION_POWER_CONNECTED` manifest receiver →
   `specialUse` FGS + full-screen-intent notification. No SYSTEM_ALERT_WINDOW
   by default (opt-in overlay planned S8).
5. **Storage** — imported media is copied into `filesDir/media` (no storage
   permission needed; survives reboots without persisted URI grants).
6. **Battery honesty** — every unavailable reading renders as "Not reported";
   power/ETA are computed only from real voltage×current / charge-counter data.
