package com.akshat.chargeverse.automation

import com.akshat.chargeverse.battery.BatteryReading
import com.akshat.chargeverse.battery.BatteryRepository
import com.akshat.chargeverse.data.db.AutomationAction
import com.akshat.chargeverse.data.db.AutomationRuleDao
import com.akshat.chargeverse.data.db.AutomationRuleEntity
import com.akshat.chargeverse.data.db.AutomationTrigger
import com.akshat.chargeverse.data.db.ChargeverseDatabase
import com.akshat.chargeverse.data.db.SoundItemDao
import com.akshat.chargeverse.data.db.SoundRole
import com.akshat.chargeverse.data.prefs.SettingsRepository
import com.akshat.chargeverse.sound.SoundEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Rules engine: reacts to charging events and percentage thresholds with
 * user-configured actions (apply profile, play sound, mute). Rules are
 * persisted in Room; the engine runs for the whole process lifetime.
 */
class AutomationEngine(
    private val db: ChargeverseDatabase,
    private val settings: SettingsRepository,
    private val battery: BatteryRepository,
    private val soundEngine: SoundEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val ruleDao: AutomationRuleDao get() = db.automationRuleDao()
    private val soundDao: SoundItemDao get() = db.soundItemDao()

    /** Start reacting to battery signals. Called once at app start. */
    fun start() {
        battery.events
            .onEach { event -> onEvent(event) }
            .launchIn(scope)

        battery.reading
            .onEach { reading -> onReading(reading) }
            .launchIn(scope)
    }

    private suspend fun onEvent(event: com.akshat.chargeverse.battery.ChargingEvent) {
        // CHARGER_* rules fire on the plug/unplug event itself.
        val trigger = when (event) {
            com.akshat.chargeverse.battery.ChargingEvent.CONNECTED -> AutomationTrigger.CHARGER_CONNECTED
            com.akshat.chargeverse.battery.ChargingEvent.DISCONNECTED -> AutomationTrigger.CHARGER_DISCONNECTED
            else -> null
        }
        trigger?.let { executeMatching(it, triggerValue = 0) }

        // FULL/LOW rules are transition-based (see onReading) — the events only
        // drive the built-in alert behavior, governed by settings (not rules):
        val s = settings.current()
        when (event) {
            com.akshat.chargeverse.battery.ChargingEvent.FULL ->
                if (s.fullBatteryAlertEnabled) playRoleSound(SoundRole.FULL)
            com.akshat.chargeverse.battery.ChargingEvent.LOW ->
                if (s.lowBatteryAlertEnabled) playRoleSound(SoundRole.LOW)
            else -> Unit
        }
    }

    /** Public entry for WorkManager sweeps (percentage rules without live flow). */
    suspend fun onReadingForWorker(reading: BatteryReading) = onReading(reading)

    /**
     * Evaluates every transition-based rule against the fresh reading using
     * its persisted arming state: a rule fires when its condition becomes
     * true (crossing), stays disarmed while the condition holds, and re-arms
     * when the condition clears.
     */
    private suspend fun onReading(reading: BatteryReading) {
        if (reading.levelPercent < 0) return
        val pct = reading.levelPercent
        val lowThreshold = settings.current().lowBatteryThreshold

        val rules = ruleDao.enabledRules().filter {
            it.trigger == AutomationTrigger.PERCENT_ABOVE ||
                it.trigger == AutomationTrigger.PERCENT_BELOW ||
                it.trigger == AutomationTrigger.FULL_BATTERY ||
                it.trigger == AutomationTrigger.LOW_BATTERY
        }
        for (rule in rules) {
            val decision = when (rule.trigger) {
                AutomationTrigger.PERCENT_ABOVE ->
                    RuleArming.crossing(rule.armed, pct > rule.triggerValue)
                AutomationTrigger.PERCENT_BELOW ->
                    RuleArming.crossing(rule.armed, pct < rule.triggerValue)
                AutomationTrigger.FULL_BATTERY ->
                    RuleArming.crossing(rule.armed, reading.isFull)
                AutomationTrigger.LOW_BATTERY ->
                    // LOW fires at/below the user threshold; re-arms only after
                    // the battery recovers above threshold + 5 (like the
                    // built-in low alert), charging or not.
                    RuleArming.low(
                        armed = rule.armed,
                        lowNow = pct <= lowThreshold,
                        reArmNow = pct > lowThreshold + 5,
                    )
                else -> null
            } ?: continue

            if (decision.fire) execute(rule)
            if (decision.armed != rule.armed) {
                ruleDao.update(
                    rule.copy(
                        armed = decision.armed,
                        lastFiredMs = if (decision.fire) System.currentTimeMillis() else rule.lastFiredMs,
                    )
                )
            }
        }
    }

    /**
     * Runs every enabled CHARGER_* rule whose trigger matches (events are
     * inherently one-shot; no arming needed).
     */
    suspend fun executeMatching(trigger: AutomationTrigger, triggerValue: Int) {
        val rules = ruleDao.enabledRules().filter { it.trigger == trigger }
        for (rule in rules) execute(rule)
    }

    /** Manual "test" from the automation UI: executes exactly this rule's action. */
    suspend fun executeRule(rule: AutomationRuleEntity) = execute(rule)

    private suspend fun execute(rule: AutomationRuleEntity) {
        when (rule.action) {
            AutomationAction.APPLY_PROFILE -> {
                rule.targetProfileId?.let { profileId ->
                    db.animationProfileDao().clearActive()
                    db.animationProfileDao().setActive(profileId)
                    settings.setActiveProfile(profileId)
                }
            }
            AutomationAction.PLAY_CONNECT_SOUND -> playRoleSound(SoundRole.CONNECT)
            AutomationAction.PLAY_DISCONNECT_SOUND -> playRoleSound(SoundRole.DISCONNECT)
            AutomationAction.MUTE_SOUNDS -> settings.setMasterMuted(true).also { soundEngine.muted = true }
            AutomationAction.UNMUTE_SOUNDS -> settings.setMasterMuted(false).also { soundEngine.muted = false }
        }
    }

    private suspend fun playRoleSound(role: SoundRole) {
        val s = settings.current()
        val configuredId = when (role) {
            SoundRole.CONNECT -> s.connectSoundId
            SoundRole.DISCONNECT -> s.disconnectSoundId
            SoundRole.FULL -> s.fullSoundId
            SoundRole.LOW -> s.lowSoundId
        }
        val item = if (configuredId > 0) soundDao.byId(configuredId) else null
        val uri = item?.uri
            ?: soundDao.byRole(role).firstOrNull()?.uri
            ?: return
        soundEngine.muted = s.masterMuted
        soundEngine.volume = s.soundVolume
        soundEngine.play(android.net.Uri.parse(uri), loop = false)
    }
}

/**
 * Pure arming decisions for transition-based automation rules (unit-tested,
 * no Android/Room dependencies).
 */
object RuleArming {

    data class Decision(val fire: Boolean, val armed: Boolean)

    /**
     * Fires exactly once per crossing: when [condition] becomes true while
     * [armed], the rule fires and disarms; while the condition stays true it
     * stays disarmed; when the condition becomes false again it re-arms.
     * Used by PERCENT_ABOVE / PERCENT_BELOW and FULL_BATTERY (re-arms after
     * leaving full).
     */
    fun crossing(armed: Boolean, condition: Boolean): Decision = when {
        armed && condition -> Decision(fire = true, armed = false)
        !armed && !condition -> Decision(fire = false, armed = true)
        else -> Decision(fire = false, armed = armed)
    }

    /**
     * LOW_BATTERY: fires when the battery is low; re-arms only once the
     * battery has recovered ([reArmNow], threshold + 5).
     */
    fun low(armed: Boolean, lowNow: Boolean, reArmNow: Boolean): Decision = when {
        armed && lowNow -> Decision(fire = true, armed = false)
        !armed && reArmNow -> Decision(fire = false, armed = true)
        else -> Decision(fire = false, armed = armed)
    }
}
