package com.akshat.chargeverse.automation

import com.akshat.chargeverse.automation.RuleArming.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transition/arming semantics for re-armable automation rules (D4). */
class RuleArmingTest {

    // ---- crossing(): PERCENT_ABOVE / PERCENT_BELOW / FULL_BATTERY --------

    @Test
    fun `crossing fires once when condition becomes true`() {
        val d = RuleArming.crossing(armed = true, condition = true)
        assertTrue(d.fire)
        assertFalse(d.armed)
    }

    @Test
    fun `crossing stays disarmed while condition holds`() {
        val d = RuleArming.crossing(armed = false, condition = true)
        assertFalse(d.fire)
        assertFalse(d.armed)
    }

    @Test
    fun `crossing re-arms when condition becomes false again`() {
        val d = RuleArming.crossing(armed = false, condition = false)
        assertFalse(d.fire)
        assertTrue(d.armed)
    }

    @Test
    fun `crossing stays armed while condition false`() {
        val d = RuleArming.crossing(armed = true, condition = false)
        assertFalse(d.fire)
        assertTrue(d.armed)
    }

    @Test
    fun `percent-above rule simulates full charge cycle`() {
        // threshold 80: 70% (below, armed) → 85% (fires, disarms) → 90% (silent) → 75% (re-arms) → 85% (fires again)
        val steps = listOf(70, 85, 90, 75, 85)
        var armed = true
        val fired = mutableListOf<Int>()
        for (pct in steps) {
            val d = RuleArming.crossing(armed, pct > 80)
            if (d.fire) fired.add(pct)
            armed = d.armed
        }
        assertEquals(listOf(85, 85), fired)
        assertFalse(armed)
    }

    @Test
    fun `full battery re-arms after leaving full`() {
        // Enter full → fires and disarms; still full → silent; unplugged/drain → re-arms; full again → fires again.
        val enter = RuleArming.crossing(armed = true, condition = true)
        assertTrue(enter.fire)
        val stillFull = RuleArming.crossing(enter.armed, condition = true)
        assertFalse(stillFull.fire)
        val leftFull = RuleArming.crossing(stillFull.armed, condition = false)
        assertFalse(leftFull.fire)
        assertTrue(leftFull.armed)
        val fullAgain = RuleArming.crossing(leftFull.armed, condition = true)
        assertTrue(fullAgain.fire)
        assertFalse(fullAgain.armed)
    }

    // ---- low(): LOW_BATTERY ------------------------------------------------

    @Test
    fun `low fires when battery drops to threshold`() {
        val d = RuleArming.low(armed = true, lowNow = true, reArmNow = false)
        assertTrue(d.fire)
        assertFalse(d.armed)
    }

    @Test
    fun `low stays disarmed while battery stays low`() {
        val d = RuleArming.low(armed = false, lowNow = true, reArmNow = false)
        assertFalse(d.fire)
        assertFalse(d.armed)
    }

    @Test
    fun `low only re-arms after recovery past threshold plus five`() {
        val fired = RuleArming.low(true, lowNow = true, reArmNow = false)
        assertFalse(fired.armed)
        // Recovery to threshold+3 is not enough.
        val midRecovery = RuleArming.low(fired.armed, lowNow = false, reArmNow = false)
        assertFalse(midRecovery.armed)
        // Past threshold+5 → re-armed.
        val recovered = RuleArming.low(fired.armed, lowNow = false, reArmNow = true)
        assertTrue(recovered.armed)
        assertFalse(recovered.fire)
        // Low again → fires again.
        val lowAgain = RuleArming.low(recovered.armed, lowNow = true, reArmNow = false)
        assertTrue(lowAgain.fire)
    }

    @Test
    fun `decisions carry their fields`() {
        assertEquals(Decision(fire = true, armed = false), RuleArming.crossing(true, true))
        assertEquals(Decision(fire = false, armed = true), RuleArming.crossing(true, false))
    }
}
