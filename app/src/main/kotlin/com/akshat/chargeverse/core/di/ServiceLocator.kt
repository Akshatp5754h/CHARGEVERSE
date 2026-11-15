package com.akshat.chargeverse.core.di

import android.app.Application
import com.akshat.chargeverse.ai.ComfyClient
import com.akshat.chargeverse.automation.AutomationEngine
import com.akshat.chargeverse.battery.BatteryReader
import com.akshat.chargeverse.battery.BatteryRepository
import com.akshat.chargeverse.core.media.MediaResolver
import com.akshat.chargeverse.data.db.ChargeverseDatabase
import com.akshat.chargeverse.data.prefs.SettingsRepository
import com.akshat.chargeverse.sound.SoundEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Lightweight manual DI. The app is a single-module Compose application;
 * explicit construction keeps startup fast on the Moto G54.
 */
object ServiceLocator {
    lateinit var app: Application
        private set
    lateinit var db: ChargeverseDatabase
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var batteryReader: BatteryReader
        private set
    lateinit var battery: BatteryRepository
        private set
    lateinit var soundEngine: SoundEngine
        private set
    lateinit var comfyClient: ComfyClient
        private set
    lateinit var automation: AutomationEngine
        private set
    lateinit var mediaResolver: MediaResolver
        private set

    @Volatile
    private var initialized = false

    fun init(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            app = application
            db = ChargeverseDatabase.build(application)
            settings = SettingsRepository(application)
            batteryReader = BatteryReader(application)
            battery = BatteryRepository(
                reader = batteryReader,
                // D6: the user's low-battery threshold drives LOW events.
                lowThresholdFlow = settings.settings.map { it.lowBatteryThreshold },
            )
            soundEngine = SoundEngine(application)
            comfyClient = ComfyClient()
            mediaResolver = MediaResolver(application)
            automation = AutomationEngine(
                db = db,
                settings = settings,
                battery = battery,
                soundEngine = soundEngine,
            )
            initialized = true
        }
    }
}
