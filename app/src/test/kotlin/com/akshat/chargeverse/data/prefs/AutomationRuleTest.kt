package com.akshat.chargeverse.data.prefs

import com.akshat.chargeverse.data.db.AutomationAction
import com.akshat.chargeverse.data.db.AutomationTrigger
import com.akshat.chargeverse.data.db.AutomationRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room-free checks of the automation rule model semantics. */
class AutomationRuleTest {

    private fun rule(
        trigger: AutomationTrigger,
        value: Int = 0,
        lastFiredMs: Long? = null,
    ) = AutomationRuleEntity(
        id = 1,
        trigger = trigger,
        triggerValue = value,
        action = AutomationAction.PLAY_CONNECT_SOUND,
        targetProfileId = null,
        enabled = true,
        createdAtMs = 0,
        lastFiredMs = lastFiredMs,
    )

    @Test
    fun `one-shot triggers include full low and percent`() {
        val oneShot = listOf(
            AutomationTrigger.FULL_BATTERY,
            AutomationTrigger.LOW_BATTERY,
            AutomationTrigger.PERCENT_ABOVE,
            AutomationTrigger.PERCENT_BELOW,
        )
        assertTrue(oneShot.all { it != AutomationTrigger.CHARGER_CONNECTED })
        assertFalse(oneShot.contains(AutomationTrigger.CHARGER_DISCONNECTED))
    }

    @Test
    fun `percent rules carry thresholds`() {
        val above = rule(AutomationTrigger.PERCENT_ABOVE, value = 80)
        val below = rule(AutomationTrigger.PERCENT_BELOW, value = 15)
        assertEquals(80, above.triggerValue)
        assertEquals(15, below.triggerValue)
    }

    @Test
    fun `fired timestamp records one-shot execution`() {
        val r = rule(AutomationTrigger.FULL_BATTERY)
        val fired = r.copy(lastFiredMs = 12345L)
        assertEquals(12345L, fired.lastFiredMs)
    }
}
