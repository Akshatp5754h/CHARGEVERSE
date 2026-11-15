package com.akshat.chargeverse.core.di

import android.app.Application
import com.akshat.chargeverse.ai.ComfyClient
import com.akshat.chargeverse.automation.AutomationEngine
import com.akshat.chargeverse.battery.BatteryReader
import com.akshat.chargeverse.battery.BatteryRepository
import com.akshat.chargeverse.data.db.ChargeverseDatabase
import com.akshat.chargeverse.data.prefs.SettingsRepository
import com.akshat.chargeverse.sound.SoundEngine

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
            battery = BatteryRepository(batteryReader)
            soundEngine = SoundEngine(application)
            comfyClient = ComfyClient()
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
