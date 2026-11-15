package com.akshat.chargeverse.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the pure battery computations (no Android runtime needed). */
class BatteryMathTest {

    @Test
    fun `charging power is computed from real voltage and current`() {
        // 5000 mV * 2_000_000 uA = 10 W = 10000 mW
        assertEquals(10000, BatteryMath.chargingPowerMw(5000, 2_000_000, isCharging = true))
    }

    @Test
    fun `power is null when not charging`() {
        assertNull(BatteryMath.chargingPowerMw(5000, 2_000_000, isCharging = false))
    }

    @Test
    fun `power is null when readings are missing or invalid`() {
        assertNull(BatteryMath.chargingPowerMw(null, 1_000_000, true))
        assertNull(BatteryMath.chargingPowerMw(5000, null, true))
        assertNull(BatteryMath.chargingPowerMw(0, 1_000_000, true))
        assertNull(BatteryMath.chargingPowerMw(5000, -1, true))
    }

    @Test
    fun `eta is computed from charge counter and current`() {
        // Remaining 2_000_000 uAh at 500_000 uA => 4 hours * 60 = 240 min
        assertEquals(
            240L,
            BatteryMath.minutesToFull(
                chargeCounterUah = 3_000_000,
                designCapacityUah = 5_000_000L,
                currentMicroAmp = 500_000,
                isCharging = true,
            ),
        )
    }

    @Test
    fun `eta is null when data unusable`() {
        assertNull(BatteryMath.minutesToFull(null, 5_000_000L, 500_000, true))
        assertNull(BatteryMath.minutesToFull(3_000_000, null, 500_000, true))
        assertNull(BatteryMath.minutesToFull(3_000_000, 5_000_000L, 0, true))
        assertNull(BatteryMath.minutesToFull(3_000_000, 5_000_000L, 500_000, false))
    }

    @Test
    fun `eta is zero when full`() {
        assertEquals(
            0L,
            BatteryMath.minutesToFull(5_000_000, 5_000_000L, 500_000, true),
        )
    }

    @Test
    fun `transition detection flips only on change`() {
        assertTrue(BatteryMath.transitioned(wasCharging = false, isCharging = true))
        assertTrue(BatteryMath.transitioned(wasCharging = true, isCharging = false))
        assertFalse(BatteryMath.transitioned(wasCharging = true, isCharging = true))
        assertFalse(BatteryMath.transitioned(wasCharging = false, isCharging = false))
    }
}
