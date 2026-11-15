package com.akshat.chargeverse

import androidx.compose.ui.graphics.Color
import com.akshat.chargeverse.core.theme.BtnPrimaryEnd
import com.akshat.chargeverse.core.theme.BtnPrimaryStart
import com.akshat.chargeverse.core.theme.FullGreen
import com.akshat.chargeverse.core.theme.Ink
import com.akshat.chargeverse.core.theme.LowRed
import com.akshat.chargeverse.core.theme.NeonBlue
import com.akshat.chargeverse.core.theme.NeonMagenta
import com.akshat.chargeverse.core.theme.NeonPurple
import com.akshat.chargeverse.core.theme.Obsidian
import com.akshat.chargeverse.core.theme.OnAccent
import com.akshat.chargeverse.core.theme.Surface
import com.akshat.chargeverse.core.theme.SurfaceHigh
import com.akshat.chargeverse.core.theme.TextDisabled
import com.akshat.chargeverse.core.theme.TextPrimary
import com.akshat.chargeverse.core.theme.TextSecondary
import com.akshat.chargeverse.core.theme.TextTertiary
import com.akshat.chargeverse.core.theme.WarnAmber
import com.akshat.chargeverse.core.theme.Void
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG 2.2 AA contrast enforcement for the CHARGERVERSE semantic palette.
 *
 * Rule: a colour-token change that drops any pairing below its required
 * ratio FAILS the unit-test build, so "black-on-black text" and invisible
 * labels cannot regress. Normal text floor = 4.5:1; large/disabled floor = 3:1.
 */
class CvPaletteContrastTest {

    // ---- WCAG 2.2 math -------------------------------------------------

    private fun channel(c: Double): Double =
        if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double {
        val r = channel(color.red.toDouble())
        val g = channel(color.green.toDouble())
        val b = channel(color.blue.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun ratio(fg: Color, bg: Color): Double {
        val lf = luminance(fg)
        val lb = luminance(bg)
        val lighter = maxOf(lf, lb)
        val darker = minOf(lf, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertReadable(fg: Color, bg: Color, min: Double, label: String) {
        val r = ratio(fg, bg)
        // Guard against invisible text: fg/bg must be distinct so the ratio is
        // meaningful. Direction-agnostic: dark text on light fills (e.g. the
        // Void label on the primary button gradient) is equally valid.
        assertTrue(
            "$label: foreground and background must differ",
            kotlin.math.abs(luminance(fg) - luminance(bg)) > 1e-9,
        )
        assertTrue(
            "$label: contrast %.2f < required %.2f".format(r, min),
            r >= min,
        )
    }

    // ---- Normal-text pairs (>= 4.5:1) -----------------------------------

    @Test
    fun `primary text on every background`() {
        listOf(Void, Obsidian, Ink, Surface, SurfaceHigh).forEach { bg ->
            assertReadable(TextPrimary, bg, 4.5, "TextPrimary on $bg")
        }
    }

    @Test
    fun `secondary text on every background`() {
        listOf(Void, Obsidian, Ink, Surface, SurfaceHigh).forEach { bg ->
            assertReadable(TextSecondary, bg, 4.5, "TextSecondary on $bg")
        }
    }

    @Test
    fun `tertiary text passes 4_5 on base surfaces`() {
        listOf(Void, Obsidian, Ink, Surface).forEach { bg ->
            assertReadable(TextTertiary, bg, 4.5, "TextTertiary on $bg")
        }
    }

    @Test
    fun `disabled text stays well above the 3x floor`() {
        assertReadable(TextDisabled, Void, 3.0, "TextDisabled on Void")
        assertReadable(TextDisabled, Surface, 3.0, "TextDisabled on Surface")
    }

    @Test
    fun `primary button label on both gradient ends`() {
        assertReadable(OnAccent, BtnPrimaryStart, 4.5, "OnAccent on BtnPrimaryStart")
        assertReadable(OnAccent, BtnPrimaryEnd, 4.5, "OnAccent on BtnPrimaryEnd")
    }

    @Test
    fun `accents used as small text on Void`() {
        assertReadable(NeonBlue, Void, 4.5, "NeonBlue on Void")
        assertReadable(NeonPurple, Void, 4.5, "NeonPurple on Void")
        assertReadable(NeonMagenta, Void, 4.5, "NeonMagenta on Void")
    }

    @Test
    fun `state colors as text on Void`() {
        assertReadable(FullGreen, Void, 4.5, "FullGreen on Void")
        assertReadable(WarnAmber, Void, 4.5, "WarnAmber on Void")
        assertReadable(LowRed, Void, 4.5, "LowRed on Void")
    }

    // ---- Sanity: surfaces are a real elevation ramp ----------------------

    @Test
    fun `surface ramp is monotonically lighter`() {
        val ramp = listOf(
            luminance(Void),
            luminance(Obsidian),
            luminance(Ink),
            SlateLuminance(),
            SurfaceLuminance(),
            SurfaceHighLuminance(),
        )
        assertTrue(ramp.zipWithNext().all { (a, b) -> b > a })
    }

    private fun SlateLuminance() = luminance(com.akshat.chargeverse.core.theme.Slate)
    private fun SurfaceLuminance() = luminance(Surface)
    private fun SurfaceHighLuminance() = luminance(SurfaceHigh)
}
