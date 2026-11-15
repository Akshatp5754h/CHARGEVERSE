# CHARGERVERSE v2 — Design System Contract

All screen work (S2+) MUST follow this document. Tokens live in
`core/theme/Color.kt` + `MaterialTheme.cvColors`; enforcement lives in
`app/src/test/java/com/akshat/chargeverse/CvPaletteContrastTest.kt`.

## 1. Colour tokens (use these, never raw Color/Color.White/Color.Black)

| Token | Hex | Use |
|---|---|---|
| `Void` | 000000 | app background, hero backdrops |
| `Obsidian` | 05050F | secondary background, screen sections |
| `Ink` | 0B0B1C | cards (surfaceContainer) |
| `Slate` | 131328 | elevated cards |
| `Surface` | 171730 | chips, inputs, surfaceContainer |
| `SurfaceHigh` | 202040 | pressed/elevated chips |
| `OutlineDim` | 2E2E52 | borders, dividers |
| `cvColors.textPrimary` | F4F4FF | headings, values |
| `cvColors.textSecondary` | B9B9DC | body, labels |
| `cvColors.textTertiary` | A0A0C4 | hints, metadata |
| `cvColors.textDisabled` | 7A7A98 | disabled states |
| `NeonBlue` | 00D4FF | primary accent, selected states, links |
| `BtnPrimaryStart/End` | 00D4FF→8B72FF | primary button gradient (label = `OnAccent`) |
| `NeonPurple` / `NeonMagenta` | 7C5CFF / E14EFF | secondary accent, gradients |
| `FullGreen` / `WarnAmber` / `LowRed` | 4AFF9E / FFB74A / FF4A6A | success / warning / error |

Rules:
- Text on any background must come from the text ramp or an accent with a
  proven ≥4.5:1 pairing (the unit test encodes the full matrix).
- Overlays/scrim on imagery: use `Color(0xB3000000)` (70% black) minimum for
  text-bearing scrims; pure 60%-black scrims are forbidden for text.
- Decorative glass fills (`GlassFill` 8% white) never carry text below 4.5:1.
- No alpha < 0.5 on any Text or Icon that conveys information.

## 2. Typography (Type.kt)

displayLarge 44sp Black — battery %, hero numerals.
displayMedium 34sp ExtraBold — screen titles on hero surfaces.
displaySmall 28sp ExtraBold — secondary hero numerals.
headlineMedium 24sp Bold — section headers.
titleLarge 19sp Bold — card titles.
titleMedium 16sp SemiBold — row titles, button labels (large).
bodyLarge 15sp / bodyMedium 13sp — body copy, list subtitles.
labelLarge 13sp SemiBold — buttons, chips (large).
labelMedium 11sp / labelSmall 10sp — badges, nav labels (large only).

Respect system font scale: never fix line height below 1.2× font size; test
at 1.3× scale mentally — cards must wrap, not clip.

## 3. Component recipes

- **Screen scaffold**: `Column` on `Void`, content in a `LazyColumn` padded
  20.dp horizontal, 96.dp bottom (clears the floating nav bar).
- **Cards**: rounded 24.dp, fill `Ink` or `Slate`, 1.dp border `OutlineDim` at
  40% alpha. Accent cards may use vertical gradient `Slate→Ink`.
- **Primary button**: `Row` clip(RoundedCornerShape(16.dp)) background
  `Brush.linearGradient(listOf(btnPrimaryStart, btnPrimaryEnd))`, label
  `OnAccent` labelLarge, min height 48.dp, minimum tap target 48.dp.
- **Secondary button**: transparent fill, 1.dp `OutlineDim` border,
  `NeonBlue` label.
- **Chips**: `Surface` fill, 12.dp radius, `textSecondary` label; selected =
  `NeonBlue` fill with `OnAccent` label.
- **Empty states**: icon in 64.dp tinted `OutlineDim` circle, titleMedium
  title in textPrimary, bodyMedium hint in textTertiary, optional primary
  button. Never a bare "No items" line.
- **Loading**: circular indicator in `NeonBlue` + textSecondary status line.
- **Error**: `LowRed` icon + textPrimary message + retry secondary button.
- **Confirm dialogs**: `ConfirmDeleteDialog` from `ui/components/Dialogs.kt`
  for every destructive action.
- **Sliders**: M3 Slider, track `NeonBlue`, inactive `OutlineDim`, value label
  visible while dragging.
- **Icon buttons**: always carry a `contentDescription`; decorative icons get
  null but must not be the sole affordance.

## 4. Motion

- Screen transitions: 280 ms fade + 8.dp slide-up (MainActivity NavHost).
- List items: `animateItemPlacement` for reorder/inserts.
- Taps: scale 0.97 on press for cards (no ripple-only feedback).
- Ambient loops (pulse rings, hero glow): 1200–2000 ms, `FastOutSlowInEasing`,
  and must pause when battery-saver mode is active (S10).

## 5. Accessibility contract

- Every interactive element: min 48.dp tap target, semantic label, visible
  state (selected/disabled) not conveyed by colour alone.
- Contrast floors enforced by unit test; large-text floor 3:1.
- `contentDescription` for all media previews ("Animation preview: <name>").
- Battery % values announced via `semantics { contentDescription }` on rings.
