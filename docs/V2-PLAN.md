# CHARGERVERSE v2 — Master Plan & Defect Register

Branch: `feature/cinematic-v2` · Base: `develop@32bb3de` · Tag preserved: `v1.0.0`

## Milestones
| # | Milestone | Status |
|---|---|---|
| S0 | Baseline & safety audit | ✅ DONE — build green, secrets audit clean, branch created |
| S1 | Design system, typography & contrast repair (release blocker) | IN PROGRESS |
| S2 | Cinematic home & navigation redesign | queued |
| S3 | Animation gallery & playback redesign | queued |
| S4 | Full-length charging media experience | queued |
| S5 | Audio engine & sound studio | queued |
| S6 | Animation editor & profile upgrades | queued |
| S7 | Content pack architecture (manifests, downloads, checksums) | queued |
| S8 | Starter original/licensed content catalogue | queued |
| S9 | AI studio integration | queued |
| S10 | Accessibility, performance & compatibility | queued |
| S11 | Google Play readiness docs | queued |
| S12 | Final APK release (delivery/*V2*) | queued |

Release naming rule (§19): use the actual version determined by the release
plan; do not call it 2.0.0 prematurely.

## Defect register (from S0 code audit)

| ID | Defect | Fix owner | Status |
|----|--------|-----------|--------|
| D1 | `fallbackToDestructiveMigration` — schema bump erases all user data (`data/db/ChargeverseDatabase.kt:31`) | DEF-FIX | ✅ DONE — Room v2 with hand-written `MIGRATION_1_2` (profiles FK, sound_roles split, rules `armed`), schema export committed |
| D2 | Custom sounds can't serve DISCONNECT/FULL/LOW — import hardcodes CONNECT role (`ui/sounds/SoundsScreen.kt:44`), `assignRole` dead (`SoundsViewModel.kt:86-95`) | DEF-FIX | ✅ DONE — import asks for role(s) (`sound_roles`), per-row role chip reassigns |
| D3 | GIF/WebP render as static frame — coil-gif dep present but no ImageLoaderFactory/decoder registration anywhere | DEF-FIX | ✅ DONE — `ChargeverseApp.newImageLoader` registers ImageDecoderDecoder + GifDecoder + VideoFrameDecoder |
| D4 | PERCENT_* automation rules never re-arm — one-shot `lastFiredMs` blocks all future firing (`AutomationEngine.kt:89-98`) | DEF-FIX | ✅ DONE — transition-based arming persisted in `armed` column, pure `RuleArming` + unit tests |
| D5 | Battery sweep WorkManager scheduled only from BootReceiver — dead until reboot (`BootReceiver.kt:22`) | DEF-FIX | ✅ DONE — also scheduled in `Application.onCreate`, unique name + KEEP |
| D6 | User low-battery threshold setting ignored — repo hardcodes 15 (`BatteryRepository.kt:45,55`); haptics switch + VIBRATE permission have zero consumers | DEF-FIX | ✅ DONE — threshold flows into BatteryRepository; haptic pulse on charger connect |
| D7 | ChargingService stacks a battery-flow collector on every onStartCommand — double loop sound risk (`ChargingService.kt:50-78`) | DEF-FIX | ✅ DONE — single cancellable collector Job + loop-start guard |
| D8 | Lottie is a declared media type with no playback — ChargingActivity stub (`ChargingActivity.kt:199-207`) | DEF-FIX | ✅ DONE — lottie-compose playback with profile speed/scale/offset, loop aware |
| D9 | No confirmations on destructive actions; built-in sounds deletable (rows gone until reseed) | DEF-FIX | ✅ DONE — shared `ConfirmDeleteDialog` wired everywhere; built-ins protected; deletes clean roles/settings; active profile guarded |
| D10 | AI studio not product-grade: no cancel/progress/queue/model picker; IMG2VID promised but absent | S9 | open |
| D11 | ChargingService notification lacks Open/Stop actions (strings exist, unused); notification title empty arg (`ChargingService.kt:96`) | DEF-FIX | ✅ DONE — Open/Stop actions wired to `notif_action_open/stop`; title never renders empty |
| D12 | Volume slider doesn't affect a currently playing sound (`SoundEngine.kt:48`); no audio focus anywhere | S5 | open |
| D13 | Sound import loses original filename (`SoundsViewModel.kt:69-70`, `MediaResolver.kt:118-125`) | DEF-FIX | ✅ DONE — DISPLAY_NAME queried; source extension kept on the stored copy |
| D14 | Orphan profiles after media delete (no FK, no sweep) | DEF-FIX | ✅ DONE — FK CASCADE + stale `active_profile_id` self-heal |
| D15 | FGS-start rejection silently drops the experience (`ChargingReceiver.kt:38-41` runCatching swallow) | DEF-FIX | ✅ DONE — posts a normal notification fallback, never crashes |
| D16 | Home stats rows silently vanish when null instead of "Not reported" (`HomeScreen.kt:97-106`) | S2 | open |
| D17 | Decorative Pills on Home (`HomeScreen.kt:119-129`); `HomeViewModel.setActiveProfile` dead | S2 | open |
| D18 | `importing` state never rendered in AnimationsScreen; error line has no dismiss (`dismissError` dead) | S3 | open |
| D19 | Profile editor: no live preview; `backgroundHex` unexposed (`StudioScreen.kt:201-236`) | S6 | open |
| D20 | Import audio loses role choice; delete button instantly deletes imported files | DEF-FIX | ✅ DONE — role chooser before import; confirm dialog before delete |
| D21 | Automation: rule test fires all rules of a trigger, not the individual rule; profile picker hidden when no profiles | DEF-FIX | ✅ DONE — per-rule `executeRule`; explicit "no profiles" hint |
| D22 | Radio unselected dot near-invisible 0x33FFFFFF (`AutomationScreen.kt:199-207`) | S1 | open |
| D23 | `Color.Black` on gradient Generate button (`AiStudioScreen.kt:105`) ≈4.0:1 borderline | S1 | open |
| D24 | Hardcoded Color.White/Black in ~8 UI spots bypass the semantic palette | S1 | open |
| D25 | Dead settings keys: `onboarding_done`, `overlay_mode`; dead `setOverlayMode` | S10/S11 | open |
| D26 | No audio focus / ducking; session audio overlaps system audio | S5 | open |
| D27 | Downloads unbound growth in media dir (AI output, imports) | S7 | open |
| D28 | Charging text overlay offset math inconsistent with media layer scale (`ChargingActivity.kt:144-147` vs :170-171) | S4 | open |

## S0 audit conclusions (preserved from exploration agent)

Architecture: Kotlin + Compose BOM, MVVM (manual DI via ServiceLocator), Room v1 +
DataStore, Media3 ExoPlayer/Coil/Lottie deps, WorkManager, minSdk 29 / target 36 /
compileSdk 37, R8 on, single module.

Key facts the v2 work must respect:
- Room DB v1, `exportSchema=false`, destructive fallback — **v2 must ship real
  migrations** (hand-written 1→2 SQL; schema JSON export turned on going forward).
- MediaItemEntity already has durationMs/widthPx/heightPx; missing tags, license,
  download state, checksum, thumbnail path — needed by the content-pack system (S7).
- Charging flow: POWER_CONNECTED → ChargingReceiver (goAsync) → ChargingService
  (specialUse FGS + fullScreenIntent notification) → ChargingActivity
  (showWhenLocked/turnScreenOn) → ChargingMediaEngine (per-screen ExoPlayer).
- Coil GIF decoding must be registered via an app-level ImageLoaderFactory (D3).
- SoundEngine is a single MediaPlayer instance; loop volume never updates mid-play;
  no fades, no focus, no multi-voice (D12/D26) — replaced/upgraded in S5.
- Battery: real BatteryManager telemetry incl. dynamic properties; design capacity
  read from sysfs; LOW threshold hardcoded (D6).
- Automation: 6 triggers / 5 actions, one-shot semantics for percent rules (D4),
  sweep only scheduled on boot (D5).
- AI: raw HttpURLConnection, hardcoded SDXL txt2img graph, 1s history polling,
  auto-import into library; no cancel/progress (D10 → S9).
- Navigation: 7 flat routes; ProfileEditor is an in-screen branch (back exits app);
  no detail routes (S3/S6 add them).
- Resources: 17 vector drawables, 4 WAV chimes (~160 KB), no bundled video; unused
  notif action strings (`notif_action_open/stop`) — use them for D11.
