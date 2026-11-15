package com.akshat.chargeverse.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = NeonBlue,
    onPrimary = OnAccent,
    secondary = NeonPurple,
    onSecondary = OnAccent,
    tertiary = NeonMagenta,
    background = Void,
    onBackground = TextPrimary,
    surface = Obsidian,
    onSurface = TextPrimary,
    surfaceVariant = Surface,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDim,
    error = LowRed,
    surfaceContainer = Ink,
    surfaceContainerHigh = Slate,
    surfaceContainerHighest = SurfaceHigh,
    surfaceContainerLow = Ink,
    surfaceContainerLowest = Void,
)

/**
 * Semantic tokens beyond Material's scheme: the accessible text ramp, the
 * primary-button gradient (both ends keep >= 4.5:1 with [OnAccent] content)
 * and state colors. Screens must consume these instead of hardcoding
 * Color.Black / Color.White.
 */
@Immutable
data class CvColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val btnPrimaryStart: Color,
    val btnPrimaryEnd: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
)

val LocalCvColors = staticCompositionLocalOf {
    CvColors(
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textTertiary = TextTertiary,
        textDisabled = TextDisabled,
        btnPrimaryStart = BtnPrimaryStart,
        btnPrimaryEnd = BtnPrimaryEnd,
        onAccent = OnAccent,
        success = FullGreen,
        warning = WarnAmber,
        error = LowRed,
    )
}

/** Convenience accessor: `MaterialTheme.cvColors.textSecondary`. */
val MaterialTheme.cvColors: CvColors
    @Composable get() = LocalCvColors.current

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
