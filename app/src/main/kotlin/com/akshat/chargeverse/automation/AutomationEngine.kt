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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

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
        val trigger = when (event) {
            com.akshat.chargeverse.battery.ChargingEvent.CONNECTED -> AutomationTrigger.CHARGER_CONNECTED
            com.akshat.chargeverse.battery.ChargingEvent.DISCONNECTED -> AutomationTrigger.CHARGER_DISCONNECTED
            com.akshat.chargeverse.battery.ChargingEvent.FULL -> AutomationTrigger.FULL_BATTERY
            com.akshat.chargeverse.battery.ChargingEvent.LOW -> AutomationTrigger.LOW_BATTERY
        }
        executeMatching(trigger, triggerValue = 0)
    }

    /** Public entry for WorkManager sweeps (percentage rules without live flow). */
    suspend fun onReadingForWorker(reading: BatteryReading) = onReading(reading)

    private suspend fun onReading(reading: BatteryReading) {
        if (reading.levelPercent < 0) return
        val pct = reading.levelPercent
        executeMatching(AutomationTrigger.PERCENT_ABOVE, triggerValue = pct)
        executeMatching(AutomationTrigger.PERCENT_BELOW, triggerValue = pct)
    }

    /** Runs every enabled rule whose trigger matches; one-shot triggers mark lastFiredMs. */
    suspend fun executeMatching(trigger: AutomationTrigger, triggerValue: Int) {
        val rules = ruleDao.enabledRules().filter { it.trigger == trigger }
        for (rule in rules) {
            val matches = when (trigger) {
                AutomationTrigger.PERCENT_ABOVE -> triggerValue > rule.triggerValue
                AutomationTrigger.PERCENT_BELOW -> triggerValue < rule.triggerValue
                else -> true
            }
            val isOneShot = trigger == AutomationTrigger.FULL_BATTERY ||
                trigger == AutomationTrigger.LOW_BATTERY ||
                trigger == AutomationTrigger.PERCENT_ABOVE ||
                trigger == AutomationTrigger.PERCENT_BELOW
            if (!matches) continue
            if (isOneShot && rule.lastFiredMs != null) continue

            execute(rule)
            if (isOneShot) {
                ruleDao.update(rule.copy(lastFiredMs = System.currentTimeMillis()))
            }
        }
    }

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
            ?: soundDao.observeByRole(role).first().firstOrNull()?.uri
            ?: return
        soundEngine.muted = s.masterMuted
        soundEngine.volume = s.soundVolume
        soundEngine.play(android.net.Uri.parse(uri), loop = false)
    }
}
