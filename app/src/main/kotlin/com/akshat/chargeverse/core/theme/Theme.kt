package com.akshat.chargeverse.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = Void,
    secondary = NeonPurple,
    onSecondary = Void,
    tertiary = NeonMagenta,
    background = Void,
    onBackground = BrightText,
    surface = Obsidian,
    onSurface = BrightText,
    surfaceVariant = Surface,
    onSurfaceVariant = DimText,
    outline = OutlineDim,
    error = LowRed,
    surfaceContainer = Ink,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = SurfaceHigh,
    surfaceContainerLow = Ink,
    surfaceContainerLowest = Void,
)

val CvShapes = Shapes(
    extraSmall = RoundedCornerShape(8),
    small = RoundedCornerShape(12),
    medium = RoundedCornerShape(18),
    large = RoundedCornerShape(24),
    extraLarge = RoundedCornerShape(32),
)

@Composable
fun ChargeverseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = CvTypography,
        shapes = CvShapes,
        content = content,
    )
}

/** Battery-percentage dependent accent color, used across the app. */
fun batteryAccent(percent: Int): Color = when {
    percent >= 80 -> FullGreen
    percent >= 30 -> NeonBlue
    percent >= 15 -> WarnAmber
    else -> LowRed
}
