package com.akshat.chargeverse.data.prefs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the `overlay_mode` CSV contract (v2/S4, defect D25):
 * invalid entries ignored, duplicates removed, unknown entries filtered,
 * serialization in canonical order, round-trip stable.
 */
class OverlayConfigTest {

    @Test
    fun `null parses to the default overlay set`() {
        assertEquals(OverlayConfig.DEFAULT, OverlayConfig.parse(null))
        assertEquals(setOf(ChargingOverlay.BATTERY, ChargingOverlay.STATUS), OverlayConfig.parse(null))
    }

    @Test
    fun `blank parses to an empty set`() {
        assertEquals(emptySet<ChargingOverlay>(), OverlayConfig.parse(""))
        assertEquals(emptySet<ChargingOverlay>(), OverlayConfig.parse("   "))
    }

    @Test
    fun `unknown and malformed entries are filtered`() {
        assertEquals(setOf(ChargingOverlay.CLOCK), OverlayConfig.parse("clock,banana,42,;"))
        assertEquals(emptySet<ChargingOverlay>(), OverlayConfig.parse("banana"))
    }

    @Test
    fun `duplicates collapse and case plus whitespace are ignored`() {
        assertEquals(
            setOf(ChargingOverlay.CLOCK, ChargingOverlay.STATUS),
            OverlayConfig.parse("clock, STATUS ,Clock,status,,clock"),
        )
    }

    @Test
    fun `parse keeps first occurrence order`() {
        val parsed = OverlayConfig.parse("battery,clock,status")
        assertTrue(parsed is LinkedHashSet)
        assertEquals(
            listOf(ChargingOverlay.BATTERY, ChargingOverlay.CLOCK, ChargingOverlay.STATUS),
            parsed.toList(),
        )
    }

    @Test
    fun `serialize emits the canonical declaration order`() {
        assertEquals("clock,date,status,temp,battery", OverlayConfig.serialize(ChargingOverlay.entries.toSet()))
        assertEquals("status,battery", OverlayConfig.serialize(setOf(ChargingOverlay.BATTERY, ChargingOverlay.STATUS)))
        assertEquals("", OverlayConfig.serialize(emptySet()))
    }

    @Test
    fun `round trip is stable`() {
        val samples = listOf(
            "battery,status",
            "clock,status,battery",
            "temp",
            "banana,clock,,battery,battery",
            "BATTERY,status",
            null,
            "",
            "   ",
        )
        for (raw in samples) {
            val once = OverlayConfig.parse(raw)
            val twice = OverlayConfig.parse(OverlayConfig.serialize(once))
            assertEquals(raw.toString(), once, twice)
        }
    }

    @Test
    fun `default round trips to itself`() {
        assertEquals(OverlayConfig.DEFAULT, OverlayConfig.parse(OverlayConfig.serialize(OverlayConfig.DEFAULT)))
    }
}
