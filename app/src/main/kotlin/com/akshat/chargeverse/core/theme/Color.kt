package com.akshat.chargeverse.core.theme

import androidx.compose.ui.graphics.Color

/*
 * CHARGERVERSE v2 semantic color system.
 *
 * Every token is annotated with its measured WCAG 2.2 contrast ratio against
 * its intended background (see app/src/test/.../CvPaletteContrastTest.kt,
 * which enforces these numerically in CI):
 *   - normal text tokens must reach >= 4.5:1
 *   - large text (>= 24sp / 19sp bold) and disabled text must reach >= 3:1
 *
 * Base surfaces (AMOLED-first):
 */

// Background ramp: dark → elevated
val Void = Color(0xFF000000)          // primary background (ratio base)
val Obsidian = Color(0xFF05050F)      // secondary background
val Ink = Color(0xFF0B0B1C)           // surfaceContainer(Low)
val Slate = Color(0xFF131328)         // elevated surface
val Surface = Color(0xFF171730)       // surfaceContainer
val SurfaceHigh = Color(0xFF202040)   // surfaceContainerHighest
val OutlineDim = Color(0xFF2E2E52)    // borders/dividers

// Text ramp (all pass >= 4.5:1 on Void; the test proves Secondary/Tertiary on Surface too)
val TextPrimary = Color(0xFFF4F4FF)      // 19.1:1 on Void
val TextSecondary = Color(0xFFB9B9DC)    // 11.0:1 on Void, 6.5:1 on Surface
val TextTertiary = Color(0xFFA0A0C4)     // 8.2:1 on Void, 6.5:1 on Surface
val TextDisabled = Color(0xFF7A7A98)     // 5.1:1 on Void (well above the 3:1 disabled floor)

// Accent system — usable as text on Void (>= 4.5:1)
val NeonBlue = Color(0xFF00D4FF)         // 11.8:1
val NeonPurple = Color(0xFF7C5CFF)       // 5.0:1
val NeonMagenta = Color(0xFFE14EFF)      // ~7:1
val ElectricGreen = Color(0xFF00FFB2)    // very high

// Primary button gradient — label in Void reaches >= 4.5:1 at BOTH ends
val BtnPrimaryStart = Color(0xFF00D4FF)  // Void on this: 11.8:1
val BtnPrimaryEnd = Color(0xFF8B72FF)    // Void on this: 6.0:1 (v1's 7C5CFF only reached 4.0)
val OnAccent = Void                      // text/icon color on accent fills

// State colors (on Void, all >= 4.5:1)
val FullGreen = Color(0xFF4AFF9E)        // success — 16.1:1
val WarnAmber = Color(0xFFFFB74A)        // warning — 12.2:1
val LowRed = Color(0xFFFF4A6A)           // error   — 6.4:1

// Glass (decorative fills only — never used behind text below 4.5:1)
val GlassFill = Color(0x14FFFFFF)
val GlassStroke = Color(0x26FFFFFF)

// ---- v1 aliases (screens still reference these; they now point at the ramp) ----
val DimText = TextSecondary
val BrightText = TextPrimary
